package br.com.cadeiralivre.api.barbearia.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.horario.dto.HorariosDoPainelResponse;
import br.com.cadeiralivre.api.servico.dto.ComboResponse;
import br.com.cadeiralivre.api.servico.dto.ServicoResponse;

/**
 * Portal público da barbearia (RF09/RF12), no formato que o front usa. Se o portal estiver oculto,
 * só slug, nome, temaId e publico = false são preenchidos.
 * Os profissionais, serviços e combos mostram só quem atende clientes.
 */
public record PortalPublicoResponse(
        UUID id,
        String slug,
        String nome,
        String temaId,
        boolean publico,
        String logo,
        String capa,
        List<String> galeria,
        String sobre,
        String whatsapp,
        String cidade,
        String endereco,
        String categoria,
        String redes,
        Map<String, HorariosDoPainelResponse.Dia> horarios,
        String atendimento,
        int cancelamentoHoras,
        List<ServicoResponse> servicos,
        List<ComboResponse> combos,
        List<ProfissionalDoPortal> profissionais) {

    /** Profissional como o cliente vê: o nome social, o que ele faz e o horário de trabalho. */
    public record ProfissionalDoPortal(UUID id, String nome, List<UUID> servicoIds,
                                       Map<String, HorariosDoPainelResponse.Dia> horarios) {
    }

    /** Cada salão no resultado da busca (tela "Encontre seu salão"). */
    public record SalaoDaBusca(UUID id, String slug, String nome, String cidade, String categoria, String temaId,
                               String logo) {

        public static SalaoDaBusca de(Barbearia b) {
            return new SalaoDaBusca(b.getId(), b.getSlug(), b.getNomeFantasia(), b.getCidade(), b.getCategoria(),
                    b.getTemaId(), b.getLogoUrl());
        }
    }

    public static PortalPublicoResponse oculto(Barbearia b) {
        return new PortalPublicoResponse(null, b.getSlug(), b.getNomeFantasia(), b.getTemaId(), false, null, null,
                List.of(), null, null, null, null, null, null, Map.of(), null, 0, List.of(), List.of(), List.of());
    }

    public static PortalPublicoResponse de(Barbearia b, List<String> galeria, Map<String, HorariosDoPainelResponse.Dia> horarios,
                                           List<ServicoResponse> servicos, List<ComboResponse> combos,
                                           List<ProfissionalDoPortal> profissionais) {
        return new PortalPublicoResponse(b.getId(), b.getSlug(), b.getNomeFantasia(), b.getTemaId(), true, b.getLogoUrl(),
                b.getCapaUrl(), galeria, b.getSobre(), b.getTelefone(), b.getCidade(), b.getEndereco(), b.getCategoria(),
                b.getRedes(), horarios, b.getAtendimento(), b.getCancelamentoHoras(), servicos, combos, profissionais);
    }
}
