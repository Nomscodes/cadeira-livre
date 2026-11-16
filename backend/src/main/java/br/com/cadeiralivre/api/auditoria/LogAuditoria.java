package br.com.cadeiralivre.api.auditoria;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Uma linha do histórico de ações críticas: quem fez, quando, em qual barbearia e o quê. */
@Entity
@Table(name = "log_auditoria")
@Getter
@Setter
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "barbearia_id")
    private UUID barbeariaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AcaoAuditoria acao;

    @Column(length = 500)
    private String detalhe;
}
