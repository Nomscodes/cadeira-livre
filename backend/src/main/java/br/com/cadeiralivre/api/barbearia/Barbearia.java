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

/**
 * Raiz do tenant. Guarda a customização white-label (RF06–RF08): logo, cor primária, tema e nome fantasia,
 * e os dados do portal (cidade, categoria, sobre, redes, capa) e das configurações do salão.
 * As fotos da galeria ficam em {@link BarbeariaFoto}.
 */
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

    // telefone/WhatsApp de contato mostrado no portal
    @Column(length = 20)
    private String telefone;

    @Column(nullable = false)
    private boolean publico = false;

    // cidade e UF como o dono escreve ("Goiânia, GO")
    @Column(length = 120)
    private String cidade;

    // barbearia, salão de beleza ou unhas (filtro da busca de salões)
    @Column(nullable = false, length = 20)
    private String categoria = "barbearia";

    // um dos 14 temas de cor do front
    @Column(name = "tema_id", nullable = false, length = 40)
    private String temaId = "vermelho-escuro";

    @Column(length = 600)
    private String sobre;

    @Column(length = 255)
    private String redes;

    // imagem de capa do portal (data URL, igual à logo)
    @Column(name = "capa_url")
    private String capaUrl;

    // local (o cliente vai até o salão) ou domicilio (o profissional vai até o cliente)
    @Column(nullable = false, length = 10)
    private String atendimento = "local";

    // até quantas horas antes do horário o cliente pode cancelar
    @Column(name = "cancelamento_horas", nullable = false)
    private int cancelamentoHoras = 4;

    @Column(name = "funcionarios_editam_horario", nullable = false)
    private boolean funcionariosEditamHorario = false;

    // autonomo (o dono também atende) ou equipe (o dono só gerencia)
    @Column(nullable = false, length = 10)
    private String atuacao = "autonomo";

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();
}
