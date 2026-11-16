package br.com.cadeiralivre.api.auth;

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

/** Pedido de recuperação de senha: o código (só o hash), a validade, as tentativas e quando foi usado. */
@Entity
@Table(name = "recuperacao_senha")
@Getter
@Setter
@NoArgsConstructor
public class RecuperacaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // o código nunca é guardado puro, só o hash (como a senha)
    @Column(name = "codigo_hash", nullable = false, length = 72)
    private String codigoHash;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "usada_em")
    private Instant usadaEm;

    /** O código ainda pode ser conferido: não foi usado, não venceu e não passou do limite de tentativas. */
    public boolean valeAinda(Instant agora, int limiteDeTentativas) {
        return usadaEm == null && agora.isBefore(expiraEm) && tentativas < limiteDeTentativas;
    }
}
