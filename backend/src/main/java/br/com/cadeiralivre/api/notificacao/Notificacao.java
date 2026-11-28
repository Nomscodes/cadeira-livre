package br.com.cadeiralivre.api.notificacao;

import java.time.Instant;
import java.util.UUID;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.usuario.Cliente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Aviso dentro do sistema (RF03). Fica guardado com a data em que foi lido (RF04). */
@Entity
@Table(name = "notificacao")
@Getter
@Setter
@NoArgsConstructor
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacao tipo;

    // null só no aviso do salão de um horário que já foi apagado (V21): aí o texto fica guardado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id")
    private Agendamento agendamento;

    // para quem é: o dono da barbearia ou o cliente (só um dos dois fica preenchido)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barbearia_id")
    private Barbearia barbearia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    // para um profissional da equipe (aviso do dono); nesse caso o barbearia_id também fica preenchido
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id")
    private Profissional profissional;

    // texto livre do aviso do dono ou do alerta do funcionário
    @Column(length = 500)
    private String texto;

    // nome de quem mandou o aviso ou o alerta
    @Column(length = 120)
    private String remetente;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    // null enquanto ninguém abriu
    @Column(name = "lida_em")
    private Instant lidaEm;
}
