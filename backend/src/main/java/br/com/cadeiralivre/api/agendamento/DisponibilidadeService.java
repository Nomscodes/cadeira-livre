package br.com.cadeiralivre.api.agendamento;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.dto.CalendarioResponse;
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
import br.com.cadeiralivre.api.profissional.ProfissionalHorario;
import br.com.cadeiralivre.api.profissional.ProfissionalHorarioRepository;
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
    // descanso padrão de todos (igual ao front): ninguém atende das 12:00 às 13:00
    private static final int ALMOCO_INICIO = 12 * 60;
    private static final int ALMOCO_FIM = 13 * 60;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final BarbeariaRepository barbeariaRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final ProfissionalHorarioRepository profissionalHorarioRepository;
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

    // ---------- calendário do front: vários profissionais e a duração somada dos itens ----------

    /** Estado de cada dia do mês para o calendário do cliente. */
    @Transactional(readOnly = true)
    public List<CalendarioResponse.Dia> diasDoMes(String slug, List<UUID> profissionalIds, YearMonth mes, int duracao) {
        Barbearia barbearia = barbeariaPublicada(slug);
        List<Profissional> profissionais = profissionaisQueAtendem(barbearia, profissionalIds);
        Agenda agenda = agenda(barbearia, mes.atDay(1), mes.atEndOfMonth());
        LocalDate hoje = LocalDate.now(clock);

        List<CalendarioResponse.Dia> dias = new ArrayList<>();
        for (LocalDate dia = mes.atDay(1); !dia.isAfter(mes.atEndOfMonth()); dia = dia.plusDays(1)) {
            final LocalDate data = dia; // a lambda abaixo precisa de uma variável que não muda
            BloqueioHorario fechadoNoDia = agenda.salaoFechadoNoDia(data);
            String estado;
            if (data.isBefore(hoje)) estado = "passado";
            else if (fechadoNoDia != null) estado = "bloqueado";
            else if (agenda.horarioDoSalao(data) == null) estado = "fechado";
            else if (profissionais.isEmpty() || profissionais.stream().anyMatch(p -> agenda.janela(p, data) == null)) estado = "folga";
            else estado = horarios(agenda, profissionais, data, duracao).stream().anyMatch(CalendarioResponse.Horario::livre) ? "ok" : "lotado";
            dias.add(new CalendarioResponse.Dia(data, estado, "bloqueado".equals(estado) ? fechadoNoDia.getMotivo() : null));
        }
        return dias;
    }

    /** Horários de início do dia em que todos os profissionais escolhidos estão livres pela duração inteira. */
    @Transactional(readOnly = true)
    public List<CalendarioResponse.Horario> horariosDoDia(String slug, List<UUID> profissionalIds, LocalDate data, int duracao) {
        Barbearia barbearia = barbeariaPublicada(slug);
        List<Profissional> profissionais = profissionaisQueAtendem(barbearia, profissionalIds);
        return horarios(agenda(barbearia, data, data), profissionais, data, duracao);
    }

    /** Usado na hora de agendar: o horário pedido tem que estar livre para todos os profissionais dos itens. */
    public boolean estaLivre(Barbearia barbearia, List<Profissional> profissionais, LocalDate data, LocalTime hora, int duracao) {
        String pedido = hora.format(HORA);
        return horarios(agenda(barbearia, data, data), profissionais, data, duracao).stream()
                .anyMatch(h -> h.hora().equals(pedido) && h.livre());
    }

    private List<CalendarioResponse.Horario> horarios(Agenda agenda, List<Profissional> profissionais, LocalDate data, int duracao) {
        List<CalendarioResponse.Horario> horarios = new ArrayList<>();
        if (profissionais.isEmpty() || agenda.salaoFechadoNoDia(data) != null) return horarios;

        // a janela do dia é a parte em comum entre o salão e todos os profissionais escolhidos
        int abre = 0;
        int fecha = 24 * 60;
        for (Profissional p : profissionais) {
            int[] janela = agenda.janela(p, data);
            if (janela == null) return horarios; // alguém não trabalha nesse dia
            abre = Math.max(abre, janela[0]);
            fecha = Math.min(fecha, janela[1]);
        }

        LocalDate hoje = LocalDate.now(clock);
        int agora = LocalTime.now(clock).toSecondOfDay() / 60;
        for (int inicio = abre; inicio + duracao <= fecha; inicio += INTERVALO_MINUTOS) {
            if (inicio >= ALMOCO_INICIO && inicio < ALMOCO_FIM) continue; // ninguém começa no almoço
            int fim = inicio + duracao;
            boolean jaPassou = data.isBefore(hoje) || (data.equals(hoje) && inicio <= agora + 30);
            final int ini = inicio;
            boolean livre = !jaPassou && profissionais.stream().noneMatch(p -> agenda.ocupado(p, data, ini, fim));
            horarios.add(new CalendarioResponse.Horario(LocalTime.ofSecondOfDay(inicio * 60L).format(HORA), livre));
        }
        return horarios;
    }

    private Barbearia barbeariaPublicada(String slug) {
        return barbeariaRepository.findBySlug(slug)
                .filter(Barbearia::isPublico)
                .orElseThrow(() -> ApiException.naoEncontrado("Salão não encontrado."));
    }

    // só profissionais deste salão, ativos e que atendem clientes
    private List<Profissional> profissionaisQueAtendem(Barbearia barbearia, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Set<UUID> pedidos = new HashSet<>(ids);
        List<Profissional> encontrados = profissionalRepository.findAllByBarbeariaIdOrderByNome(barbearia.getId()).stream()
                .filter(p -> p.isAtivo() && p.isAtende() && pedidos.contains(p.getId()))
                .toList();
        if (encontrados.size() != pedidos.size()) {
            throw ApiException.invalido("Escolha profissionais deste salão que atendem clientes.");
        }
        return encontrados;
    }

    // busca de uma vez tudo o que o cálculo do período precisa (horários, bloqueios e agendamentos aprovados)
    private Agenda agenda(Barbearia barbearia, LocalDate inicio, LocalDate fim) {
        UUID id = barbearia.getId();
        Map<DayOfWeek, HorarioFuncionamento> salao = horarioRepository.findAllByBarbeariaId(id).stream()
                .filter(HorarioFuncionamento::isAtivo)
                .collect(Collectors.toMap(HorarioFuncionamento::getDiaSemana, h -> h));
        Map<UUID, Map<DayOfWeek, ProfissionalHorario>> profissionais = new HashMap<>();
        for (ProfissionalHorario h : profissionalHorarioRepository.findAllByProfissionalBarbeariaId(id)) {
            if (h.isAtivo()) {
                profissionais.computeIfAbsent(h.getProfissional().getId(), k -> new HashMap<>()).put(h.getDiaSemana(), h);
            }
        }
        return new Agenda(salao, profissionais,
                bloqueioRepository.findAllByBarbeariaIdAndDataBetweenOrderByData(id, inicio, fim),
                agendamentoRepository.findAllByBarbeariaIdAndDataBetweenAndStatus(id, inicio, fim, StatusAgendamento.APROVADO));
    }

    /** Os dados de um período, para responder "o salão abre?", "o profissional trabalha?" e "está ocupado?". */
    private record Agenda(Map<DayOfWeek, HorarioFuncionamento> salao,
                          Map<UUID, Map<DayOfWeek, ProfissionalHorario>> profissionais,
                          List<BloqueioHorario> bloqueios,
                          List<Agendamento> aprovados) {

        HorarioFuncionamento horarioDoSalao(LocalDate data) {
            return salao.get(data.getDayOfWeek());
        }

        // o dia inteiro fechado para o salão (feriado, manutenção)
        BloqueioHorario salaoFechadoNoDia(LocalDate data) {
            return bloqueios.stream()
                    .filter(b -> b.getData().equals(data) && b.getProfissional() == null && b.getHoraInicio() == null)
                    .findFirst().orElse(null);
        }

        /** Janela de trabalho do profissional no dia, em minutos [início, fim], ou null se ele não atende. */
        int[] janela(Profissional p, LocalDate data) {
            HorarioFuncionamento hs = horarioDoSalao(data);
            ProfissionalHorario hp = profissionais.getOrDefault(p.getId(), Map.of()).get(data.getDayOfWeek());
            boolean deFolga = bloqueios.stream().anyMatch(b -> b.getData().equals(data) && b.getHoraInicio() == null
                    && b.getProfissional() != null && b.getProfissional().getId().equals(p.getId()));
            if (hs == null || hp == null || deFolga || salaoFechadoNoDia(data) != null) return null;
            int inicio = Math.max(minutos(hs.getHoraAbertura()), minutos(hp.getHoraInicio()));
            int fim = Math.min(minutos(hs.getHoraFechamento()), minutos(hp.getHoraFim()));
            return inicio < fim ? new int[] {inicio, fim} : null;
        }

        /** O profissional está ocupado no intervalo? (almoço, bloqueio por horário ou agendamento aprovado) */
        boolean ocupado(Profissional p, LocalDate data, int inicio, int fim) {
            if (inicio < ALMOCO_FIM && fim > ALMOCO_INICIO) return true;
            for (BloqueioHorario b : bloqueios) {
                boolean valeParaEle = b.getProfissional() == null || b.getProfissional().getId().equals(p.getId());
                if (b.getData().equals(data) && b.getHoraInicio() != null && valeParaEle
                        && inicio < minutos(b.getHoraFim()) && fim > minutos(b.getHoraInicio())) return true;
            }
            for (Agendamento a : aprovados) {
                boolean comEle = a.getItens().stream().anyMatch(i -> i.getProfissional().getId().equals(p.getId()));
                if (a.getData().equals(data) && comEle
                        && inicio < minutos(a.getHoraFim()) && fim > minutos(a.getHoraInicio())) return true;
            }
            return false;
        }

        private static int minutos(LocalTime hora) {
            return hora.toSecondOfDay() / 60;
        }
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
