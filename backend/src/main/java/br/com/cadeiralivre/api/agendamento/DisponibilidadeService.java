package br.com.cadeiralivre.api.agendamento;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.dto.DisponibilidadeResponse;
import br.com.cadeiralivre.api.agendamento.dto.DisponibilidadeResponse.ProfissionalDisponivel;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.BloqueioHorario;
import br.com.cadeiralivre.api.horario.BloqueioHorarioRepository;
import br.com.cadeiralivre.api.horario.HorarioFuncionamento;
import br.com.cadeiralivre.api.horario.HorarioFuncionamentoRepository;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import lombok.RequiredArgsConstructor;

/**
 * Horários livres para o cliente agendar (RF13). Cada profissional tem a sua agenda:
 * um horário é livre quando o serviço inteiro cabe no funcionamento do dia (RF19),
 * não bate com bloqueio nem folga (RF20) e não bate com agendamento aprovado daquele profissional (RF21).
 */
@Service
@RequiredArgsConstructor
public class DisponibilidadeService {

    // os horários são oferecidos de 30 em 30 minutos
    private static final int INTERVALO_MINUTOS = 30;

    private final BarbeariaRepository barbeariaRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final HorarioFuncionamentoRepository horarioRepository;
    private final BloqueioHorarioRepository bloqueioRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DisponibilidadeResponse consultar(String slug, UUID servicoId, LocalDate data) {
        Barbearia barbearia = barbeariaRepository.findBySlug(slug)
                .filter(Barbearia::isPublico)
                .orElseThrow(() -> ApiException.naoEncontrado("Salão não encontrado."));
        Servico servico = servicoRepository.findByIdAndBarbeariaId(servicoId, barbearia.getId())
                .filter(Servico::isAtivo)
                .orElseThrow(() -> ApiException.invalido("Serviço inválido para este salão."));

        // os dados do dia são buscados uma vez só, e não uma vez para cada profissional
        List<Profissional> profissionais = profissionalRepository.buscarAtivosQueFazemOServico(barbearia.getId(), servico.getId());
        HorarioFuncionamento horario = horarioDoDia(barbearia.getId(), data);
        List<BloqueioHorario> bloqueios = bloqueioRepository.findAllByBarbeariaIdAndData(barbearia.getId(), data);
        List<Agendamento> aprovados = aprovadosDoDia(barbearia.getId(), data);

        List<ProfissionalDisponivel> agendas = new ArrayList<>();
        for (Profissional p : profissionais) {
            List<LocalTime> livres = horariosLivres(p, horario, data, servico.getDuracaoMinutos(), bloqueios, aprovados);
            agendas.add(new ProfissionalDisponivel(p.getId(), p.getNome(), livres));
        }
        return new DisponibilidadeResponse(data, servico.getId(), servico.getDuracaoMinutos(), agendas);
    }

    /**
     * Usado na hora de agendar (RF14), dentro da transação do agendamento: o horário pedido
     * precisa ser um dos horários livres do profissional, calculados com as mesmas regras da consulta acima.
     */
    public boolean estaLivre(Barbearia barbearia, Servico servico, Profissional profissional, LocalDate data, LocalTime hora) {
        HorarioFuncionamento horario = horarioDoDia(barbearia.getId(), data);
        List<BloqueioHorario> bloqueios = bloqueioRepository.findAllByBarbeariaIdAndData(barbearia.getId(), data);
        List<Agendamento> aprovados = aprovadosDoDia(barbearia.getId(), data);
        return horariosLivres(profissional, horario, data, servico.getDuracaoMinutos(), bloqueios, aprovados).contains(hora);
    }

    // vazio ou desativado = barbearia fechada nesse dia da semana
    private HorarioFuncionamento horarioDoDia(UUID barbeariaId, LocalDate data) {
        return horarioRepository.findByBarbeariaIdAndDiaSemana(barbeariaId, data.getDayOfWeek())
                .filter(HorarioFuncionamento::isAtivo)
                .orElse(null);
    }

    private List<Agendamento> aprovadosDoDia(UUID barbeariaId, LocalDate data) {
        return agendamentoRepository.findAllByBarbeariaIdAndDataAndStatus(barbeariaId, data, StatusAgendamento.APROVADO);
    }

    private List<LocalTime> horariosLivres(Profissional p, HorarioFuncionamento horario, LocalDate data, int duracao,
                                           List<BloqueioHorario> bloqueios, List<Agendamento> aprovados) {
        List<LocalTime> livres = new ArrayList<>();
        if (horario == null) return livres; // barbearia fechada nesse dia da semana

        // data + hora juntas: dá para comparar com o "agora" e a conta não dá a volta na meia-noite
        LocalDateTime agora = LocalDateTime.now(clock);
        LocalDateTime fechamento = data.atTime(horario.getHoraFechamento());
        // anda de 30 em 30 minutos enquanto o serviço inteiro couber antes do fechamento
        for (LocalDateTime momento = data.atTime(horario.getHoraAbertura());
                !momento.plusMinutes(duracao).isAfter(fechamento);
                momento = momento.plusMinutes(INTERVALO_MINUTOS)) {
            LocalTime inicio = momento.toLocalTime();
            LocalTime fim = inicio.plusMinutes(duracao);
            boolean jaPassou = !momento.isAfter(agora);
            if (!jaPassou && !bloqueado(p, inicio, fim, bloqueios) && !ocupado(p, inicio, fim, aprovados)) {
                livres.add(inicio);
            }
        }
        return livres;
    }

    // RF20: bloqueio da barbearia toda (sem profissional) ou folga deste profissional
    private boolean bloqueado(Profissional p, LocalTime inicio, LocalTime fim, List<BloqueioHorario> bloqueios) {
        for (BloqueioHorario b : bloqueios) {
            boolean valeParaEle = b.getProfissional() == null || b.getProfissional().getId().equals(p.getId());
            boolean diaInteiro = b.getHoraInicio() == null;
            if (valeParaEle && (diaInteiro || sobrepoe(inicio, fim, b.getHoraInicio(), b.getHoraFim()))) {
                return true;
            }
        }
        return false;
    }

    // RF21: já existe agendamento aprovado com este profissional nesse horário
    private boolean ocupado(Profissional p, LocalTime inicio, LocalTime fim, List<Agendamento> aprovados) {
        for (Agendamento a : aprovados) {
            boolean comEle = a.getProfissional() != null && a.getProfissional().getId().equals(p.getId());
            if (comEle && sobrepoe(inicio, fim, a.getHoraInicio(), a.getHoraFim())) {
                return true;
            }
        }
        return false;
    }

    // dois horários se sobrepõem quando cada um começa antes de o outro terminar
    private boolean sobrepoe(LocalTime inicioA, LocalTime fimA, LocalTime inicioB, LocalTime fimB) {
        return inicioA.isBefore(fimB) && inicioB.isBefore(fimA);
    }
}
