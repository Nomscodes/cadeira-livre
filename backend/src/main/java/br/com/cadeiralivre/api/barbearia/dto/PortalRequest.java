package br.com.cadeiralivre.api.barbearia.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados do portal enviados pelo dono na tela "Meu portal" (RF06–RF09), com os nomes que o front usa.
 * As imagens chegam em base64, do jeito que o front envia (ex.: "data:image/png;base64,iVBOR...").
 * A publicação do portal tem rota própria.
 */
public record PortalRequest(
        @NotBlank(message = "informe o nome do salão") @Size(max = 120) String nome,

        // um dos 14 temas de cor do front (7 cores, versão escura ou pastel). Se não vier, fica o atual
        @Pattern(regexp = PortalRequest.TEMAS, message = "escolha um dos temas de cor") String temaId,

        @Size(max = 600) String sobre,
        @Size(max = 20) String whatsapp,

        // se não vier, fica a atual
        @Pattern(regexp = "barbearia|salão de beleza|unhas", message = "escolha barbearia, salão de beleza ou unhas")
        String categoria,

        @Size(max = 120) String cidade,
        @Size(max = 255) String endereco,
        @Size(max = 255) String redes,

        // null = sem logo
        @Size(max = 1_400_000, message = "a logo pode ter no máximo 1 MB")
        @Pattern(regexp = PortalRequest.IMAGEM, message = "envie a logo como imagem PNG, JPG ou WEBP")
        String logo,

        // null = sem capa
        @Size(max = 2_800_000, message = "a capa pode ter no máximo 2 MB")
        @Pattern(regexp = PortalRequest.IMAGEM, message = "envie a capa como imagem PNG, JPG ou WEBP")
        String capa,

        // a galeria inteira, na ordem em que aparece; null = não mexe nas fotos
        @Size(max = 20, message = "a galeria pode ter no máximo 20 fotos")
        List<@Size(max = 1_400_000, message = "cada foto da galeria pode ter no máximo 1 MB")
             @Pattern(regexp = PortalRequest.IMAGEM, message = "envie as fotos como imagem PNG, JPG ou WEBP")
             String> galeria,

        // profissionais que administram o salão; lista vazia ou null = não mexe
        List<UUID> adminIds) {

    static final String IMAGEM = "^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}$";
    static final String TEMAS = "(vermelho|laranja|amarelo|verde|azul|anil|violeta)-(escuro|pastel)";
}
