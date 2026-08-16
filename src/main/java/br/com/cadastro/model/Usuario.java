package br.com.cadastro.model;

/** Usuário autenticável do sistema. */
public record Usuario(long id, String login, boolean administrador) {}
