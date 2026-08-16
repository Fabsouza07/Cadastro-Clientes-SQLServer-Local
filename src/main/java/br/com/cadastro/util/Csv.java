package br.com.cadastro.util;

import br.com.cadastro.model.Cliente;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Csv {
  private Csv() {}

  public static void exportar(List<Cliente> lista, Path arquivo) throws IOException {
    try (Writer writer = Files.newBufferedWriter(arquivo)) {
      writer.write("id;nome;idade;cidade;email;telefone_fixo;telefone_celular\n");
      for (Cliente cliente : lista) {
        writer.write(
            "%d;%s;%d;%s;%s;%s;%s%n"
                .formatted(
                    cliente.id(),
                    limpar(cliente.nome()),
                    cliente.idade(),
                    limpar(cliente.cidade()),
                    limpar(cliente.email()),
                    limpar(cliente.telefoneFixo()),
                    limpar(cliente.telefoneCelular())));
      }
    }
  }

  private static String limpar(String valor) {
    return valor == null ? "" : valor.replace(";", ",").replace("\n", " ");
  }
}
