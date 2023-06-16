package com.wsudesc.otavio;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.SearchV2Result;
import com.dropbox.core.v2.files.WriteMode;

/**
 * Hello world!
 *
 */
public class Client {
    private static Logger logger = Logger.getLogger(Client.class.getName());
    private static DropboxConfig[] dbClients = new DropboxConfig[3];

    private static void setClient() {
        for (int i = 0; i < 3; i++) {
            String filePath = String.format("dbU%dToken.properties", i + 1);
            dbClients[i] = new DropboxConfig(i + 1, filePath);
        }
    }

    public static Socket getJob() throws Exception {
        Socket clientSocket = new Socket("ens5", 4355);
        return clientSocket;
    }

    private static void checkIfFolderExists(DbxClientV2 client, String dbFolder) {
        try {
            client.files().createFolderV2("/" + dbFolder, false);
        } catch (DbxException | NullPointerException e) {
            // e.printStackTrace();
        }
    }

    private static long getFolderSpaceUsage(DbxClientV2 client, String folder)
            throws ListFolderErrorException, DbxException {
        long size = 0L;
        ListFolderResult result = client.files()
                .listFolderBuilder(folder)
                .withIncludeDeleted(false)
                .withRecursive(true).start();
        while (true) {
            for (Metadata metadata : result.getEntries()) {
                if (metadata instanceof FileMetadata) {
                    FileMetadata fileMetadata = (FileMetadata) metadata;
                    size += fileMetadata.getSize();
                }
            }

            if (!result.getHasMore()) {
                break;
            }

            result = client.files().listFolderContinue(result.getCursor());
        }

        return size;
    }

