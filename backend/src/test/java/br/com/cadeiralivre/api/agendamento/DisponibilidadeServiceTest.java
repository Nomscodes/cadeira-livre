package br.com.cadeiralivre.api.agendamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.agendamento.dto.DisponibilidadeResponse;
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

@ExtendWith(MockitoExtension.class)
class DisponibilidadeServiceTest {

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private HorarioFuncionamentoRepository horarioRepository;

    @Mock
    private BloqueioHorarioRepository bloqueioRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    private DisponibilidadeService disponibilidadeService;

    // "Agora" fixo nos testes: terça, 10/11/2026 às 09:00 no horário de Brasília
    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private final LocalDate hoje = LocalDate.of(2026, 11, 10);   // terça
    private final LocalDate amanha = LocalDate.of(2026, 11, 11); // quarta

    private Barbearia barbearia;
    private Servico corte;
    private Profissional rafael;
    private Profissional diego;

    @BeforeEach
    void preparar() {
        // o Clock não é mock, por isso o service é criado na mão
        disponibilidadeService = new DisponibilidadeService(barbeariaRepository, servicoRepository,
                profissionalRepository, horarioRepository, bloqueioRepository, agendamentoRepository, relogio);

        barbearia = new Barbearia();
        barbearia.setId(UUID.randomUUID());
        barbearia.setSlug("barbearia-teste");
        barbearia.setPublico(true);

        corte = new Servico();
        corte.setId(UUID.randomUUID());
        corte.setNome("Corte");
        corte.setDuracaoMinutos(30);
        corte.setBarbearia(barbearia);

        rafael = profissional("Rafael Lima");
        diego = profissional("Diego Santos");
    }

    // ---------- funcionamento do dia (RF19) ----------

    @Test
    void ofereceHorariosDeMeiaEmMeiaHoraDentroDoFuncionamento() {
        salaoComProfissionais(rafael);
        abertoNoDia(amanha, "09:00", "12:00");

        assertEquals(horas("09:00", "09:30", "10:00", "10:30", "11:00", "11:30"), horariosDe(rafael, amanha));
    }

    @Test
    void servicoMaisLongoSoComecaSeTerminarAteOFechamento() {
        corte.setDuracaoMinutos(60);
        salaoComProfissionais(rafael);
        abertoNoDia(amanha, "09:00", "12:00");

        // 11:30 ficou de fora: o serviço terminaria 12:30, depois do fechamento
        assertEquals(horas("09:00", "09:30", "10:00", "10:30", "11:00"), horariosDe(rafael, amanha));
    }

    @Test
    void diaFechadoNaoTemHorarios() {
        salaoComProfissionais(rafael);
        HorarioFuncionamento quarta = horario(amanha, "09:00", "12:00");
        quarta.setAtivo(false);
        when(horarioRepository.findByBarbeariaIdAndDiaSemana(barbearia.getId(), amanha.getDayOfWeek()))
                .thenReturn(Optional.of(quarta));

        assertTrue(horariosDe(rafael, amanha).isEmpty());
    }

    @Test
    void hojeSoOfereceHorariosQueAindaNaoPassaram() {
        salaoComProfissionais(rafael);
        abertoNoDia(hoje, "08:00", "11:00");

        // agora são 09:00: 08:00, 08:30 e 09:00 já passaram
        assertEquals(horas("09:30", "10:00", "10:30"), horariosDe(rafael, hoje));
    }

    // ---------- bloqueios e folgas (RF20) ----------

    @Test
    void feriadoBloqueiaODiaInteiroParaTodos() {
        salaoComProfissionais(rafael);
        abertoNoDia(amanha, "09:00", "12:00");
        bloqueiosDeAmanha(bloqueio(null, null, null));

        assertTrue(horariosDe(rafael, amanha).isEmpty());
    }

    @Test
    void bloqueioDeAlgumasHorasTiraSoOsHorariosQueBatem() {
        salaoComProfissionais(rafael);
        abertoNoDia(amanha, "09:00", "12:00");
        bloqueiosDeAmanha(bloqueio(null, "10:00", "11:00"));

        // 09:30 continua: o corte termina 10:00, bem na hora em que o bloqueio começa
        assertEquals(horas("09:00", "09:30", "11:00", "11:30"), horariosDe(rafael, amanha));
    }

    @Test
    void folgaBloqueiaSoOProfissionalDaFolga() {
        salaoComProfissionais(diego, rafael);
        abertoNoDia(amanha, "09:00", "12:00");
        bloqueiosDeAmanha(bloqueio(diego, null, null));

        DisponibilidadeResponse resposta = consultar(amanha);

        assertTrue(resposta.profissionais().get(0).horarios().isEmpty());     // Diego está de folga
        assertEquals(6, resposta.profissionais().get(1).horarios().size());   // Rafael atende normal
    }

    // ---------- agendamentos aprovados (RF21) ----------

