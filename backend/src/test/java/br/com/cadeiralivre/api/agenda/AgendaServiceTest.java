package br.com.cadeiralivre.api.agenda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.cadeiralivre.api.agenda.dto.AgendaResponse;
import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoItem;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalHorario;
import br.com.cadeiralivre.api.profissional.ProfissionalHorarioRepository;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.servico.Servico;

@ExtendWith(MockitoExtension.class)
class AgendaServiceTest {

    @Mock
    private ProfissionalRepository profissionalRepository;
    @Mock
    private ProfissionalHorarioRepository horarioRepository;
    @Mock
    private AgendamentoRepository agendamentoRepository;

    private AgendaService agendaService;

    // "Agora" fixo: sábado 14/11/2026 às 10:00 no horário de Brasília
    private final Instant agora = Instant.parse("2026-11-14T13:00:00Z");

    private final UUID contaDoNaoki = UUID.randomUUID();
    private Barbearia barbearia;
    private Profissional naoki;
    private Profissional gabriel;

    @BeforeEach
    void preparar() {
        agendaService = new AgendaService(profissionalRepository, horarioRepository, agendamentoRepository,
                Clock.fixed(agora, ZoneId.of("America/Sao_Paulo")));

        barbearia = new Barbearia();
        barbearia.setNomeFantasia("Barbearia Naoki Profissional");
        barbearia.setTemaId("verde-escuro");

        naoki = profissional("Naoki Barbeiro", "Naoki");
        gabriel = profissional("Gabriel Naoki", "Gabriel");
        Servico corte = new Servico();
        corte.setNome("Corte");
        Servico barba = new Servico();
        barba.setNome("Barba");
        naoki.getServicos().add(corte);
        naoki.getServicos().add(barba);
        naoki.setTelefone("(62) 99999-0001");

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                contaDoNaoki.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_FUNCIONARIO"))));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listaOsAgendamentosDoProfissionalComOsColegas() {
        when(profissionalRepository.findByUsuarioId(contaDoNaoki)).thenReturn(Optional.of(naoki));
        // combo: o Naoki faz o corte e o Gabriel a barba
        Agendamento combo = agendamento(LocalDate.of(2026, 11, 17), LocalTime.of(10, 0), StatusAgendamento.APROVADO);
        combo.adicionarItem(item("Corte", naoki, 30));
        combo.adicionarItem(item("Barba", gabriel, 20));
        when(agendamentoRepository.doProfissional(naoki.getId())).thenReturn(List.of(combo));

        AgendaResponse.Item item = agendaService.agendamentos().get(0);

        assertEquals("Corte + Barba", item.servicos());
        assertEquals(50, item.duracao());
        assertEquals("10:00", item.hora());
        assertEquals("Gabriel Naoki", item.cliente());
        assertEquals(StatusAgendamento.APROVADO, item.status());
        assertEquals(List.of("Gabriel"), item.colegas());
    }

    @Test
    void resumoContaHojeProximosEConcluidosDoMes() {
        when(profissionalRepository.findByUsuarioId(contaDoNaoki)).thenReturn(Optional.of(naoki));
        when(horarioRepository.findAllByProfissionalId(naoki.getId()))
                .thenReturn(ProfissionalHorario.semanaPadrao(naoki));
        Agendamento jaPassouHoje = comItem(agendamento(LocalDate.of(2026, 11, 14), LocalTime.of(9, 0), StatusAgendamento.APROVADO));
        Agendamento maisTardeHoje = comItem(agendamento(LocalDate.of(2026, 11, 14), LocalTime.of(15, 0), StatusAgendamento.APROVADO));
        Agendamento canceladoHoje = comItem(agendamento(LocalDate.of(2026, 11, 14), LocalTime.of(16, 0), StatusAgendamento.CANCELADO));
        Agendamento daquiA10Dias = comItem(agendamento(LocalDate.of(2026, 11, 24), LocalTime.of(10, 0), StatusAgendamento.APROVADO));
        Agendamento concluido = comItem(agendamento(LocalDate.of(2026, 11, 6), LocalTime.of(14, 0), StatusAgendamento.CONCLUIDO));
        Agendamento pendente = comItem(agendamento(LocalDate.of(2026, 11, 16), LocalTime.of(9, 0), StatusAgendamento.PENDENTE));
        when(agendamentoRepository.doProfissional(naoki.getId())).thenReturn(
                List.of(concluido, jaPassouHoje, maisTardeHoje, canceladoHoje, pendente, daquiA10Dias));

        AgendaResponse.Resumo r = agendaService.resumo();

        assertEquals("Naoki", r.profissional().nomeSocial());
        assertEquals("(62) 99999-0001", r.profissional().whatsapp());
        assertEquals(List.of("Barba", "Corte"), r.profissional().servicos());
        assertEquals(true, r.profissional().horarios().get("1").ativo()); // segunda
        assertEquals("verde-escuro", r.salao().temaId());
        assertEquals(2, r.indicadores().hoje()); // o cancelado não conta
        assertEquals(1, r.indicadores().proximos7()); // só o das 15h (o de 24/11 passa de 7 dias; pendente não conta)
        assertEquals(1, r.indicadores().concluidosMes());
        assertEquals("15:00", r.indicadores().proximo().hora());
    }

    @Test
    void resumoSemProximoAtendimento() {
        when(profissionalRepository.findByUsuarioId(contaDoNaoki)).thenReturn(Optional.of(naoki));
        when(agendamentoRepository.doProfissional(naoki.getId())).thenReturn(List.of());

        AgendaResponse.Resumo r = agendaService.resumo();

        assertNull(r.indicadores().proximo());
        assertEquals(0, r.indicadores().hoje());
    }

    @Test
    void quemNaoEhFuncionarioNaoAbreAAgenda() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));

        ApiException erro = assertThrows(ApiException.class, () -> agendaService.agendamentos());

        assertEquals(403, erro.getStatus().value());
        verifyNoInteractions(agendamentoRepository);
    }

    @Test
    void funcionarioRemovidoDaEquipeNaoAbreAAgenda() {
        naoki.setAtivo(false);
        when(profissionalRepository.findByUsuarioId(contaDoNaoki)).thenReturn(Optional.of(naoki));

        ApiException erro = assertThrows(ApiException.class, () -> agendaService.resumo());

        assertEquals(403, erro.getStatus().value());
    }

    // ---------- métodos de apoio ----------

    private Profissional profissional(String nome, String nomeSocial) {
        Profissional p = new Profissional();
        p.setId(UUID.randomUUID());
        p.setNome(nome);
        p.setNomeSocial(nomeSocial);
        p.setBarbearia(barbearia);
        return p;
    }

    private Agendamento agendamento(LocalDate data, LocalTime hora, StatusAgendamento status) {
        Agendamento a = new Agendamento();
        a.setId(UUID.randomUUID());
        a.setBarbearia(barbearia);
        a.setClienteNome("Gabriel Naoki");
        a.setData(data);
        a.setHoraInicio(hora);
        a.setHoraFim(hora.plusMinutes(30));
        a.setStatus(status);
        return a;
    }

    private Agendamento comItem(Agendamento a) {
        a.adicionarItem(item("Corte", naoki, 30));
        return a;
    }

    private AgendamentoItem item(String nome, Profissional p, int minutos) {
        AgendamentoItem i = new AgendamentoItem();
        i.setNome(nome);
        i.setProfissional(p);
        i.setPreco(new BigDecimal("40.00"));
        i.setDuracaoMinutos(minutos);
        return i;
    }
}
