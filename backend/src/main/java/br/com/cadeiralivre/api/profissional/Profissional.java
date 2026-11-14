package br.com.cadeiralivre.api.profissional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.common.cripto.CriptografiaConverter;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.usuario.Usuario;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Profissional da barbearia. Cada um tem a lista dos serviços que sabe fazer.
 * O dono também é um profissional (administrador); se ele só gerencia o salão, fica com atende = false.
 */
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

    // conta que entra no sistema como este profissional (o dono ou um funcionário); null = sem login
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;

    // nome mostrado aos clientes no portal
    @Column(name = "nome_social", length = 60)
    private String nomeSocial;

    // false = não atende clientes: não aparece no portal nem na disponibilidade
    @Column(nullable = false)
    private boolean atende = true;

    @Column(nullable = false)
    private boolean administrador = false;

    // os avisos de agendamento vão também para o WhatsApp dele
    @Column(nullable = false)
    private boolean encaminhar = false;

    // código que o funcionário usa para criar o próprio login; null depois de usado
    @Column(name = "codigo_convite", unique = true, length = 8)
    private String codigoConvite;

    @ManyToMany
    @JoinTable(name = "profissional_servico",
            joinColumns = @JoinColumn(name = "profissional_id"),
            inverseJoinColumns = @JoinColumn(name = "servico_id"))
    private Set<Servico> servicos = new HashSet<>();
}
