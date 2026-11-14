package br.com.cadeiralivre.api.agendamento;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.servico.Combo;
import br.com.cadeiralivre.api.servico.Servico;
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

/**
 * Um item do agendamento: um serviço OU um combo, com o profissional escolhido pelo cliente.
 * Nome, preço e duração ficam guardados como estavam na hora do agendamento.
 */
@Entity
@Table(name = "agendamento_item")
@Getter
@Setter
@NoArgsConstructor
public class AgendamentoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private Combo combo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(name = "duracao_minutos", nullable = false)
    private int duracaoMinutos;

    // posição do item no agendamento (0 = primeiro)
    @Column(nullable = false)
    private int ordem;

    /** Item de serviço: nome, preço e duração vêm do serviço. */
    public static AgendamentoItem doServico(Servico servico, Profissional profissional, int ordem) {
        AgendamentoItem item = new AgendamentoItem();
        item.setServico(servico);
        item.setProfissional(profissional);
        item.setNome(servico.getNome());
        item.setPreco(servico.getPreco());
        item.setDuracaoMinutos(servico.getDuracaoMinutos());
        item.setOrdem(ordem);
        return item;
    }

    /** Item de combo: o preço é o do combo e a duração é a soma dos serviços dele. */
    public static AgendamentoItem doCombo(Combo combo, Profissional profissional, int ordem) {
        AgendamentoItem item = new AgendamentoItem();
        item.setCombo(combo);
        item.setProfissional(profissional);
        item.setNome(combo.getNome());
        item.setPreco(combo.getPreco());
        item.setDuracaoMinutos(combo.duracaoMinutos());
        item.setOrdem(ordem);
        return item;
    }
}
