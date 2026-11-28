package br.com.cadeiralivre.api.horario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoItem;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.dto.BloqueioRequest;
import br.com.cadeiralivre.api.horario.dto.BloqueioResponse;
import br.com.cadeiralivre.api.notificacao.NotificacaoService;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class BloqueioServiceTest {

    @Mock
    private BloqueioHorarioRepository bloqueioRepository;

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private NotificacaoService notificacaoService;

    private BloqueioService bloqueioService;

    // "Agora" fixo nos testes: 10/11/2026 às 09:00 no horário de Brasília
    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private final LocalDate hoje = LocalDate.of(2026, 11, 10);
    private final LocalDate feriado = LocalDate.of(2026, 11, 20);
    private final LocalTime catorzeHoras = LocalTime.of(14, 0);
    private final LocalTime dezesseisHoras = LocalTime.of(16, 0);

    private final UUID minhaBarbearia = UUID.randomUUID();

    private Barbearia barbearia;

    @BeforeEach
    void preparar() {
        // o Clock não é mock, por isso o service é criado na mão
        bloqueioService = new BloqueioService(bloqueioRepository, profissionalRepository, barbeariaRepository,
                agendamentoRepository, notificacaoService, relogio);

        // simula o que o JwtAuthenticationFilter faz com o token do dono
        TenantContext.set(minhaBarbearia);

        barbearia = new Barbearia();
        barbearia.setId(minhaBarbearia);
        barbearia.setNomeFantasia("Barbearia Naoki");
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    // ---------- listagem ----------

    @Test
    void listarBuscaSoOsBloqueiosDeHojeEmDianteDaBarbeariaDoToken() {
        bloqueioService.listar();

        verify(bloqueioRepository)
                .findAllByBarbeariaIdAndDataGreaterThanEqualOrderByDataAscHoraInicioAsc(minhaBarbearia, hoje);
    }

    // ---------- criação ----------

    @Test
    void feriadoSemHorasBloqueiaODiaInteiroDaBarbeariaToda() {
        when(barbeariaRepository.getReferenceById(minhaBarbearia)).thenReturn(barbearia);
        when(bloqueioRepository.save(any(BloqueioHorario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        BloqueioResponse criado = bloqueioService.criar(
                new BloqueioRequest(feriado, null, null, "  Feriado  ", null));

        ArgumentCaptor<BloqueioHorario> salvo = ArgumentCaptor.forClass(BloqueioHorario.class);
        verify(bloqueioRepository).save(salvo.capture());
        assertEquals(minhaBarbearia, salvo.getValue().getBarbearia().getId());
        assertTrue(criado.diaInteiro());
        assertEquals("Feriado", criado.motivo());
        assertNull(criado.profissionalId());
    }

    @Test
    void folgaComProfissionalValeSoParaEle() {
        Profissional rafael = new Profissional();
        rafael.setId(UUID.randomUUID());
        rafael.setNome("Rafael Lima");
        when(barbeariaRepository.getReferenceById(minhaBarbearia)).thenReturn(barbearia);
        when(profissionalRepository.findByIdAndBarbeariaId(rafael.getId(), minhaBarbearia)).thenReturn(Optional.of(rafael));
        when(bloqueioRepository.save(any(BloqueioHorario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        BloqueioResponse criado = bloqueioService.criar(
                new BloqueioRequest(feriado, catorzeHoras, dezesseisHoras, "Consulta", rafael.getId()));

        assertFalse(criado.diaInteiro());
        assertEquals(rafael.getId(), criado.profissionalId());
        assertEquals("Rafael Lima", criado.profissionalNome());
    }

    @Test
    void profissionalDeOutraBarbeariaDevolve422ENaoSalva() {
        UUID profissionalDeOutraBarbearia = UUID.randomUUID();
        when(profissionalRepository.findByIdAndBarbeariaId(profissionalDeOutraBarbearia, minhaBarbearia))
                .thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class, () -> bloqueioService.criar(
                new BloqueioRequest(feriado, null, null, null, profissionalDeOutraBarbearia)));

        assertEquals(422, erro.getStatus().value());
        verify(bloqueioRepository, never()).save(any());
    }

    @Test
    void dataQueJaPassouDevolve422() {
        LocalDate ontem = LocalDate.of(2026, 11, 9);

        ApiException erro = assertThrows(ApiException.class,
                () -> bloqueioService.criar(new BloqueioRequest(ontem, null, null, null, null)));

        assertEquals(422, erro.getStatus().value());
        verify(bloqueioRepository, never()).save(any());
    }

    @Test
    void soUmaDasHorasPreenchidaDevolve422() {
        ApiException erro = assertThrows(ApiException.class,
                () -> bloqueioService.criar(new BloqueioRequest(feriado, catorzeHoras, null, null, null)));

        assertEquals(422, erro.getStatus().value());
        verify(bloqueioRepository, never()).save(any());
    }

    @Test
    void horaDeInicioDepoisDaHoraDeFimDevolve422() {
        ApiException erro = assertThrows(ApiException.class, () -> bloqueioService.criar(
                new BloqueioRequest(feriado, dezesseisHoras, catorzeHoras, null, null)));

        assertEquals(422, erro.getStatus().value());
        verify(bloqueioRepository, never()).save(any());
    }

    // ---------- exclusão ----------

    @Test
    void excluirApagaOBloqueioDaBarbeariaDoToken() {
        BloqueioHorario bloqueio = new BloqueioHorario();
        bloqueio.setId(UUID.randomUUID());
        when(bloqueioRepository.findByIdAndBarbeariaId(bloqueio.getId(), minhaBarbearia)).thenReturn(Optional.of(bloqueio));

        bloqueioService.excluir(bloqueio.getId());

        verify(bloqueioRepository).delete(bloqueio);
    }

    @Test
    void excluirBloqueioDeOutraBarbeariaDevolve404ENaoApaga() {
        UUID bloqueioDeOutraBarbearia = UUID.randomUUID();
        when(bloqueioRepository.findByIdAndBarbeariaId(bloqueioDeOutraBarbearia, minhaBarbearia))
                .thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class, () -> bloqueioService.excluir(bloqueioDeOutraBarbearia));

        assertEquals(404, erro.getStatus().value());
        verify(bloqueioRepository, never()).delete(any());
    }

    @Test
    void semBarbeariaNoTokenDevolve403() {
        TenantContext.clear();

        ApiException erro = assertThrows(ApiException.class, () -> bloqueioService.listar());

        assertEquals(403, erro.getStatus().value());
    }

    // ---------- calendário do painel: dia inteiro por data ----------

    @Test
    void bloqueiosDoMesTrazSoODiaInteiroDoSalao() {
        Profissional gabriel = profissionalDoGabriel();
        BloqueioHorario feriadoDoSalao = diaInteiro(feriado, null, "Feriado");
        BloqueioHorario folga = diaInteiro(feriado, gabriel, "Folga");
        BloqueioHorario soDeTarde = diaInteiro(feriado, null, "Reforma");
        soDeTarde.setHoraInicio(catorzeHoras);
        soDeTarde.setHoraFim(dezesseisHoras);
        when(bloqueioRepository.findAllByBarbeariaIdAndDataBetweenOrderByData(minhaBarbearia,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).thenReturn(List.of(feriadoDoSalao, folga, soDeTarde));

        List<BloqueioResponse> doMes = bloqueioService.bloqueiosDoMes(YearMonth.of(2026, 11));

        assertEquals(1, doMes.size());
        assertEquals("Feriado", doMes.get(0).motivo());
        assertEquals(feriado, doMes.get(0).data());
    }

    @Test
    void folgasDoMesFiltramPeloProfissional() {
        Profissional gabriel = profissionalDoGabriel();
        Profissional diego = new Profissional();
        diego.setId(UUID.randomUUID());
        when(bloqueioRepository.findAllByBarbeariaIdAndDataBetweenOrderByData(minhaBarbearia,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).thenReturn(List.of(
                diaInteiro(feriado, gabriel, "Folga"), diaInteiro(feriado, diego, "Médico"), diaInteiro(feriado, null, "Feriado")));

        assertEquals(2, bloqueioService.folgasDoMes(YearMonth.of(2026, 11), null).size());
        List<BloqueioResponse> doGabriel = bloqueioService.folgasDoMes(YearMonth.of(2026, 11), gabriel.getId());
        assertEquals(1, doGabriel.size());
        assertEquals(gabriel.getId(), doGabriel.get(0).profissionalId());
    }

    @Test
    void fecharDiaCriaOBloqueioDoDiaInteiroComMotivoPadrao() {
        when(barbeariaRepository.getReferenceById(minhaBarbearia)).thenReturn(barbearia);

        bloqueioService.fecharDia(feriado, "  ");

        ArgumentCaptor<BloqueioHorario> salvo = ArgumentCaptor.forClass(BloqueioHorario.class);
        verify(bloqueioRepository).save(salvo.capture());
        assertEquals(feriado, salvo.getValue().getData());
        assertNull(salvo.getValue().getHoraInicio());
        assertNull(salvo.getValue().getProfissional());
        assertEquals("Sem motivo informado", salvo.getValue().getMotivo());
    }

    @Test
    void fecharDiaQueJaEstaFechadoSoTrocaOMotivo() {
        BloqueioHorario existente = diaInteiro(feriado, null, "Feriado");
        when(bloqueioRepository.findAllByBarbeariaIdAndData(minhaBarbearia, feriado)).thenReturn(List.of(existente));

        bloqueioService.fecharDia(feriado, "Consciência Negra");

        verify(bloqueioRepository).save(existente);
        assertEquals("Consciência Negra", existente.getMotivo());
        // os clientes já foram avisados quando o dia foi fechado
        verifyNoInteractions(agendamentoRepository, notificacaoService);
    }

    @Test
    void fecharDiaAvisaOsClientesComHorarioMarcadoEOHorarioContinua() {
        when(barbeariaRepository.getReferenceById(minhaBarbearia)).thenReturn(barbearia);
        Agendamento aprovado = agendamento(feriado, LocalTime.of(10, 0), StatusAgendamento.APROVADO, profissionalDoGabriel());
        Agendamento pendente = agendamento(feriado, catorzeHoras, StatusAgendamento.PENDENTE, profissionalDoGabriel());
        Agendamento cancelado = agendamento(feriado, dezesseisHoras, StatusAgendamento.CANCELADO, profissionalDoGabriel());
        when(agendamentoRepository.findAllByBarbeariaIdAndDataOrderByHoraInicioAscProfissionalNomeAsc(minhaBarbearia, feriado))
                .thenReturn(List.of(aprovado, pendente, cancelado));

        bloqueioService.fecharDia(feriado, "Consciência Negra");

        verify(notificacaoService).avisoDoSalao(aprovado, "A Barbearia Naoki não vai abrir em 20/11 (Consciência Negra)."
                + " Seu horário das 10:00 continua marcado: fale com o salão para remarcar.");
        verify(notificacaoService).avisoDoSalao(eq(pendente), anyString());
        verify(notificacaoService, never()).avisoDoSalao(eq(cancelado), anyString());
        // D1: o horário não é cancelado
        assertEquals(StatusAgendamento.APROVADO, aprovado.getStatus());
    }

    @Test
    void naoDaPraFecharUmDiaQueJaPassou() {
        ApiException erro = assertThrows(ApiException.class, () -> bloqueioService.fecharDia(hoje.minusDays(1), null));

        assertEquals(422, erro.getStatus().value());
        verify(bloqueioRepository, never()).save(any());
    }

    @Test
    void reabrirDiaApagaSoOBloqueioDoSalaoNaqueleDia() {
        BloqueioHorario doSalao = diaInteiro(feriado, null, "Feriado");
        BloqueioHorario folga = diaInteiro(feriado, profissionalDoGabriel(), "Folga");
        when(bloqueioRepository.findAllByBarbeariaIdAndData(minhaBarbearia, feriado)).thenReturn(List.of(doSalao, folga));

        bloqueioService.reabrirDia(feriado);

        verify(bloqueioRepository).deleteAll(List.of(doSalao));
    }

    @Test
    void darFolgaUsaMotivoFolgaEProfissionalDoSalao() {
        Profissional gabriel = profissionalDoGabriel();
        when(profissionalRepository.findByIdAndBarbeariaId(gabriel.getId(), minhaBarbearia)).thenReturn(Optional.of(gabriel));
        when(barbeariaRepository.getReferenceById(minhaBarbearia)).thenReturn(barbearia);

        bloqueioService.darFolga(gabriel.getId(), feriado, null);

        ArgumentCaptor<BloqueioHorario> salvo = ArgumentCaptor.forClass(BloqueioHorario.class);
        verify(bloqueioRepository).save(salvo.capture());
        assertEquals(gabriel, salvo.getValue().getProfissional());
        assertEquals("Folga", salvo.getValue().getMotivo());
    }

    @Test
    void darFolgaAvisaSoQuemTemHorarioComOProfissionalESemOMotivo() {
        Profissional gabriel = profissionalDoGabriel();
        gabriel.setNomeSocial("Naoki");
        when(profissionalRepository.findByIdAndBarbeariaId(gabriel.getId(), minhaBarbearia)).thenReturn(Optional.of(gabriel));
        when(barbeariaRepository.getReferenceById(minhaBarbearia)).thenReturn(barbearia);
        Agendamento comEle = agendamento(feriado, LocalTime.of(10, 0), StatusAgendamento.APROVADO, gabriel);
        Agendamento comOutro = agendamento(feriado, catorzeHoras, StatusAgendamento.APROVADO, profissionalDoGabriel());
        when(agendamentoRepository.findAllByBarbeariaIdAndDataOrderByHoraInicioAscProfissionalNomeAsc(minhaBarbearia, feriado))
                .thenReturn(List.of(comEle, comOutro));

        bloqueioService.darFolga(gabriel.getId(), feriado, "Consulta médica");

        // o motivo da folga é do profissional e não aparece para o cliente
        verify(notificacaoService).avisoDoSalao(comEle, "Naoki não vai atender em 20/11. Seu horário das 10:00"
                + " na Barbearia Naoki continua marcado: fale com o salão para remarcar.");
        verify(notificacaoService, never()).avisoDoSalao(eq(comOutro), anyString());
    }

    @Test
    void folgaDeProfissionalDeOutroSalaoDevolve404() {
        UUID deOutroSalao = UUID.randomUUID();
        when(profissionalRepository.findByIdAndBarbeariaId(deOutroSalao, minhaBarbearia)).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class, () -> bloqueioService.darFolga(deOutroSalao, feriado, null));

        assertEquals(404, erro.getStatus().value());
        verify(bloqueioRepository, never()).save(any());
    }

    @Test
    void tirarFolgaApagaSoAFolgaDaqueleProfissional() {
        Profissional gabriel = profissionalDoGabriel();
        BloqueioHorario folga = diaInteiro(feriado, gabriel, "Folga");
        BloqueioHorario doSalao = diaInteiro(feriado, null, "Feriado");
        when(profissionalRepository.findByIdAndBarbeariaId(gabriel.getId(), minhaBarbearia)).thenReturn(Optional.of(gabriel));
        when(bloqueioRepository.findAllByBarbeariaIdAndData(minhaBarbearia, feriado)).thenReturn(List.of(folga, doSalao));

        bloqueioService.tirarFolga(gabriel.getId(), feriado);

        verify(bloqueioRepository).deleteAll(List.of(folga));
    }

    private Agendamento agendamento(LocalDate data, LocalTime hora, StatusAgendamento status, Profissional quem) {
        Agendamento a = new Agendamento();
        a.setId(UUID.randomUUID());
        a.setBarbearia(barbearia);
        a.setData(data);
        a.setHoraInicio(hora);
        a.setStatus(status);
        AgendamentoItem item = new AgendamentoItem();
        item.setNome("Corte");
        item.setProfissional(quem);
        item.setPreco(new BigDecimal("40.00"));
        item.setDuracaoMinutos(30);
        a.adicionarItem(item);
        return a;
    }

    private Profissional profissionalDoGabriel() {
        Profissional p = new Profissional();
        p.setId(UUID.randomUUID());
        p.setNome("Gabriel Naoki");
        return p;
    }

    private BloqueioHorario diaInteiro(LocalDate data, Profissional profissional, String motivo) {
        BloqueioHorario b = new BloqueioHorario();
        b.setId(UUID.randomUUID());
        b.setBarbearia(barbearia);
        b.setData(data);
        b.setProfissional(profissional);
        b.setMotivo(motivo);
        return b;
    }
}
