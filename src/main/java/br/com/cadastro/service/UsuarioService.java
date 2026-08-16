package br.com.cadastro.service;

import br.com.cadastro.dao.UsuarioDAO;
import br.com.cadastro.model.Usuario;
import br.com.cadastro.util.Senha;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class UsuarioService {
  private final UsuarioDAO dao = new UsuarioDAO();

  public void preparar() throws SQLException { dao.garantirTabela(); }
  public boolean possuiUsuarios() throws SQLException { return dao.existeAlgum(); }
  public List<Usuario> listar() throws SQLException { return dao.listar(); }

  public void criarPrimeiroAdministrador(String login, char[] senha) throws SQLException {
    if (dao.existeAlgum()) throw new IllegalStateException("Já existe um usuário cadastrado.");
    criar(login, senha, true);
  }

  public void criarOperador(String login, char[] senha) throws SQLException { criar(login, senha, false); }

  public Optional<Usuario> autenticar(String login, char[] senha) throws SQLException {
    try {
      Optional<UsuarioDAO.Registro> registro = dao.buscarAutenticacao(login.trim());
      if (registro.isEmpty() || !Senha.confere(senha, registro.get().salt(), registro.get().hash())) {
        if (registro.isPresent()) registrarFalha(registro.get().usuario().id());
        return Optional.empty();
      }
      if (registro.get().bloqueado() || !dao.registrarSucesso(registro.get().usuario().id())) {
        throw new LoginBloqueadoException();
      }
      return Optional.of(registro.get().usuario());
    } finally { Arrays.fill(senha, '\0'); }
  }

  private void registrarFalha(long id) throws SQLException {
    UsuarioDAO.Tentativa tentativa = dao.registrarFalha(id);
    if (tentativa == null || tentativa.bloqueado()) throw new LoginBloqueadoException();
  }

  public void alterarSenha(long id, char[] senha) throws SQLException {
    validarSenha(senha);
    try { byte[] salt = Senha.novoSalt(); dao.alterarSenha(id, Senha.hash(senha, salt), salt); }
    finally { Arrays.fill(senha, '\0'); }
  }

  public boolean excluirOperador(long id) throws SQLException { return dao.excluirOperador(id); }

  private void criar(String login, char[] senha, boolean admin) throws SQLException {
    validarLogin(login); validarSenha(senha);
    try { byte[] salt = Senha.novoSalt(); dao.inserir(login.trim(), Senha.hash(senha, salt), salt, admin); }
    finally { Arrays.fill(senha, '\0'); }
  }

  private static void validarLogin(String login) {
    if (login == null || !login.trim().matches("[A-Za-z0-9._-]{3,50}"))
      throw new IllegalArgumentException("Login deve ter de 3 a 50 caracteres (letras, números, ponto, hífen ou _).");
  }
  private static void validarSenha(char[] senha) {
    if (senha == null || senha.length < 8) throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres.");
  }

  private static final class LoginBloqueadoException extends IllegalArgumentException {
    LoginBloqueadoException() {
      super("Login bloqueado após três tentativas incorretas. Aguarde 10 minutos para tentar novamente.");
    }
  }
}
