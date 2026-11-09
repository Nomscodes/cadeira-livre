package br.com.cadeiralivre.api.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessaoRepository extends JpaRepository<Sessao, UUID> {

    // sessão que ainda vale: existe e ninguém fez logout com ela
    boolean existsByIdAndEncerradaEmIsNull(UUID id);

    // limpeza diária: apaga as sessões que já venceram e devolve quantas foram apagadas
    long deleteByExpiraEmBefore(Instant agora);
}