    @Test
    void agendamentoAprovadoOcupaSoOHorarioDoProfissionalDele() {
        salaoComProfissionais(diego, rafael);
        abertoNoDia(amanha, "09:00", "12:00");
        Agendamento comRafael = new Agendamento();
        comRafael.setProfissional(rafael);
        comRafael.setHoraInicio(LocalTime.parse("10:00"));
        comRafael.setHoraFim(LocalTime.parse("10:30"));
        when(agendamentoRepository.findAllByBarbeariaIdAndDataAndStatus(barbearia.getId(), amanha, StatusAgendamento.APROVADO))
                .thenReturn(List.of(comRafael));

        DisponibilidadeResponse resposta = consultar(amanha);

        assertTrue(resposta.profissionais().get(0).horarios().contains(LocalTime.parse("10:00")));  // Diego livre
        assertFalse(resposta.profissionais().get(1).horarios().contains(LocalTime.parse("10:00"))); // Rafael ocupado
    }

    // ---------- conferência na hora de agendar (RF14) ----------

    @Test
    void soDaParaAgendarHorarioQueApareceNaDisponibilidade() {
        abertoNoDia(amanha, "09:00", "12:00");

        assertTrue(disponibilidadeService.estaLivre(barbearia, corte, rafael, amanha, LocalTime.parse("10:00")));
        // 10:15 não está na lista: os horários são de 30 em 30 minutos
        assertFalse(disponibilidadeService.estaLivre(barbearia, corte, rafael, amanha, LocalTime.parse("10:15")));
    }

    @Test
    void folgaDoProfissionalNaoDeixaAgendar() {
        abertoNoDia(amanha, "09:00", "12:00");
        bloqueiosDeAmanha(bloqueio(rafael, null, null));

        assertFalse(disponibilidadeService.estaLivre(barbearia, corte, rafael, amanha, LocalTime.parse("10:00")));
    }

    // ---------- portal e serviço ----------

    @Test
    void salaoQueAindaNaoFoiPublicadoDevolve404() {
        barbearia.setPublico(false);
        when(barbeariaRepository.findBySlug("barbearia-teste")).thenReturn(Optional.of(barbearia));

        ApiException erro = assertThrows(ApiException.class, () -> consultar(amanha));

        assertEquals(404, erro.getStatus().value());
    }

    @Test
    void servicoDesativadoDevolve422() {
        corte.setAtivo(false);
        when(barbeariaRepository.findBySlug("barbearia-teste")).thenReturn(Optional.of(barbearia));
        when(servicoRepository.findByIdAndBarbeariaId(corte.getId(), barbearia.getId())).thenReturn(Optional.of(corte));

        ApiException erro = assertThrows(ApiException.class, () -> consultar(amanha));

        assertEquals(422, erro.getStatus().value());
    }

    // ---------- métodos de apoio ----------

    private void salaoComProfissionais(Profissional... profissionais) {
        when(barbeariaRepository.findBySlug("barbearia-teste")).thenReturn(Optional.of(barbearia));
        when(servicoRepository.findByIdAndBarbeariaId(corte.getId(), barbearia.getId())).thenReturn(Optional.of(corte));
        when(profissionalRepository.buscarAtivosQueFazemOServico(barbearia.getId(), corte.getId()))
                .thenReturn(List.of(profissionais));
    }

    private void abertoNoDia(LocalDate dia, String abertura, String fechamento) {
        when(horarioRepository.findByBarbeariaIdAndDiaSemana(barbearia.getId(), dia.getDayOfWeek()))
                .thenReturn(Optional.of(horario(dia, abertura, fechamento)));
    }

    private HorarioFuncionamento horario(LocalDate dia, String abertura, String fechamento) {
        HorarioFuncionamento h = new HorarioFuncionamento();
        h.setBarbearia(barbearia);
        h.setDiaSemana(dia.getDayOfWeek());
        h.setHoraAbertura(LocalTime.parse(abertura));
        h.setHoraFechamento(LocalTime.parse(fechamento));
        return h;
    }

    private void bloqueiosDeAmanha(BloqueioHorario... bloqueios) {
        when(bloqueioRepository.findAllByBarbeariaIdAndData(barbearia.getId(), amanha)).thenReturn(List.of(bloqueios));
    }

    // profissional nulo = barbearia toda; horas nulas = dia inteiro
    private BloqueioHorario bloqueio(Profissional profissional, String inicio, String fim) {
        BloqueioHorario b = new BloqueioHorario();
        b.setProfissional(profissional);
        b.setData(amanha);
        b.setHoraInicio(inicio == null ? null : LocalTime.parse(inicio));
        b.setHoraFim(fim == null ? null : LocalTime.parse(fim));
        return b;
    }

    private Profissional profissional(String nome) {
        Profissional p = new Profissional();
        p.setId(UUID.randomUUID());
        p.setNome(nome);
        return p;
    }

    private DisponibilidadeResponse consultar(LocalDate dia) {
        return disponibilidadeService.consultar("barbearia-teste", corte.getId(), dia);
    }

    // só os horários livres de um profissional, tirados da resposta
    private List<LocalTime> horariosDe(Profissional profissional, LocalDate dia) {
        return consultar(dia).profissionais().stream()
                .filter(p -> p.id().equals(profissional.getId()))
                .findFirst().orElseThrow()
                .horarios();
    }

    private List<LocalTime> horas(String... horas) {
        return Arrays.stream(horas).map(LocalTime::parse).toList();
    }
}
