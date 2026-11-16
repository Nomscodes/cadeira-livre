package br.com.cadeiralivre.api.usuario;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Profissional da equipe com login próprio. A conta é criada com o código de convite e fica ligada
 * ao profissional (profissional.usuario_id). Vê a própria agenda, não o painel do dono.
 */
@Entity
@Table(name = "funcionario")
@Getter
@Setter
public class Funcionario extends Usuario {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbearia_id", nullable = false)
    private Barbearia barbearia;

    public Funcionario() {
        setPapel(Papel.FUNCIONARIO);
    }
}
