package br.com.cadastro.app;

import br.com.cadastro.model.Usuario;
import br.com.cadastro.service.UsuarioService;
import java.awt.*;
import java.sql.SQLException;
import java.util.Optional;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Tela de entrada e do cadastro do administrador inicial. */
final class LoginFrame extends JFrame {
  private final UsuarioService usuarios = new UsuarioService();
  private final JTextField login = new JTextField();
  private final JPasswordField senha = new JPasswordField();
  private final JLabel orientacao = new JLabel();
  private boolean primeiroAcesso;

  LoginFrame() {
    super("Acesso ao Cadastro de Clientes");
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setSize(430, 330);
    setLocationRelativeTo(null);
    setResizable(false);
    setContentPane(conteudo());
    carregarModo();
  }

  private JComponent conteudo() {
    JPanel p = new JPanel();
    p.setBorder(new EmptyBorder(28, 34, 28, 34));
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    JLabel titulo = new JLabel("Cadastro de Clientes");
    titulo.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 24));
    titulo.setAlignmentX(Component.LEFT_ALIGNMENT);
    orientacao.setFont(new Font("Segoe UI", Font.PLAIN, 13));
    orientacao.setAlignmentX(Component.LEFT_ALIGNMENT);
    p.add(titulo); p.add(Box.createVerticalStrut(8)); p.add(orientacao); p.add(Box.createVerticalStrut(22));
    p.add(rotulo("Login")); p.add(login); p.add(Box.createVerticalStrut(12));
    p.add(rotulo("Senha")); p.add(senha); p.add(Box.createVerticalStrut(22));
    JButton entrar = new JButton("Entrar");
    entrar.setAlignmentX(Component.LEFT_ALIGNMENT);
    entrar.addActionListener(e -> entrar());
    p.add(entrar);
    getRootPane().setDefaultButton(entrar);
    return p;
  }

  private JLabel rotulo(String texto) { JLabel l = new JLabel(texto); l.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13)); return l; }

  private void carregarModo() {
    executar(() -> {
      primeiroAcesso = !usuarios.possuiUsuarios();
      SwingUtilities.invokeLater(() -> orientacao.setText(primeiroAcesso
          ? "Primeiro acesso: crie o administrador do sistema."
          : "Informe suas credenciais para acessar o sistema."));
    });
  }

  private void entrar() {
    executar(() -> {
      String nome = login.getText();
      char[] segredo = senha.getPassword();
      if (primeiroAcesso) {
        usuarios.criarPrimeiroAdministrador(nome, segredo);
        abrir(new Usuario(0, nome.trim(), true));
      } else {
        Optional<Usuario> usuario = usuarios.autenticar(nome, segredo);
        if (usuario.isEmpty()) throw new IllegalArgumentException("Login ou senha inválidos.");
        abrir(usuario.get());
      }
    });
  }

  private void abrir(Usuario usuario) {
    SwingUtilities.invokeLater(() -> { dispose(); new MainFrame(usuario).setVisible(true); });
  }

  private void executar(Tarefa tarefa) {
    new SwingWorker<Void, Void>() {
      protected Void doInBackground() throws Exception { tarefa.run(); return null; }
      protected void done() { try { get(); } catch (Exception e) { Throwable t = e.getCause() == null ? e : e.getCause(); JOptionPane.showMessageDialog(LoginFrame.this, t.getMessage(), "Acesso", JOptionPane.ERROR_MESSAGE); senha.setText(""); } }
    }.execute();
  }
  @FunctionalInterface private interface Tarefa { void run() throws SQLException; }
}
