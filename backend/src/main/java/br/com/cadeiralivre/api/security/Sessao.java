package br.com.cadeiralivre.api.security;

import java.time.Instant;
import java.util.UUID;

import br.com.cadeiralivre.api.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sessão aberta no login (RF04). O token leva o id dela e o logout encerra a sessão (RF05). */
@Entity
@Table(name = "sessao")
@Getter
@Setter
@NoArgsConstructor
public class Sessao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    // vence junto com o token
    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    // null enquanto o usuário não fizer logout
    @Column(name = "encerrada_em")
    private Instant encerradaEm;
}
