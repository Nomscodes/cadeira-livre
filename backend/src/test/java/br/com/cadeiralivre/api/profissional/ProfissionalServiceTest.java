package br.com.cadeiralivre.api.profissional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.notificacao.NotificacaoService;
import br.com.cadeiralivre.api.profissional.dto.ProfissionalRequest;
import br.com.cadeiralivre.api.profissional.dto.ProfissionalResponse;
import br.com.cadeiralivre.api.servico.Combo;
import br.com.cadeiralivre.api.servico.ComboRepository;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class ProfissionalServiceTest {

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private ProfissionalHorarioRepository horarioRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private ComboRepository comboRepository;

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private NotificacaoService notificacaoService;

    // "Agora" fixo nos testes: 10/11/2026 às 09:00 no horário de Brasília
    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private ProfissionalService profissionalService;

    private final Barbearia minhaBarbearia = new Barbearia();
    private Servico corte;

    @BeforeEach
    void logarComoDono() {
        // o Clock não é mock, por isso o service é criado na mão
        profissionalService = new ProfissionalService(profissionalRepository, horarioRepository, servicoRepository,
                comboRepository, barbeariaRepository, agendamentoRepository, notificacaoService, relogio);
        minhaBarbearia.setId(UUID.randomUUID());
        minhaBarbearia.setNomeFantasia("Barbearia Gabriel Naoki");
        TenantContext.set(minhaBarbearia.getId());
        corte = new Servico();
        corte.setId(UUID.randomUUID());
        corte.setNome("Corte");
        corte.setPreco(new BigDecimal("40.00"));
        corte.setDuracaoMinutos(30);
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    // ---------- lista ----------

    @Test
    void listarMostraSoOsAtivosComOHorarioDeCadaUm() {
        Profissional gabriel = profissional("Gabriel Naoki", true);
        Profissional saiu = profissional("Diego Santos", false);
        saiu.setAtivo(false);
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(minhaBarbearia.getId())).thenReturn(List.of(saiu, gabriel));
        when(horarioRepository.findAllByProfissionalBarbeariaId(minhaBarbearia.getId())).thenReturn(List.of(
                new ProfissionalHorario(gabriel, DayOfWeek.SATURDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), true)));

        List<ProfissionalResponse> lista = profissionalService.listar();

        assertEquals(1, lista.size());
        ProfissionalResponse resposta = lista.get(0);
        assertEquals("Gabriel Naoki", resposta.nome());
        assertTrue(resposta.ehAdmin());
        assertEquals(new ProfissionalResponse.Dia(true, "08:00", "12:00"), resposta.horarios().get("6"));
        assertFalse(resposta.horarios().get("1").ativo()); // dia sem horário vem como folga
    }

    @Test
    void semBarbeariaNoTokenDevolve403() {
        TenantContext.clear();

        ApiException erro = assertThrows(ApiException.class, () -> profissionalService.listar());

        assertEquals(403, erro.getStatus().value());
    }

    // ---------- cadastro ----------

    @Test
    void cadastroGeraConviteESemanaPadrao() {
        when(barbeariaRepository.getReferenceById(minhaBarbearia.getId())).thenReturn(minhaBarbearia);
        when(servicoRepository.findAllByIdInAndBarbeariaId(Set.of(corte.getId()), minhaBarbearia.getId())).thenReturn(List.of(corte));
        when(horarioRepository.saveAll(anyList())).thenAnswer(chamada -> chamada.getArgument(0));

        ProfissionalResponse.ComConvite criado = profissionalService.criar(new ProfissionalRequest(" Gabriel Naoki ",
                "Naoki", "(62) 98888-7777", true, null, Set.of(corte.getId())));

        verify(profissionalRepository).save(any(Profissional.class));
        assertTrue(criado.convite().matches("[A-Z2-9]{6}")); // 6 letras e números
        ProfissionalResponse p = criado.profissional();
        assertEquals("Gabriel Naoki", p.nome());
        assertEquals("Naoki", p.nomeSocial());
        assertEquals("(62) 98888-7777", p.whatsapp());
        assertTrue(p.encaminhar());
        assertTrue(p.atende()); // sem informar, atende
        assertFalse(p.ehAdmin());
        assertEquals(List.of(corte.getId()), p.servicoIds());
        assertEquals(new ProfissionalResponse.Dia(true, "09:00", "18:00"), p.horarios().get("1"));
        assertFalse(p.horarios().get("0").ativo());
    }

    @Test
    void encaminharSemWhatsappDevolve422() {
        when(barbeariaRepository.getReferenceById(minhaBarbearia.getId())).thenReturn(minhaBarbearia);

        ApiException erro = assertThrows(ApiException.class, () -> profissionalService.criar(
                new ProfissionalRequest("Gabriel Naoki", null, "  ", true, true, null)));

        assertEquals("Para ativar o encaminhamento, informe o WhatsApp do profissional.", erro.getMessage());
        verify(profissionalRepository, never()).save(any());
    }

    @Test
    void servicoDeOutroSalaoDevolve422() {
        when(barbeariaRepository.getReferenceById(minhaBarbearia.getId())).thenReturn(minhaBarbearia);
        UUID deOutroSalao = UUID.randomUUID();
        when(servicoRepository.findAllByIdInAndBarbeariaId(Set.of(deOutroSalao), minhaBarbearia.getId())).thenReturn(List.of());

        ApiException erro = assertThrows(ApiException.class, () -> profissionalService.criar(
                new ProfissionalRequest("Gabriel Naoki", null, null, false, true, Set.of(deOutroSalao))));

        assertEquals(422, erro.getStatus().value());
    }

    // ---------- edição ----------

    @Test
    void quemDeixaDeAtenderFicaSemServicosESaiDosCombos() {
        Profissional gabriel = existente("Gabriel Naoki", false);
        gabriel.getServicos().add(corte);
        Combo combo = new Combo();
        combo.getProfissionais().add(gabriel);
        when(comboRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(minhaBarbearia.getId())).thenReturn(List.of(combo));

        ProfissionalResponse.ComConvite editado = profissionalService.atualizar(gabriel.getId(),
                new ProfissionalRequest("Gabriel Naoki", null, null, false, false, Set.of(corte.getId())));

        assertFalse(editado.profissional().atende());
        assertTrue(gabriel.getServicos().isEmpty());
        assertTrue(combo.getProfissionais().isEmpty());
        assertNull(editado.convite()); // o convite só aparece no cadastro
    }

    // ---------- remoção ----------

    @Test
    void naoDaPraRemoverOUnicoAdministrador() {
        Profissional gabriel = existente("Gabriel Naoki", true);
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(minhaBarbearia.getId())).thenReturn(List.of(gabriel));

        ApiException erro = assertThrows(ApiException.class, () -> profissionalService.excluir(gabriel.getId()));

        assertEquals(422, erro.getStatus().value());
        assertTrue(gabriel.isAtivo());
    }

    @Test
    void administradorPodeSairSeHouverOutro() {
        Profissional gabriel = existente("Gabriel Naoki", true);
        Profissional diego = profissional("Diego Santos", true);
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(minhaBarbearia.getId())).thenReturn(List.of(diego, gabriel));

        profissionalService.excluir(gabriel.getId());

        assertFalse(gabriel.isAtivo());
    }

    @Test
    void removerDesativaTiraDosServicosEDosCombosEApagaOConvite() {
        Profissional diego = existente("Diego Santos", false);
        diego.setCodigoConvite("ABC234");
        diego.getServicos().add(corte);
        Combo combo = new Combo();
        combo.getProfissionais().add(diego);
        when(comboRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(minhaBarbearia.getId())).thenReturn(List.of(combo));

        profissionalService.excluir(diego.getId());

        assertFalse(diego.isAtivo());
        assertNull(diego.getCodigoConvite());
        assertTrue(diego.getServicos().isEmpty());
        assertTrue(combo.getProfissionais().isEmpty());
        verify(profissionalRepository, never()).delete(any());
    }

    @Test
    void removerAvisaOsClientesComHorarioMarcadoEOHorarioContinua() {
        Profissional diego = existente("Diego Santos", false);
        diego.setNomeSocial("Diego");
        Agendamento amanha = agendamento(LocalDate.of(2026, 11, 11), StatusAgendamento.APROVADO);
        Agendamento pendente = agendamento(LocalDate.of(2026, 11, 12), StatusAgendamento.PENDENTE);
        Agendamento cancelado = agendamento(LocalDate.of(2026, 11, 11), StatusAgendamento.CANCELADO);
        Agendamento ontem = agendamento(LocalDate.of(2026, 11, 9), StatusAgendamento.APROVADO);
        when(agendamentoRepository.doProfissional(diego.getId())).thenReturn(List.of(ontem, amanha, pendente, cancelado));

        profissionalService.excluir(diego.getId());

        verify(notificacaoService).avisoDoSalao(amanha, "Diego não faz mais parte da equipe da Barbearia Gabriel Naoki."
                + " Seu horário de 11/11 às 10:00 continua marcado: fale com o salão para remarcar.");
        verify(notificacaoService).avisoDoSalao(eq(pendente), anyString());
        verify(notificacaoService, never()).avisoDoSalao(eq(cancelado), anyString());
        verify(notificacaoService, never()).avisoDoSalao(eq(ontem), anyString());
        // D1: o horário não é cancelado
        assertEquals(StatusAgendamento.APROVADO, amanha.getStatus());
    }

    @Test
    void profissionalJaRemovidoDevolve404() {
        Profissional diego = existente("Diego Santos", false);
        diego.setAtivo(false);

        ApiException erro = assertThrows(ApiException.class, () -> profissionalService.excluir(diego.getId()));

        assertEquals(404, erro.getStatus().value());
    }

    // ---------- métodos de apoio ----------

    private Agendamento agendamento(LocalDate data, StatusAgendamento status) {
        Agendamento a = new Agendamento();
        a.setId(UUID.randomUUID());
        a.setBarbearia(minhaBarbearia);
        a.setData(data);
        a.setHoraInicio(LocalTime.of(10, 0));
        a.setStatus(status);
        return a;
    }

    private Profissional profissional(String nome, boolean administrador) {
        Profissional p = new Profissional();
        p.setId(UUID.randomUUID());
        p.setBarbearia(minhaBarbearia);
        p.setNome(nome);
        p.setAdministrador(administrador);
        return p;
    }

    private Profissional existente(String nome, boolean administrador) {
        Profissional p = profissional(nome, administrador);
        when(profissionalRepository.findByIdAndBarbeariaId(p.getId(), minhaBarbearia.getId())).thenReturn(Optional.of(p));
        return p;
    }
}
