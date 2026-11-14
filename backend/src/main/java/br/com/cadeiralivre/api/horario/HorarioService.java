package br.com.cadeiralivre.api.horario;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.dto.HorariosDoPainelResponse;
import br.com.cadeiralivre.api.horario.dto.SemanaRequest;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalHorario;
import br.com.cadeiralivre.api.profissional.ProfissionalHorarioRepository;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;

/**
 * Horário de funcionamento do salão (RF19) e horário de trabalho de cada profissional, por dia da semana.
 * Restrito à barbearia do token. Depois de salvar, devolve os avisos de conflito entre os dois.
 */
@Service
@RequiredArgsConstructor
public class HorarioService {

    // no front, "0" é domingo; os nomes são usados nas mensagens ("na segunda", "na terça"...)
    private static final String[] NOMES_DIA = {"domingo", "segunda", "terça", "quarta", "quinta", "sexta", "sábado"};
    private static final LocalTime INICIO_PADRAO = LocalTime.of(9, 0);
    private static final LocalTime FIM_PADRAO = LocalTime.of(18, 0);

    private final HorarioFuncionamentoRepository horarioRepository;
    private final ProfissionalHorarioRepository profissionalHorarioRepository;
    private final ProfissionalRepository profissionalRepository;
    private final BarbeariaRepository barbeariaRepository;

    /** Funcionamento do salão, horário de quem atende e os avisos de conflito. */
    @Transactional(readOnly = true)
    public HorariosDoPainelResponse listar() {
        UUID barbeariaId = TenantContext.require();
        Map<String, HorariosDoPainelResponse.Dia> salao =
                HorariosDoPainelResponse.semanaDoSalao(horarioRepository.findAllByBarbeariaId(barbeariaId));
        Map<UUID, List<ProfissionalHorario>> porProfissional = horariosDosProfissionais(barbeariaId);

        List<HorariosDoPainelResponse.ProfissionalComHorario> profissionais = queAtendem(barbeariaId).stream()
                .map(p -> new HorariosDoPainelResponse.ProfissionalComHorario(p.getId(), p.getNome(), p.isAdministrador(),
                        HorariosDoPainelResponse.semanaDoProfissional(porProfissional.getOrDefault(p.getId(), List.of()))))
                .toList();
        return new HorariosDoPainelResponse(new HorariosDoPainelResponse.Salao(salao), profissionais, avisos(barbeariaId));
    }

    /** Salva o funcionamento do salão (os 7 dias). O salão precisa abrir em pelo menos um dia. */
    @Transactional
    public List<String> salvarDoSalao(SemanaRequest req) {
        UUID barbeariaId = TenantContext.require();
        Map<DayOfWeek, SemanaRequest.Dia> semana = validar(req, true);
        Map<DayOfWeek, HorarioFuncionamento> salvos = horarioRepository.findAllByBarbeariaId(barbeariaId).stream()
                .collect(Collectors.toMap(HorarioFuncionamento::getDiaSemana, Function.identity()));

        semana.forEach((dia, novo) -> {
            // cada dia tem uma linha só no banco: se já existe, atualiza; se não, cria
            HorarioFuncionamento h = salvos.get(dia);
            if (h == null) {
                h = new HorarioFuncionamento();
                h.setBarbearia(barbeariaRepository.getReferenceById(barbeariaId));
                h.setDiaSemana(dia);
            }
            h.setAtivo(novo.ativo());
            h.setHoraAbertura(novo.inicio());
            h.setHoraFechamento(novo.fim());
            horarioRepository.save(h);
        });
        return avisos(barbeariaId);
    }

    /** Salva o horário de trabalho de um profissional do salão (pode ficar sem nenhum dia). */
    @Transactional
    public List<String> salvarDoProfissional(UUID profissionalId, SemanaRequest req) {
        UUID barbeariaId = TenantContext.require();
        Profissional p = profissionalRepository.findByIdAndBarbeariaId(profissionalId, barbeariaId)
                .filter(Profissional::isAtivo)
                .orElseThrow(() -> ApiException.naoEncontrado("Profissional não encontrado."));
        Map<DayOfWeek, SemanaRequest.Dia> semana = validar(req, false);
        Map<DayOfWeek, ProfissionalHorario> salvos = profissionalHorarioRepository.findAllByProfissionalId(p.getId()).stream()
                .collect(Collectors.toMap(ProfissionalHorario::getDiaSemana, Function.identity()));

        semana.forEach((dia, novo) -> {
            ProfissionalHorario h = salvos.getOrDefault(dia, new ProfissionalHorario(p, dia, null, null, true));
            h.setAtivo(novo.ativo());
            h.setHoraInicio(novo.inicio());
            h.setHoraFim(novo.fim());
            profissionalHorarioRepository.save(h);
        });
        return avisos(barbeariaId);
    }

