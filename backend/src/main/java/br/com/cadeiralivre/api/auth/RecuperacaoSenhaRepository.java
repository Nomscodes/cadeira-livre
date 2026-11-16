package br.com.cadeiralivre.api.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, UUID> {

    // vale sempre o pedido mais recente: pedir de novo substitui o código anterior
    Optional<RecuperacaoSenha> findFirstByUsuarioIdOrderByCriadaEmDesc(UUID usuarioId);
}
