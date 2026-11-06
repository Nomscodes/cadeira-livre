package br.com.cadeiralivre.api.horario;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.dto.HorarioDiaRequest;
import br.com.cadeiralivre.api.horario.dto.HorarioDiaResponse;
import br.com.cadeiralivre.api.horario.dto.HorarioSemanaRequest;
import br.com.cadeiralivre.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;

/** Horário de funcionamento da barbearia por dia da semana (RF19). Restrito à barbearia do token. */
@Service
@RequiredArgsConstructor
public class HorarioService {

    private final HorarioFuncionamentoRepository horarioRepository;
    private final BarbeariaRepository barbeariaRepository;

    /** Sempre devolve os 7 dias, de segunda a domingo. */
    @Transactional(readOnly = true)
    public List<HorarioDiaResponse> listar() {
        Map<DayOfWeek, HorarioFuncionamento> salvos = horariosPorDia(TenantContext.require());
        return Arrays.stream(DayOfWeek.values())
                .map(dia -> salvos.containsKey(dia) ? HorarioDiaResponse.de(salvos.get(dia)) : HorarioDiaResponse.fechado(dia))
                .toList();
    }

    @Transactional
    public List<HorarioDiaResponse> salvar(HorarioSemanaRequest req) {
        UUID barbeariaId = TenantContext.require();
        validar(req);

        Map<DayOfWeek, HorarioFuncionamento> salvos = horariosPorDia(barbeariaId);
        for (HorarioDiaRequest dia : req.dias()) {
            // cada dia tem uma linha só no banco: se já existe, atualiza; se não, cria
            HorarioFuncionamento h = salvos.get(dia.diaSemana());
            if (h == null) {
                h = new HorarioFuncionamento();
                h.setBarbearia(barbeariaRepository.getReferenceById(barbeariaId));
                h.setDiaSemana(dia.diaSemana());
            }
            h.setAtivo(dia.aberto());
            h.setHoraAbertura(dia.abertura());
            h.setHoraFechamento(dia.fechamento());
            horarioRepository.save(h);
        }
        return listar();
    }

    private void validar(HorarioSemanaRequest req) {
        Set<DayOfWeek> diasVistos = new HashSet<>();
        for (HorarioDiaRequest dia : req.dias()) {
            if (!diasVistos.add(dia.diaSemana())) {
                throw ApiException.invalido("Dia repetido na lista: " + nome(dia.diaSemana()) + ".");
            }
            if (!dia.abertura().isBefore(dia.fechamento())) {
                throw ApiException.invalido("Horário inválido em " + nome(dia.diaSemana())
                        + ": a abertura precisa ser antes do fechamento.");
            }
        }
    }

    private Map<DayOfWeek, HorarioFuncionamento> horariosPorDia(UUID barbeariaId) {
        return horarioRepository.findAllByBarbeariaId(barbeariaId).stream()
                .collect(Collectors.toMap(HorarioFuncionamento::getDiaSemana, h -> h));
    }

    // MONDAY -> "segunda-feira", para a mensagem de erro ficar em português
    private String nome(DayOfWeek dia) {
        return dia.getDisplayName(TextStyle.FULL, Locale.of("pt", "BR"));
    }
}
