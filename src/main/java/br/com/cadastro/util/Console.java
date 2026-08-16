package br.com.cadastro.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public final class Console {
  private static final Scanner SCANNER = new Scanner(System.in);
  private static final String LINHA = "═".repeat(76);

  private Console() {}

  public static void limpar() {
    System.out.print("\033[H\033[2J");
    System.out.flush();
  }

  public static void cabecalho(String banco) {
    limpar();
    System.out.println("╔" + LINHA + "╗");
    System.out.printf("║%-76s║%n", centralizar("SISTEMA DE CADASTRO DE CLIENTES", 76));
    System.out.printf(
        "║%-76s║%n", centralizar("Projeto de estudo • Java 25 • JDBC • " + banco, 76));
    System.out.println("╚" + LINHA + "╝");
    System.out.println(
        "Data/Hora: "
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
  }

  public static String ler(String rotulo) {
    System.out.print(rotulo);
    return SCANNER.nextLine().trim();
  }

  public static int lerInt(String rotulo) {
    while (true) {
      try {
        return Integer.parseInt(ler(rotulo));
      } catch (NumberFormatException e) {
        aviso("Digite um número inteiro.");
      }
    }
  }

  public static long lerLong(String rotulo) {
    while (true) {
      try {
        return Long.parseLong(ler(rotulo));
      } catch (NumberFormatException e) {
        aviso("Digite um código numérico.");
      }
    }
  }

  public static boolean confirmar(String mensagem) {
    return ler(mensagem + " (S/N): ").equalsIgnoreCase("S");
  }

  public static void sucesso(String mensagem) {
    System.out.println("[SUCESSO] " + mensagem);
  }

  public static void aviso(String mensagem) {
    System.out.println("[AVISO] " + mensagem);
  }

  public static void erro(String mensagem) {
    System.out.println("[ERRO] " + mensagem);
  }

  public static void pausar() {
    ler("\nPressione ENTER para continuar...");
  }

  private static String centralizar(String texto, int tamanho) {
    int espacos = Math.max(0, (tamanho - texto.length()) / 2);
    return " ".repeat(espacos) + texto;
  }
}
