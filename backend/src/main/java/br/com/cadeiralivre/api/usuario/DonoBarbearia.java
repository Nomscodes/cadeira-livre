package br.com.cadeiralivre.api.usuario;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Dono/gerente da barbearia (RF02). É quem acessa o painel. */
@Entity
@Table(name = "dono_barbearia")
@Getter
@Setter
public class DonoBarbearia extends Usuario {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbearia_id", nullable = false)
    private Barbearia barbearia;

    public DonoBarbearia() {
        setPapel(Papel.DONO);
    }
}
