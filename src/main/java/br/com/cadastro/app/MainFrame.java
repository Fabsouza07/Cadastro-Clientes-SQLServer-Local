package br.com.cadastro.app;

import br.com.cadastro.model.Cliente;
import br.com.cadastro.model.Estatisticas;
import br.com.cadastro.model.Usuario;
import br.com.cadastro.service.ClienteService;
import br.com.cadastro.service.UsuarioService;
import br.com.cadastro.util.Config;
import br.com.cadastro.util.Csv;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.MaskFormatter;

final class MainFrame extends JFrame {
  private static final Color NAVY = new Color(15, 23, 42), BLUE = new Color(37, 99, 235);
  private static final Color TEXT = new Color(30, 41, 59), MUTED = new Color(100, 116, 139);
  private static final String MASCARA_TELEFONE_FIXO = "(##) ####-####";
  private static final String MASCARA_TELEFONE_CELULAR = "(##) #####-####";
  private final ClienteService service = new ClienteService();
  private final UsuarioService usuarioService = new UsuarioService();
  private final Usuario usuario;
  private final DefaultTableModel tableModel =
      new DefaultTableModel(
          new String[] {"ID", "Nome", "Idade", "Cidade", "E-mail", "Fixo", "Celular"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };
  private final JTable table = new JTable(tableModel);
  private final JTextField search = new JTextField();
  private final JLabel total = valueLabel(),
      average = valueLabel(),
      oldest = valueLabel(),
      city = valueLabel();
  private boolean telefonesPadronizados;

  MainFrame(Usuario usuario) {
    super("Cadastro de Clientes");
    this.usuario = usuario;
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    setUndecorated(true); // Remove e, portanto, desabilita os controles nativos da janela.
    // O VcXsrv pode não ter gerenciador de janelas; ocupa a tela diretamente
    // para evitar maximização incompleta e faixas de outra janela no topo.
    setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            solicitarSaida();
          }
        });
    setContentPane(createContent());
    carregarDados();
  }

  private JPanel createContent() {
    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(new Color(248, 250, 252));
    root.add(header(), BorderLayout.NORTH);
    root.add(sidebar(), BorderLayout.WEST);
    root.add(mainArea(), BorderLayout.CENTER);
    return root;
  }

  private JComponent header() {
    JPanel p = new JPanel(new BorderLayout());
    p.setBackground(NAVY);
    p.setBorder(new EmptyBorder(15, 26, 15, 22));
    JLabel brand = new JLabel("C   Cadastro de Clientes");
    brand.setForeground(Color.WHITE);
    brand.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 20));
    JLabel db = new JLabel((usuario.administrador() ? "Administrador: " : "Operador: ") + usuario.login() + "   |   Banco: " + Config.get("app.banco") + "   ");
    db.setForeground(new Color(203, 213, 225));
    db.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    JButton exit = button("Sair", new Color(51, 65, 85));
    exit.addActionListener(e -> solicitarSaida());
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    actions.setOpaque(false);
    actions.add(db);
    actions.add(exit);
    p.add(brand, BorderLayout.WEST);
    p.add(actions, BorderLayout.EAST);
    return p;
  }

  private JComponent sidebar() {
    JPanel p = new JPanel();
    p.setBackground(Color.WHITE);
    p.setBorder(new MatteBorder(0, 0, 0, 1, new Color(226, 232, 240)));
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.setPreferredSize(new Dimension(220, 0));
    JLabel section = new JLabel("GESTÃO");
    section.setForeground(MUTED);
    section.setFont(new Font("Segoe UI", Font.BOLD, 11));
    section.setBorder(new EmptyBorder(28, 24, 10, 0));
    section.setAlignmentX(LEFT_ALIGNMENT);
    p.add(section);
    p.add(navButton("Visão geral", this::carregarDados));
    p.add(navButton("Novo cliente", () -> abrirFormulario(null)));
    p.add(navButton("Atualizar dados", this::editarSelecionado));
    if (usuario.administrador()) p.add(navButton("Usuários", this::gerenciarUsuarios));
    p.add(Box.createVerticalGlue());
    JLabel note = new JLabel("  Dados protegidos localmente");
    note.setForeground(MUTED);
    note.setFont(new Font("Segoe UI", Font.PLAIN, 11));
    note.setAlignmentX(LEFT_ALIGNMENT);
    note.setBorder(new EmptyBorder(0, 12, 22, 0));
    p.add(note);
    return p;
  }

  private JComponent mainArea() {
    JPanel p = new JPanel(new BorderLayout(0, 18));
    p.setBackground(new Color(248, 250, 252));
    p.setBorder(new EmptyBorder(28, 30, 28, 30));
    JPanel intro = new JPanel(new BorderLayout());
    intro.setOpaque(false);
    JPanel titles = new JPanel();
    titles.setOpaque(false);
    titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
    JLabel title = new JLabel("Visão geral");
    title.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 27));
    title.setForeground(TEXT);
    JLabel sub = new JLabel("Acompanhe e gerencie os clientes cadastrados.");
    sub.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    sub.setForeground(MUTED);
    titles.add(title);
    titles.add(Box.createVerticalStrut(5));
    titles.add(sub);
    intro.add(titles, BorderLayout.WEST);
    JButton add = button("+  Novo cliente", BLUE);
    add.addActionListener(e -> abrirFormulario(null));
    intro.add(add, BorderLayout.EAST);
    p.add(intro, BorderLayout.NORTH);

    JPanel center = new JPanel(new BorderLayout(0, 18));
    center.setOpaque(false);
    center.add(cards(), BorderLayout.NORTH);
    center.add(tablePanel(), BorderLayout.CENTER);
    p.add(center, BorderLayout.CENTER);
    return p;
  }

  private JComponent cards() {
    JPanel p = new JPanel(new GridLayout(1, 4, 14, 0));
    p.setOpaque(false);
    p.add(card("Total de clientes", total, new Color(219, 234, 254)));
    p.add(card("Idade média", average, new Color(220, 252, 231)));
    p.add(card("Cliente mais velho", oldest, new Color(254, 243, 199)));
    p.add(card("Cidade em destaque", city, new Color(243, 232, 255)));
    return p;
  }

  private JComponent tablePanel() {
    JPanel p = new JPanel(new BorderLayout(0, 14));
    p.setBackground(Color.WHITE);
    p.setBorder(
        new CompoundBorder(
            new LineBorder(new Color(226, 232, 240)), new EmptyBorder(18, 18, 18, 18)));
    JPanel top = new JPanel(new BorderLayout());
    top.setOpaque(false);
    JLabel label = new JLabel("Clientes cadastrados");
    label.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 17));
    label.setForeground(TEXT);
    top.add(label, BorderLayout.WEST);
    search.putClientProperty("JTextField.placeholderText", "Pesquisar por nome ou cidade");
    search.setPreferredSize(new Dimension(245, 34));
    search
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                pesquisar();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                pesquisar();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                pesquisar();
              }
            });
    top.add(search, BorderLayout.EAST);
    p.add(top, BorderLayout.NORTH);
    table.setRowHeight(38);
    table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    table.setBackground(Color.WHITE);
    table.setFillsViewportHeight(true);
    table.setForeground(TEXT);
    table.setSelectionBackground(new Color(219, 234, 254));
    table.setSelectionForeground(TEXT);
    table.setShowVerticalLines(false);
    table.getTableHeader().setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));
    table.getTableHeader().setBackground(new Color(248, 250, 252));
    table.getTableHeader().setForeground(MUTED);
    table.setAutoCreateRowSorter(true);
    table.getColumnModel().getColumn(0).setMaxWidth(70);
    table.getColumnModel().getColumn(2).setMaxWidth(90);
    JScrollPane scroll = new JScrollPane(table);
    scroll.getViewport().setBackground(Color.WHITE);
    p.add(scroll, BorderLayout.CENTER);
    JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
    bottom.setOpaque(false);
    JButton edit = secondary("Editar");
    edit.addActionListener(e -> editarSelecionado());
    JButton del = secondary("Excluir");
    del.addActionListener(e -> excluirSelecionado());
    if (usuario.administrador()) {
      JButton export = secondary("Exportar CSV");
      export.addActionListener(e -> exportar());
      bottom.add(export);
    }
    bottom.add(edit);
    bottom.add(del);
    p.add(bottom, BorderLayout.SOUTH);
    return p;
  }

  private void carregarDados() {
    executar(
        () -> {
          if (!telefonesPadronizados) {
            service.padronizarTelefones();
            telefonesPadronizados = true;
          }
          preencherTabela(service.listar("nome"));
          atualizarEstatisticas(service.estatisticas());
        });
  }

  private void pesquisar() {
    executar(
        () ->
            preencherTabela(
                search.getText().isBlank()
                    ? service.listar("nome")
                    : service.pesquisar(search.getText().trim())));
  }

  private void preencherTabela(List<Cliente> clientes) {
    tableModel.setRowCount(0);
    for (Cliente c : clientes) {
      tableModel.addRow(
          new Object[] {
            c.id(), c.nome(), c.idade(), c.cidade(), c.email(), c.telefoneFixo(), c.telefoneCelular()
          });
    }
  }

  private void atualizarEstatisticas(Estatisticas e) {
    total.setText(String.valueOf(e.total()));
    average.setText(String.format("%.1f anos", e.idadeMedia()));
    oldest.setText(e.clienteMaisVelho());
    city.setText(e.cidadeMaisFrequente());
  }

  private void abrirFormulario(Cliente cliente) {
    JTextField nome = new JTextField(cliente == null ? "" : cliente.nome());
    JTextField idade = new JTextField(cliente == null ? "" : String.valueOf(cliente.idade()));
    JTextField cidadeField = new JTextField(cliente == null ? "" : cliente.cidade());
    JTextField email = new JTextField(cliente == null ? "" : cliente.email());
    JFormattedTextField telefoneFixo =
        campoTelefone(MASCARA_TELEFONE_FIXO, cliente == null ? null : cliente.telefoneFixo());
    JFormattedTextField telefoneCelular =
        campoTelefone(MASCARA_TELEFONE_CELULAR, cliente == null ? null : cliente.telefoneCelular());
    JPanel form = new JPanel(new GridLayout(0, 1, 0, 8));
    form.setBorder(new EmptyBorder(8, 12, 8, 12));
    form.setPreferredSize(new Dimension(460, 430));
    for (Object[] f :
        new Object[][] {
          {"Nome", nome},
          {"Idade", idade},
          {"Cidade", cidadeField},
          {"E-mail", email},
          {"Telefone fixo", telefoneFixo},
          {"Celular", telefoneCelular}
        }) {
      JLabel l = new JLabel((String) f[0]);
      l.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));
      form.add(l);
      form.add((JTextField) f[1]);
    }
    int option =
        JOptionPane.showConfirmDialog(
            this,
            form,
            cliente == null ? "Cadastrar cliente" : "Editar cliente",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    if (option != JOptionPane.OK_OPTION) return;
    executar(
        () -> {
          int years = Integer.parseInt(idade.getText().trim());
          if (cliente == null)
            service.cadastrar(
                nome.getText(),
                years,
                cidadeField.getText(),
                email.getText(),
                telefoneOuNulo(telefoneFixo),
                telefoneOuNulo(telefoneCelular));
          else
            service.alterar(
                cliente.id(),
                nome.getText(),
                years,
                cidadeField.getText(),
                email.getText(),
                telefoneOuNulo(telefoneFixo),
                telefoneOuNulo(telefoneCelular));
          carregarDados();
          mensagem("Cadastro salvo com sucesso.", JOptionPane.INFORMATION_MESSAGE);
        });
  }

  private void editarSelecionado() {
    Cliente c = selecionado();
    if (c != null) abrirFormulario(c);
  }

  private void excluirSelecionado() {
    Cliente c = selecionado();
    if (c != null
        && JOptionPane.showConfirmDialog(
                this,
                "Deseja excluir o cadastro de " + c.nome() + "?",
                "Confirmar exclusão",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE)
            == JOptionPane.YES_OPTION)
      executar(
          () -> {
            service.excluir(c.id());
            carregarDados();
          });
  }

  private Cliente selecionado() {
    int row = table.getSelectedRow();
    if (row < 0) {
      mensagem("Selecione um cliente na tabela.", JOptionPane.WARNING_MESSAGE);
      return null;
    }
    int modelRow = table.convertRowIndexToModel(row);
    return new Cliente(
        (Long) tableModel.getValueAt(modelRow, 0),
        (String) tableModel.getValueAt(modelRow, 1),
        (Integer) tableModel.getValueAt(modelRow, 2),
        (String) tableModel.getValueAt(modelRow, 3),
        (String) tableModel.getValueAt(modelRow, 4),
        (String) tableModel.getValueAt(modelRow, 5),
        (String) tableModel.getValueAt(modelRow, 6));
  }

  private static JFormattedTextField campoTelefone(String mascara, String valor) {
    try {
      MaskFormatter formatter = new MaskFormatter(mascara);
      formatter.setPlaceholderCharacter('_');
      formatter.setValueContainsLiteralCharacters(false);
      JFormattedTextField campo = new JFormattedTextField(formatter);
      campo.setValue(valor == null ? "" : valor.replaceAll("\\D", ""));
      return campo;
    } catch (ParseException e) {
      throw new IllegalStateException("Máscara de telefone inválida.", e);
    }
  }

  private static String telefoneOuNulo(JFormattedTextField campo) {
    String telefone = campo.getText();
    return telefone.replaceAll("\\D", "").isEmpty() ? null : telefone;
  }

  private void exportar() {
    executar(
        () -> {
          Path file = Path.of("clientes.csv");
          Csv.exportar(service.listar("nome"), file);
          mensagem(
              "Arquivo exportado em:\n" + file.toAbsolutePath(), JOptionPane.INFORMATION_MESSAGE);
        });
  }

  private void gerenciarUsuarios() {
    if (!usuario.administrador()) return;
    JDialog dialog = new JDialog(this, "Usuários", true);
    DefaultTableModel modelo = new DefaultTableModel(new String[] {"ID", "Login", "Perfil"}, 0) {
      @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    JTable usuariosTabela = new JTable(modelo);
    usuariosTabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    Runnable carregar = () -> executar(() -> {
      List<Usuario> lista = usuarioService.listar();
      SwingUtilities.invokeLater(() -> {
        modelo.setRowCount(0);
        for (Usuario u : lista) modelo.addRow(new Object[] {u.id(), u.login(), u.administrador() ? "Administrador" : "Operador"});
      });
    });
    JButton novo = secondary("Novo operador");
    novo.addActionListener(e -> criarOperador(dialog, carregar));
    JButton senhaUsuario = secondary("Alterar senha");
    senhaUsuario.addActionListener(e -> alterarSenhaUsuario(usuariosTabela, dialog));
    JButton excluir = secondary("Excluir operador");
    excluir.addActionListener(e -> excluirOperador(usuariosTabela, dialog, carregar));
    JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    botoes.add(novo); botoes.add(senhaUsuario); botoes.add(excluir);
    JPanel painel = new JPanel(new BorderLayout(0, 12));
    painel.setBorder(new EmptyBorder(16, 16, 16, 16));
    painel.add(new JLabel("Administradores não podem ser excluídos."), BorderLayout.NORTH);
    painel.add(new JScrollPane(usuariosTabela), BorderLayout.CENTER);
    painel.add(botoes, BorderLayout.SOUTH);
    dialog.setContentPane(painel);
    dialog.setSize(560, 360);
    dialog.setLocationRelativeTo(this);
    carregar.run();
    dialog.setVisible(true);
  }

  private void criarOperador(JDialog dialog, Runnable recarregar) {
    JTextField login = new JTextField();
    JPasswordField senha = new JPasswordField();
    JPanel form = formularioCredenciais(login, senha);
    if (JOptionPane.showConfirmDialog(dialog, form, "Novo operador", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
    executar(() -> { usuarioService.criarOperador(login.getText(), senha.getPassword()); recarregar.run(); mensagem("Operador cadastrado.", JOptionPane.INFORMATION_MESSAGE); });
  }

  private void alterarSenhaUsuario(JTable tabela, JDialog dialog) {
    int linha = tabela.getSelectedRow();
    if (linha < 0) { mensagem("Selecione um usuário.", JOptionPane.WARNING_MESSAGE); return; }
    long id = (Long) tabela.getValueAt(linha, 0);
    String login = (String) tabela.getValueAt(linha, 1);
    JPasswordField senha = new JPasswordField();
    JPanel form = new JPanel(new GridLayout(0, 1, 0, 7));
    form.add(new JLabel("Nova senha para " + login + ":")); form.add(senha);
    if (JOptionPane.showConfirmDialog(dialog, form, "Alterar senha", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
    executar(() -> { usuarioService.alterarSenha(id, senha.getPassword()); mensagem("Senha alterada com sucesso.", JOptionPane.INFORMATION_MESSAGE); });
  }

  private void excluirOperador(JTable tabela, JDialog dialog, Runnable recarregar) {
    int linha = tabela.getSelectedRow();
    if (linha < 0) { mensagem("Selecione um usuário.", JOptionPane.WARNING_MESSAGE); return; }
    if ("Administrador".equals(tabela.getValueAt(linha, 2))) { mensagem("Administradores não podem ser excluídos.", JOptionPane.WARNING_MESSAGE); return; }
    long id = (Long) tabela.getValueAt(linha, 0);
    String login = (String) tabela.getValueAt(linha, 1);
    if (JOptionPane.showConfirmDialog(dialog, "Excluir o operador " + login + "?", "Confirmar exclusão", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
    executar(() -> { usuarioService.excluirOperador(id); recarregar.run(); mensagem("Operador excluído.", JOptionPane.INFORMATION_MESSAGE); });
  }

  private static JPanel formularioCredenciais(JTextField login, JPasswordField senha) {
    JPanel form = new JPanel(new GridLayout(0, 1, 0, 7));
    form.setPreferredSize(new Dimension(330, 125));
    form.add(new JLabel("Login")); form.add(login); form.add(new JLabel("Senha (mínimo de 8 caracteres)")); form.add(senha);
    return form;
  }

  private void solicitarSaida() {
    if (JOptionPane.showConfirmDialog(
            this,
            "Deseja realmente sair do sistema?",
            "Confirmar saída",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE)
        == JOptionPane.YES_OPTION) dispose();
  }

  private void executar(Tarefa task) {
    new SwingWorker<Void, Void>() {
      @Override
      protected Void doInBackground() throws Exception {
        task.run();
        return null;
      }

      protected void done() {
        try {
          get();
        } catch (InterruptedException | ExecutionException e) {
          mensagem(
              "Não foi possível concluir a operação.\n\n" + detalhe(e), JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  private void mensagem(String text, int type) {
    SwingUtilities.invokeLater(
        () ->
            JOptionPane.showMessageDialog(
                this,
                text,
                type == JOptionPane.ERROR_MESSAGE ? "Erro" : "Cadastro de Clientes",
                type));
  }

  private static String detalhe(Exception e) {
    Throwable cause = e.getCause();
    Throwable t = cause != null ? cause : e;
    return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
  }

  private static JLabel valueLabel() {
    JLabel l = new JLabel("–");
    l.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 22));
    l.setForeground(TEXT);
    return l;
  }

  private static JComponent card(String title, JLabel value, Color accent) {
    JPanel p = new JPanel(new BorderLayout(0, 10));
    p.setBackground(Color.WHITE);
    p.setBorder(
        new CompoundBorder(
            new LineBorder(new Color(226, 232, 240)), new EmptyBorder(15, 16, 15, 16)));
    JLabel t = new JLabel(title);
    t.setForeground(MUTED);
    t.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    JPanel stripe = new JPanel();
    stripe.setBackground(accent);
    stripe.setPreferredSize(new Dimension(5, 0));
    p.add(stripe, BorderLayout.WEST);
    p.add(t, BorderLayout.NORTH);
    p.add(value, BorderLayout.CENTER);
    return p;
  }

  private JButton navButton(String text, Runnable action) {
    JButton b = new JButton(text);
    b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
    b.setAlignmentX(LEFT_ALIGNMENT);
    b.setHorizontalAlignment(SwingConstants.LEFT);
    b.setBorder(new EmptyBorder(0, 24, 0, 0));
    b.setForeground(TEXT);
    b.setBackground(Color.WHITE);
    b.setFocusPainted(false);
    b.setBorderPainted(false);
    b.setContentAreaFilled(false);
    b.setOpaque(false);
    b.setFocusable(false);
    b.setUI(new BasicButtonUI());
    b.addActionListener(e -> action.run());
    return b;
  }

  private static JButton button(String text, Color background) {
    JButton b =
        new JButton(text) {
          @Override
          protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
              canvas.setRenderingHint(
                  RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
              Color fill = getModel().isPressed() ? background.darker() : background;
              canvas.setColor(fill);
              canvas.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            } finally {
              canvas.dispose();
            }
            super.paintComponent(graphics);
          }
        };
    b.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 13));
    b.setForeground(Color.WHITE);
    b.setBackground(background);
    b.setOpaque(false);
    b.setContentAreaFilled(false);
    b.setFocusPainted(false);
    b.setBorderPainted(false);
    b.setBorder(new EmptyBorder(9, 16, 9, 16));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  private static JButton secondary(String text) {
    JButton b = new JButton(text);
    b.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 12));
    b.setForeground(TEXT);
    b.setBackground(Color.WHITE);
    b.setFocusPainted(false);
    b.setBorder(
        new CompoundBorder(
            new LineBorder(new Color(203, 213, 225)), new EmptyBorder(7, 12, 7, 12)));
    return b;
  }

  @FunctionalInterface
  private interface Tarefa {
    void run() throws Exception;
  }
}
