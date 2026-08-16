package br.com.cadastro.model;

public record Estatisticas(
    long total, double idadeMedia, String clienteMaisVelho, String cidadeMaisFrequente) {}
