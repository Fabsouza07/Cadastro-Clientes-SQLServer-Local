package br.com.cadastro.model;

public record Cliente(Long id, String nome, int idade, String cidade, String email, String telefoneFixo, String telefoneCelular) {}