    /**
     * Confere os 7 dias. Dia ativo precisa ter início antes do fim; dia de folga sem horas válidas
     * fica com 09:00–18:00 guardado. Devolve a semana já com ativo/início/fim preenchidos.
     */
    private Map<DayOfWeek, SemanaRequest.Dia> validar(SemanaRequest req, boolean exigirUmDia) {
        Map<DayOfWeek, SemanaRequest.Dia> semana = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            SemanaRequest.Dia d = req.horarios().get(String.valueOf(i));
            if (d == null) throw ApiException.invalido("Horários incompletos: envie os 7 dias da semana.");
            boolean ativo = Boolean.TRUE.equals(d.ativo());
            boolean horasValidas = d.inicio() != null && d.fim() != null && d.inicio().isBefore(d.fim());
            if (ativo && !horasValidas) {
                throw ApiException.invalido("Na " + NOMES_DIA[i] + ", o término precisa ser depois do início.");
            }
            semana.put(diaDaChave(i), horasValidas
                    ? new SemanaRequest.Dia(ativo, d.inicio(), d.fim())
                    : new SemanaRequest.Dia(false, INICIO_PADRAO, FIM_PADRAO));
        }
        if (exigirUmDia && semana.values().stream().noneMatch(SemanaRequest.Dia::ativo)) {
            throw ApiException.invalido("O salão precisa abrir em pelo menos um dia da semana.");
        }
        return semana;
    }

    /** Avisa quando o profissional trabalha num dia em que o salão está fechado ou passa do horário do salão. */
    private List<String> avisos(UUID barbeariaId) {
        Map<DayOfWeek, HorarioFuncionamento> salao = horarioRepository.findAllByBarbeariaId(barbeariaId).stream()
                .collect(Collectors.toMap(HorarioFuncionamento::getDiaSemana, Function.identity()));
        Map<UUID, List<ProfissionalHorario>> porProfissional = horariosDosProfissionais(barbeariaId);

        List<String> avisos = new ArrayList<>();
        for (Profissional p : queAtendem(barbeariaId)) {
            Map<DayOfWeek, ProfissionalHorario> dele = porProfissional.getOrDefault(p.getId(), List.of()).stream()
                    .collect(Collectors.toMap(ProfissionalHorario::getDiaSemana, Function.identity()));
            for (int i = 0; i < 7; i++) {
                ProfissionalHorario hp = dele.get(diaDaChave(i));
                if (hp == null || !hp.isAtivo()) continue;
                HorarioFuncionamento hs = salao.get(diaDaChave(i));
                if (hs == null || !hs.isAtivo()) {
                    avisos.add(p.getNome() + " trabalha na " + NOMES_DIA[i]
                            + ", mas o salão fica fechado nesse dia. Esse dia não terá horários.");
                } else if (hp.getHoraInicio().isBefore(hs.getHoraAbertura()) || hp.getHoraFim().isAfter(hs.getHoraFechamento())) {
                    avisos.add(p.getNome() + " na " + NOMES_DIA[i] + ": o horário passa do funcionamento do salão ("
                            + hs.getHoraAbertura() + " às " + hs.getHoraFechamento()
                            + "). O atendimento fica limitado ao horário do salão.");
                }
            }
        }
        return avisos;
    }

    private List<Profissional> queAtendem(UUID barbeariaId) {
        return profissionalRepository.findAllByBarbeariaIdOrderByNome(barbeariaId).stream()
                .filter(p -> p.isAtivo() && p.isAtende()).toList();
    }

    private Map<UUID, List<ProfissionalHorario>> horariosDosProfissionais(UUID barbeariaId) {
        return profissionalHorarioRepository.findAllByProfissionalBarbeariaId(barbeariaId).stream()
                .collect(Collectors.groupingBy(h -> h.getProfissional().getId()));
    }

    // "0" = domingo, "1" = segunda ... "6" = sábado
    private DayOfWeek diaDaChave(int chave) {
        return chave == 0 ? DayOfWeek.SUNDAY : DayOfWeek.of(chave);
    }
}
