package com.wsudesc.otavio;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Monitor {
  public Monitor() {
  }

  private static Logger logger = Logger.getLogger(Monitor.class.getName());
  // Lista síncrona de logs {
  private static List<String> logList = Collections.synchronizedList(new ArrayList<>());

  private static LinkedBlockingQueue<Job> jobsQueue = new LinkedBlockingQueue<>();

  private static int NClientes;
  private static String DbDir;

  public static void setNroClient(int nclients) throws Exception {
    NClientes = nclients;
  }

  public static int getNroClient() throws Exception {
    return NClientes;
  }

  public static void setDbDir(String dbDir) throws Exception {
    DbDir = dbDir;
  }

  public static String getdbDir() throws Exception {
    return DbDir;
  }

  public static void setServer() throws Exception {
    Scanner sc = new Scanner(System.in);
    while (true) {
      if (!sc.hasNextLine()) {
        break;
      }
      String sCurrentLine = sc.nextLine();
      String[] word = sCurrentLine.split("=");
      switch (word[0]) {
        case "NofClient":
          setNroClient(Integer.parseInt(word[1]));
          break;
        case "dbDIR":
          setDbDir(word[1]);
          break;

        default:
      }
    }

    sc.close();
    // Ler diretório de entrada
    // Montar fila de jobs (upload)
    //
    try (Stream<Path> paths = Files.walk(Paths.get("./input"))) {
      paths
          .filter(Files::isRegularFile)
          // .forEach(System.out::println);
          .forEach((path -> jobsQueue.add(new Job(JobType.UPLOAD, path))));
    }
  }

  static void printReport() {
    System.out.println("*** Log DESCMon File ***");
    for (String log : logList) {
      System.out.println(log);
    }
    System.out.println("****** End DESCMon ******");
  }

  static private byte[] toPrimitives(Byte[] oBytes) {
    byte[] bytes = new byte[oBytes.length];
    for (int i = 0; i < oBytes.length; i++) {
      bytes[i] = oBytes[i];
    }
    return bytes;
  }

  public static void main(String[] args) {
    try {
      // System.out.println("Configurando servidor ...");
      logger.setLevel(Level.ALL);
      setServer();
      // Instancia o objeto servidor e a sua stub
      ServerSocket welcomeSocket = new ServerSocket(4355);

      // System.out.println("Servidor pronto ...");
      logger.info(String.valueOf(getNroClient()));
      logger.info(String.valueOf(jobsQueue.size()));

      List<Thread> jobThreads = new ArrayList<>();

      long qtdFiles = jobsQueue.size();

      for (int i = 0; i < qtdFiles * 2; i++) {
        logger.info(String.format("Job %d", i));
        Socket connectionSocket = welcomeSocket.accept();
        // setNroClient(getNroClient() - 1);
        Thread thread = new Thread() {
          @Override
          public void run() {
            try {

              DataInputStream inFromClient = new DataInputStream(connectionSocket.getInputStream());
              DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());

              // Receber job da fila
              // Caso não tenha job, espere uma nova inserção na fila.
              Job job = jobsQueue.take();

              // Se job.type == upload: \/
              if (job.getType() == JobType.UPLOAD) {
                // T001;LIDO;arqbla1.map;input;
                long tCount = logList.size() + 1;
                logList.add(
                    String.format(
                        Locale.getDefault(),
                        "T%03d;LIDO;%s;input;",
                        tCount,
                        job.getFilepath().getFileName().toString()));

                outToClient.writeUTF("upload"); // job.type [?]
                logger.info("Sent upload job");

                // Send DbDir to Client
                outToClient.writeUTF(DbDir);
                logger.info("Sent DB Dir " + DbDir);

                // monitor envia para o cliente:
                // - nome do arquivo || - tamanho do arquivo
                outToClient.writeUTF(job.getFilepath().getFileName().toString());
                logger.info("Sent job.getFilepath " + job.getFilepath().getFileName().toString());
                outToClient.writeLong(Files.size(job.getFilepath()));
                logger.info("Sent Files.size(job.getFilepath()) " + Files.size(job.getFilepath()));

                // || - bytes do arquivo
                InputStream fileInputStream = Files.newInputStream(job.getFilepath());
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = fileInputStream.read(buffer)) > 0) {
                  Byte[] cryptedBuffer = IntStream.range(0, buffer.length)
                      .mapToObj(i -> (byte) buffer[i])
                      .map((byte_value -> ((byte) ((byte_value.intValue() + 3) % Byte.MAX_VALUE))))
                      .toArray(Byte[]::new);

                  outToClient.write(toPrimitives(cryptedBuffer), 0, count);
                }
                logger.info("File sent");

                // Receber do cliente:
                // - 2 logs de split
                String logClient;
                logClient = inFromClient.readUTF();
                logList.add(logClient);
                logger.info(logClient);
                logClient = inFromClient.readUTF();
                logList.add(logClient);
                logger.info(logClient);

                // Wait for complete

                // || - 2 logs de upload
                logClient = inFromClient.readUTF();
                logList.add(logClient);
                logger.info(logClient);
                logClient = inFromClient.readUTF();
                logList.add(logClient);
                logger.info(logClient);

                // Add Download Job
                jobsQueue.add(new Job(JobType.DOWNLOAD, job.getFilepath()));
                logger.info("Added new job download");

                outToClient.writeUTF("completed");
              }
              // Se job.type == download:
              else if (job.getType() == JobType.DOWNLOAD) {

                outToClient.writeUTF("download");
                logger.info("Sent download job");

                // Send DbDir to Client
                outToClient.writeUTF(DbDir);
                logger.info("Sent DB Dir " + DbDir);

                // monitor envia para o cliente:
                // - nome do arquivo || - tamanho do arquivo
                outToClient.writeUTF(job.getFilepath().getFileName().toString());
                logger.info("Sent job.getFilepath " + job.getFilepath().getFileName().toString());

                // Receber do cliente:
                // - 2 logs de download
                String logClient;
                logClient = inFromClient.readUTF();
                logList.add(logClient);
                logger.info(logClient);
                logClient = inFromClient.readUTF();
                logList.add(logClient);
                logger.info(logClient);
                // || - 1 log de concat/merge
                logClient = inFromClient.readUTF();
                logList.add(logClient);
                logger.info(logClient);

                // TODO: Receber bytes do arquivo
                // OutputStream fileOutputStream = Files.newOutputStream(job.getFilepath());
                // byte[] buffer = new byte[16 * 1024];
                // int count;
                // while ((count = inFromClient.read(buffer)) > 0) {
                //   Byte[] decryptedBuffer = IntStream.range(0, buffer.length)
                //       .mapToObj(i -> (byte) buffer[i])
                //       .map((byte_value -> ((byte) ((byte_value.intValue() - 3) % Byte.MAX_VALUE))))
                //       .toArray(Byte[]::new);
                //   fileOutputStream.write(
                //       toPrimitives(decryptedBuffer),
                //       0, count);
                // }

                outToClient.writeUTF("completed");
                logger.info("completed");
              } else {
                // outToClient.writeUTF("finish");
              }
            } catch (IOException | InterruptedException exception) {
            }
          }
        };
        thread.start();
        jobThreads.add(thread);
      }

      for (int i = 0; i < NClientes; i++) {
        Socket connectionSocket = welcomeSocket.accept();
        // setNroClient(getNroClient() - 1);
        Thread thread = new Thread() {
          @Override
          public void run() {
            try {
              DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
              outToClient.writeUTF("finish");
            } catch (IOException exception) {

            }
          }
        };
        thread.start();
        jobThreads.add(thread);
      }

      for (Thread jobThread : jobThreads) {
        jobThread.join();
      }

      printReport();
      // System.out.println("Servidor finalizado com sucesso!");
      welcomeSocket.close();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
}
