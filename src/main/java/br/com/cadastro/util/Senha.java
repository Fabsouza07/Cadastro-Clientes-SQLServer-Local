package br.com.cadastro.util;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Geração e comparação de hashes de senha usando PBKDF2. */
public final class Senha {
  private static final int ITERACOES = 210_000;
  private static final int TAMANHO_HASH_BITS = 256;

  private Senha() {}

  public static byte[] novoSalt() {
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    return salt;
  }

  public static byte[] hash(char[] senha, byte[] salt) {
    try {
      PBEKeySpec especificacao = new PBEKeySpec(senha, salt, ITERACOES, TAMANHO_HASH_BITS);
      try {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(especificacao)
            .getEncoded();
      } finally {
        especificacao.clearPassword();
      }
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("Não foi possível proteger a senha.", e);
    }
  }

  public static boolean confere(char[] senha, byte[] salt, byte[] esperado) {
    return Arrays.equals(hash(senha, salt), esperado);
  }
}
