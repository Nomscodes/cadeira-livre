package br.com.cadeiralivre.api.usuario;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Cliente final que agenda horários (RF01). */
@Entity
@Table(name = "cliente")
public class Cliente extends Usuario {

    public Cliente() {
        setPapel(Papel.CLIENTE);
    }
}
