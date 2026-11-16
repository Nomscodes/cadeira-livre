package br.com.cadeiralivre.api.servico;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.profissional.Profissional;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Combo: pacote de 2 ou mais serviços com preço próprio (ex.: Corte + Barba), feito pelos profissionais escolhidos.
 * A duração é sempre a soma da duração dos serviços.
 */
@Entity
@Table(name = "combo")
@Getter
@Setter
@NoArgsConstructor
public class Combo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbearia_id", nullable = false)
    private Barbearia barbearia;

    @Column(nullable = false, length = 120)
    private String nome;

    // preço do pacote (normalmente menor que a soma dos serviços)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    // exclusão lógica: some do painel e do portal, mas continua nos agendamentos antigos
    @Column(nullable = false)
    private boolean ativo = true;

    @ManyToMany
    @JoinTable(name = "combo_servico",
            joinColumns = @JoinColumn(name = "combo_id"),
            inverseJoinColumns = @JoinColumn(name = "servico_id"))
    private Set<Servico> servicos = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "combo_profissional",
            joinColumns = @JoinColumn(name = "combo_id"),
            inverseJoinColumns = @JoinColumn(name = "profissional_id"))
    private Set<Profissional> profissionais = new HashSet<>();

    /** Tempo do combo: a soma do tempo de cada serviço. */
    public int duracaoMinutos() {
        return servicos.stream().mapToInt(Servico::getDuracaoMinutos).sum();
    }

    /** Quanto os serviços custariam separados (o front mostra "economize" com a diferença). */
    public BigDecimal somaDosServicos() {
        return servicos.stream().map(Servico::getPreco).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
