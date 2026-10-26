package br.com.cadeiralivre.api.barbearia;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Raiz do tenant. Guarda a customização white-label (RF06–RF08): logo, cor primária e nome fantasia. */
@Entity
@Table(name = "barbearia")
@Getter
@Setter
@NoArgsConstructor
public class Barbearia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "nome_fantasia", nullable = false, length = 120)
    private String nomeFantasia;

    @Column(name = "razao_social", nullable = false, length = 160)
    private String razaoSocial;

    @Column(nullable = false, length = 14)
    private String documento;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "cor_primaria", nullable = false, length = 7)
    private String corPrimaria = "#9B1C2E";

    @Column(length = 255)
    private String endereco;

    @Column(length = 20)
    private String telefone;

    @Column(nullable = false)
    private boolean publico = false;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
