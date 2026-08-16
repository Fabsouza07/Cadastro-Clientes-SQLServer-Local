package br.com.cadastro.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class Config {
  private static final Properties P = new Properties();

  static {
    try (InputStream in = Config.class.getResourceAsStream("/config.properties")) {
      if (in == null) {
        throw new IllegalStateException("Arquivo config.properties não encontrado.");
      }
      P.load(in);
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private Config() {}

  public static String get(String chave) {
    String env = System.getenv(chave.toUpperCase().replace('.', '_'));
    return env != null && !env.isBlank() ? env : P.getProperty(chave, "").trim();
  }
}
