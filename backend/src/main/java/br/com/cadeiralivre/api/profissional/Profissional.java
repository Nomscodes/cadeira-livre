package br.com.cadeiralivre.api.profissional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.common.cripto.CriptografiaConverter;
import br.com.cadeiralivre.api.servico.Servico;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/** Barbeiro que atende na barbearia. Cada um tem a lista dos serviços que sabe fazer. */
@Entity
@Table(name = "profissional")
@Getter
@Setter
@NoArgsConstructor
public class Profissional {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbearia_id", nullable = false)
    private Barbearia barbearia;

    @Column(nullable = false, length = 120)
    private String nome;

    // gravado criptografado no banco (RNF03)
    @Convert(converter = CriptografiaConverter.class)
    @Column(length = 512)
    private String telefone;

    @Column(nullable = false)
    private boolean ativo = true;

    @ManyToMany
    @JoinTable(name = "profissional_servico",
            joinColumns = @JoinColumn(name = "profissional_id"),
            inverseJoinColumns = @JoinColumn(name = "servico_id"))
    private Set<Servico> servicos = new HashSet<>();
}
