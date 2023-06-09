package com.wsudesc.otavio;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class DropboxConfig {

  private String dropboxAppSecret;
  private String dropboxAppKey;
  private String dropboxRefreshToken;
  private String dropboxAccessToken;
  private String dropboxFileConf;
  private int id;
  private DbxRequestConfig config;
  private DbxCredential credentials;

  public DropboxConfig(int id, String fileConf) {
    this.id = id;
    try (InputStream input = new FileInputStream(fileConf)) {

      Properties prop = new Properties();
      prop.load(input);
      dropboxAccessToken = prop.getProperty("dropbox.access.token");
      dropboxRefreshToken = prop.getProperty("dropbox.refresh.token");
      dropboxAppKey = prop.getProperty("dropbox.app.key");
      dropboxAppSecret = prop.getProperty("dropbox.app.secret");
      dropboxFileConf = fileConf;
      
    } catch (IOException ex) {
      ex.printStackTrace();
    }
    config = DbxRequestConfig.newBuilder(dropboxFileConf).build();
    credentials = new DbxCredential(dropboxAccessToken, -1L, dropboxRefreshToken, dropboxAppKey,
        dropboxAppSecret);
  }

  public int getId() {
    return this.id;
  }

  public DbxClientV2 DropboxClient() {

    return new DbxClientV2(config, credentials);
  }
}