    public static void main(String args[]) {
        String cmdLineVal = System.getenv("loglevel");
        if (cmdLineVal != null) {
            logger.setLevel(Level.parse(cmdLineVal));
        } else {
            logger.setLevel(Level.INFO);
        }
        logger.info("LogLevel: " + cmdLineVal);

        setClient();

        try {
            while (true) {

                Socket clientSocket = getJob();

                DataOutputStream outToServer = new DataOutputStream(
                        clientSocket.getOutputStream());
                DataInputStream inFromServer = new DataInputStream(clientSocket.getInputStream());

                // Get Job type (upload|download)
                String jobTypeString = inFromServer.readUTF();
                logger.info(jobTypeString);

                // if upload
                if (jobTypeString.contains("upload")) {
                    // Get DbDir
                    String jobDbDir = inFromServer.readUTF();
                    logger.info(jobDbDir);

                    // Get NomeArquivo
                    String jobfileName = inFromServer.readUTF();
                    logger.info(jobfileName);

                    // Gen tracks name
                    // T003;DIVIDO;arqbla1.p02;tempfiles;
                    String trackName1 = String.format(Locale.getDefault(), "%s.p01",
                            jobfileName.substring(0, jobfileName.lastIndexOf('.')));
                    String trackName2 = String.format(Locale.getDefault(), "%s.p02",
                            jobfileName.substring(0, jobfileName.lastIndexOf('.')));

                    // Get Tamanho Arquivo
                    long jobFileSize = inFromServer.readLong();
                    logger.info(String.valueOf(jobFileSize));

                    // Get bytes / ler bytes
                    // Split bytes in two tracks
                    // Save tracks to tempfiles
                    byte[] buffer = new byte[16 * 1024];
                    int count;
                    long bytesToReadPerTrack = jobFileSize / 2L;

                    FileOutputStream outFile1 = new FileOutputStream("./tempfiles/" + trackName1);
                    long bytesReadTrack1 = 0;
                    do {
                        int bytesSize = (int) Math.min(buffer.length, bytesToReadPerTrack - bytesReadTrack1);
                        count = inFromServer.read(buffer, 0, bytesSize);
                        outFile1.write(buffer, 0, count);
                        bytesReadTrack1 += count;
                        if (bytesReadTrack1 >= bytesToReadPerTrack) {
                            break;
                        }
                    } while (count > 0);
                    outFile1.close();
                    logger.info("Received track 1");

                    FileOutputStream outFile2 = new FileOutputStream("./tempfiles/" + trackName2);
                    long bytesReadTrack2 = 0;
                    do {
                        int bytesSize = (int) Math.min(buffer.length, bytesToReadPerTrack - bytesReadTrack2);
                        count = inFromServer.read(buffer, 0, bytesSize);
                        outFile2.write(buffer, 0, count);
                        bytesReadTrack2 += count;
                        if (bytesReadTrack2 >= bytesToReadPerTrack) {
                            break;
                        }
                    } while (count > 0);
                    outFile2.close();
                    logger.info("Received track 2");

                    // send split logs to Mon
                    outToServer.writeUTF(String.format(
                            Locale.getDefault(),
                            "DIVIDO;%s;tempfiles;",
                            trackName1));
                    outToServer.writeUTF(String.format(
                            Locale.getDefault(),
                            "DIVIDO;%s;tempfiles;",
                            trackName2));

                    // Choose accounts to upload to
                    List<AbstractMap.SimpleEntry<Long, DropboxConfig>> sizes = new ArrayList<AbstractMap.SimpleEntry<Long, DropboxConfig>>();
                    for (int i = 0; i < dbClients.length; i++) {
                        DbxClientV2 client = dbClients[i].DropboxClient();
                        checkIfFolderExists(client, jobDbDir);
                        long size = getFolderSpaceUsage(client, "/" + jobDbDir + "/");
                        logger.info(String.format("Client %d size %d", dbClients[i].getId(), size));
                        sizes.add(new AbstractMap.SimpleEntry<Long, DropboxConfig>(size, dbClients[i]));
                    }

                    List<DropboxConfig> selectedConfigs = sizes.stream()
                            .sorted(Comparator.comparingLong(AbstractMap.SimpleEntry<Long, DropboxConfig>::getKey))
                            .limit(2)
                            .map((key -> key.getValue()))
                            .collect(Collectors.toList());

                    // upload files

                    selectedConfigs.get(0).DropboxClient().files().uploadBuilder("/" + jobDbDir + "/" + trackName1)
                            .withMode(WriteMode.OVERWRITE)
                            .uploadAndFinish(new FileInputStream("./tempfiles/" + trackName1));
                    logger.info("Sent track 1 to DB " + selectedConfigs.get(0).getId());

                    selectedConfigs.get(1).DropboxClient().files().uploadBuilder("/" + jobDbDir + "/" + trackName2)
                            .withMode(WriteMode.OVERWRITE)
                            .uploadAndFinish(new FileInputStream("./tempfiles/" + trackName2));
                    logger.info("Sent track 2 to DB " + selectedConfigs.get(1).getId());

                    // send upload logs to mon
                    // T004;UPLOAD;arqbla1.p01;myprojectdb;user1TOKEN;
                    outToServer.writeUTF(String.format(
                            Locale.getDefault(),
                            "UPLOAD;%s;%s;dbU%dToken;",
                            trackName1, jobDbDir, selectedConfigs.get(0).getId()));
                    outToServer.writeUTF(String.format(
                            Locale.getDefault(),
                            "UPLOAD;%s;%s;dbU%dToken;",
                            trackName2, jobDbDir, selectedConfigs.get(1).getId()));

                    String jobCompleted = inFromServer.readUTF();
                    logger.info(jobCompleted);
                }
                // if download
                else if (jobTypeString.contains("download")) {
                    // Get DbDir
                    String jobDbDir = inFromServer.readUTF();
                    logger.info(jobDbDir);

                    // Get NomeArquivo
                    String jobFileName = inFromServer.readUTF();
                    logger.info(jobFileName);

                    // Gen tracks name
                    String trackName1 = String.format(Locale.getDefault(), "%s.p01",
                            jobFileName.substring(0, jobFileName.lastIndexOf('.')));
                    String trackName2 = String.format(Locale.getDefault(), "%s.p02",
                            jobFileName.substring(0, jobFileName.lastIndexOf('.')));

                    // Search clients for tracks
                    Map<AbstractMap.SimpleEntry<String, String>, DropboxConfig> tracksClient = new HashMap<AbstractMap.SimpleEntry<String, String>, DropboxConfig>();
                    boolean foundTrack1 = false;
                    for (DropboxConfig dbConfig : dbClients) {
                        DbxClientV2 client = dbConfig.DropboxClient();
                        SearchV2Result resultTrack1 = client.files().searchV2(trackName1);
                        while (true) {
                            if (resultTrack1.getMatches().size() > 0) {
                                tracksClient.put(new AbstractMap.SimpleEntry<String, String>(trackName1,
                                        ((FileMetadata) resultTrack1.getMatches().get(0).getMetadata()
                                                .getMetadataValue()).getPathLower()),
                                        dbConfig);
                                foundTrack1 = true;
                                break;
                            }

                            if (!resultTrack1.getHasMore()) {
                                break;
                            }

                            resultTrack1 = client.files().searchContinueV2(resultTrack1.getCursor());
                        }
                        if (foundTrack1) {
                            break;
                        }
                    }
                    logger.info("Found client with Track1: " + foundTrack1);

                    boolean foundTrack2 = false;
                    for (DropboxConfig dbConfig : dbClients) {
                        DbxClientV2 client = dbConfig.DropboxClient();
                        SearchV2Result resultTrack2 = client.files().searchV2(trackName2);
                        while (true) {
                            if (resultTrack2.getMatches().size() > 0) {
                                tracksClient.put(new AbstractMap.SimpleEntry<String, String>(trackName2,
                                        ((FileMetadata) resultTrack2.getMatches().get(0).getMetadata()
                                                .getMetadataValue()).getPathLower()),
                                        dbConfig);
                                foundTrack2 = true;
                                break;
                            }

                            if (!resultTrack2.getHasMore()) {
                                break;
                            }

                            resultTrack2 = client.files().searchContinueV2(resultTrack2.getCursor());
                        }
                        if (foundTrack2) {
                            break;
                        }
                    }
                    logger.info("Found client with Track2: " + foundTrack2);

                    // Download tracks to tempfiles
                    List<String> tracksToSend = new ArrayList<String>();
                    for (Map.Entry<AbstractMap.SimpleEntry<String, String>, DropboxConfig> entry : tracksClient
                            .entrySet()) {
                        DbxClientV2 client = entry.getValue().DropboxClient();
                        logger.info("Downloading Track Client: " + entry.getValue().getId() + " "
                                + entry.getKey().getKey());

                        DbxDownloader<FileMetadata> downloader = client.files().download(entry.getKey().getValue());
                        FileOutputStream out = new FileOutputStream("./tempfiles/download/" + entry.getKey().getKey());
                        downloader.download(out);
                        out.close();

                        // send download logs to Mon
                        outToServer.writeUTF(String.format(
                                Locale.getDefault(),
                                "DOWNLOAD;%s;%s;dbU%dToken;",
                                entry.getKey().getKey(), jobDbDir, entry.getValue().getId()));

                        logger.info(
                                "Downloaded Track Client: " + entry.getValue().getId() + " " + entry.getKey().getKey());
                        tracksToSend.add("./tempfiles/download/" + entry.getKey().getKey());
                    }

                    // Concat files
                    // send concat log to Mon
                    // T008;CONCAT;arqbla1.map;output;
                    outToServer.writeUTF(String.format(
                            Locale.getDefault(),
                            "CONCAT;%s;output;",
                            jobFileName));

                    // send TamanhoArquivo to Mon
                    tracksToSend.sort(Comparator.naturalOrder());
                    for (String trackToSend : tracksToSend) {
                        Path path = Paths.get(trackToSend);
                        outToServer.writeLong(Files.size(path));
                    }
                    for (String trackToSend : tracksToSend) {
                        Path path = Paths.get(trackToSend);
                        InputStream fileInputStream = Files.newInputStream(path);
                        byte[] buffer = new byte[16 * 1024];
                        int count;
                        while ((count = fileInputStream.read(buffer)) > 0) {
                            outToServer.write(buffer, 0, count);
                        }
                        logger.info("File sent");
                    }

                    String jobCompleted = inFromServer.readUTF();
                    logger.info(jobCompleted);
                }
                // if finish
                else {
                    break;
                }
                clientSocket.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
