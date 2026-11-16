package br.com.cadeiralivre.api.agendamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.common.cripto.CriptografiaConverter;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.usuario.Cliente;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Solicitação de horário (RF14/RF15). Guarda nome/telefone do cliente mesmo sem conta (cliente_id é opcional). */
@Entity
@Table(name = "agendamento")
@Getter
@Setter
@NoArgsConstructor
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbearia_id", nullable = false)
    private Barbearia barbearia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    // profissional que vai atender: a agenda e a regra do RF21 são por profissional
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "cliente_nome", nullable = false, length = 120)
    private String clienteNome;

    // gravado criptografado no banco (RNF03)
    @Convert(converter = CriptografiaConverter.class)
    @Column(name = "cliente_telefone", nullable = false, length = 512)
    private String clienteTelefone;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAgendamento status = StatusAgendamento.PENDENTE;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    // soma dos preços dos itens
    @Column(precision = 10, scale = 2)
    private BigDecimal valor;

    // os serviços/combos escolhidos, cada um com o seu profissional; o primeiro item é o "principal"
    @OneToMany(mappedBy = "agendamento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem")
    private List<AgendamentoItem> itens = new ArrayList<>();

    /** Liga o item a este agendamento (os dois lados da relação). */
    public void adicionarItem(AgendamentoItem item) {
        item.setAgendamento(this);
        itens.add(item);
    }

    /** Duração total em minutos: a soma dos itens. */
    public int duracaoMinutos() {
        return itens.stream().mapToInt(AgendamentoItem::getDuracaoMinutos).sum();
    }

    /** Valor total: a soma dos preços dos itens. */
    public BigDecimal somaDosItens() {
        return itens.stream().map(AgendamentoItem::getPreco).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
