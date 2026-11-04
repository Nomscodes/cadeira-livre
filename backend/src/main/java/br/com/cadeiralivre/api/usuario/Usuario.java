package br.com.cadeiralivre.api.usuario;

import java.time.Instant;
import java.util.UUID;

import br.com.cadeiralivre.api.common.cripto.CriptografiaConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Classe abstrata do diagrama de classes. Herança JOINED: tabela usuario + tabela da subclasse. */
@Entity
@Table(name = "usuario")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
public abstract class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nome;

    // gravado criptografado no banco (RNF03)
    @Convert(converter = CriptografiaConverter.class)
    @Column(nullable = false, length = 512)
    private String email;

    // hash do e-mail: usado no login e para garantir e-mail único (RF03)
    @Column(name = "email_hash", nullable = false, unique = true, length = 64)
    private String emailHash;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    // gravado criptografado no banco (RNF03)
    @Convert(converter = CriptografiaConverter.class)
    @Column(length = 512)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Papel papel;

    // quando o usuário aceitou os termos de uso e a política de privacidade (RNF02)
    @Column(name = "aceite_termos_em", nullable = false)
    private Instant aceiteTermosEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
