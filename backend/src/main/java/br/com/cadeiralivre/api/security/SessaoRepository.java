package br.com.cadeiralivre.api.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessaoRepository extends JpaRepository<Sessao, UUID> {

    // sessão que ainda vale: existe e ninguém fez logout com ela
    boolean existsByIdAndEncerradaEmIsNull(UUID id);

    // troca de senha pela recuperação: encerra todas as sessões abertas da conta (quem tinha o token antigo sai)
    @Modifying
    @Query("update Sessao s set s.encerradaEm = :agora where s.usuario.id = :usuarioId and s.encerradaEm is null")
    int encerrarTodasDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("agora") Instant agora);

    // limpeza diária: apaga as sessões que já venceram e devolve quantas foram apagadas
    long deleteByExpiraEmBefore(Instant agora);
}
