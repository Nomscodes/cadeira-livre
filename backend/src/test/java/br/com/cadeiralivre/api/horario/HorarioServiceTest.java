package br.com.cadeiralivre.api.horario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.dto.HorariosDoPainelResponse;
import br.com.cadeiralivre.api.horario.dto.SemanaRequest;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalHorario;
import br.com.cadeiralivre.api.profissional.ProfissionalHorarioRepository;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class HorarioServiceTest {

    @Mock
    private HorarioFuncionamentoRepository horarioRepository;

    @Mock
    private ProfissionalHorarioRepository profissionalHorarioRepository;

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @InjectMocks
    private HorarioService horarioService;

    private final Barbearia minhaBarbearia = new Barbearia();
    private Profissional gabriel;

    @BeforeEach
    void preparar() {
        minhaBarbearia.setId(UUID.randomUUID());
        minhaBarbearia.setNomeFantasia("Barbearia Gabriel Naoki");
        TenantContext.set(minhaBarbearia.getId());
        gabriel = new Profissional();
        gabriel.setId(UUID.randomUUID());
        gabriel.setBarbearia(minhaBarbearia);
        gabriel.setNome("Gabriel Naoki");
        gabriel.setAdministrador(true);
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    // ---------- lista ----------

    @Test
    void listarDevolveOSalaoEQuemAtendeNoFormatoDoFront() {
        Profissional naoAtende = new Profissional();
        naoAtende.setId(UUID.randomUUID());
        naoAtende.setNome("Dono que só gerencia");
        naoAtende.setAtende(false);
        when(horarioRepository.findAllByBarbeariaId(minhaBarbearia.getId()))
                .thenReturn(List.of(doSalao(DayOfWeek.MONDAY, true, 9, 18)));
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(minhaBarbearia.getId())).thenReturn(List.of(naoAtende, gabriel));
        when(profissionalHorarioRepository.findAllByProfissionalBarbeariaId(minhaBarbearia.getId()))
                .thenReturn(List.of(new ProfissionalHorario(gabriel, DayOfWeek.MONDAY, hora(10), hora(17), true)));

        HorariosDoPainelResponse resposta = horarioService.listar();

        assertEquals(new HorariosDoPainelResponse.Dia(true, "09:00", "18:00"), resposta.salao().horarios().get("1"));
        assertFalse(resposta.salao().horarios().get("0").ativo()); // domingo sem horário = fechado
        assertEquals(1, resposta.profissionais().size()); // quem não atende fica de fora
        HorariosDoPainelResponse.ProfissionalComHorario p = resposta.profissionais().get(0);
        assertEquals("Gabriel Naoki", p.nome());
        assertTrue(p.ehAdmin());
        assertEquals(new HorariosDoPainelResponse.Dia(true, "10:00", "17:00"), p.horarios().get("1"));
        assertTrue(resposta.avisos().isEmpty());
    }

    @Test
    void semBarbeariaNoTokenDevolve403() {
        TenantContext.clear();

        ApiException erro = assertThrows(ApiException.class, () -> horarioService.listar());

        assertEquals(403, erro.getStatus().value());
    }

    // ---------- horário do salão ----------

    @Test
    void salvarDoSalaoGravaOsSeteDiasDaBarbeariaDoToken() {
        when(barbeariaRepository.getReferenceById(minhaBarbearia.getId())).thenReturn(minhaBarbearia);

        horarioService.salvarDoSalao(semana(1, 2, 3, 4, 5));

        ArgumentCaptor<HorarioFuncionamento> salvos = ArgumentCaptor.forClass(HorarioFuncionamento.class);
        verify(horarioRepository, times(7)).save(salvos.capture());
        HorarioFuncionamento segunda = salvos.getAllValues().stream()
                .filter(h -> h.getDiaSemana() == DayOfWeek.MONDAY).findFirst().orElseThrow();
        assertTrue(segunda.isAtivo());
        assertEquals(hora(9), segunda.getHoraAbertura());
        assertEquals(minhaBarbearia, segunda.getBarbearia());
        assertFalse(salvos.getAllValues().stream()
                .filter(h -> h.getDiaSemana() == DayOfWeek.SUNDAY).findFirst().orElseThrow().isAtivo());
    }

    @Test
    void salvarAtualizaODiaQueJaExisteSemCriarOutro() {
        HorarioFuncionamento segunda = doSalao(DayOfWeek.MONDAY, false, 8, 12);
        when(horarioRepository.findAllByBarbeariaId(minhaBarbearia.getId())).thenReturn(List.of(segunda));
        when(barbeariaRepository.getReferenceById(minhaBarbearia.getId())).thenReturn(minhaBarbearia);

        horarioService.salvarDoSalao(semana(1));

        assertTrue(segunda.isAtivo());
        assertEquals(hora(9), segunda.getHoraAbertura());
        assertEquals(hora(18), segunda.getHoraFechamento());
    }

    @Test
    void salaoFechadoTodosOsDiasDevolve422() {
        ApiException erro = assertThrows(ApiException.class, () -> horarioService.salvarDoSalao(semana()));

        assertEquals("O salão precisa abrir em pelo menos um dia da semana.", erro.getMessage());
        verify(horarioRepository, never()).save(any());
    }

    @Test
    void terminoAntesDoInicioDevolve422ComONomeDoDia() {
        SemanaRequest req = semana(1);
        req.horarios().put("2", new SemanaRequest.Dia(true, hora(18), hora(9)));

        ApiException erro = assertThrows(ApiException.class, () -> horarioService.salvarDoSalao(req));

        assertEquals("Na terça, o término precisa ser depois do início.", erro.getMessage());
    }

    @Test
    void faltandoUmDiaDaSemanaDevolve422() {
        SemanaRequest req = semana(1);
        req.horarios().remove("6");

        ApiException erro = assertThrows(ApiException.class, () -> horarioService.salvarDoSalao(req));

        assertEquals("Horários incompletos: envie os 7 dias da semana.", erro.getMessage());
    }

    // ---------- horário do profissional e avisos ----------

    @Test
    void salvarDoProfissionalGravaOsDiasEAvisaDosConflitos() {
        when(profissionalRepository.findByIdAndBarbeariaId(gabriel.getId(), minhaBarbearia.getId())).thenReturn(Optional.of(gabriel));
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(minhaBarbearia.getId())).thenReturn(List.of(gabriel));
        // salão: segunda das 9h às 18h; domingo fechado
        when(horarioRepository.findAllByBarbeariaId(minhaBarbearia.getId())).thenReturn(List.of(doSalao(DayOfWeek.MONDAY, true, 9, 18)));
        // o que já está gravado do Gabriel (é o que os avisos olham)
        when(profissionalHorarioRepository.findAllByProfissionalBarbeariaId(minhaBarbearia.getId())).thenReturn(List.of(
                new ProfissionalHorario(gabriel, DayOfWeek.MONDAY, hora(8), hora(18), true),
                new ProfissionalHorario(gabriel, DayOfWeek.SUNDAY, hora(9), hora(12), true)));

        List<String> avisos = horarioService.salvarDoProfissional(gabriel.getId(), semana(0, 1));

        verify(profissionalHorarioRepository, times(7)).save(any(ProfissionalHorario.class));
        // os dias vão de domingo ("0") a sábado ("6"), igual ao front
        assertEquals(List.of(
                "Gabriel Naoki trabalha na domingo, mas o salão fica fechado nesse dia. Esse dia não terá horários.",
                "Gabriel Naoki na segunda: o horário passa do funcionamento do salão (09:00 às 18:00). "
                        + "O atendimento fica limitado ao horário do salão."),
                avisos);
    }

    @Test
    void profissionalPodeFicarSemNenhumDia() {
        when(profissionalRepository.findByIdAndBarbeariaId(gabriel.getId(), minhaBarbearia.getId())).thenReturn(Optional.of(gabriel));

        horarioService.salvarDoProfissional(gabriel.getId(), semana());

        verify(profissionalHorarioRepository, times(7)).save(any(ProfissionalHorario.class));
    }

    @Test
    void profissionalDeOutroSalaoDevolve404() {
        UUID deOutroSalao = UUID.randomUUID();
        when(profissionalRepository.findByIdAndBarbeariaId(deOutroSalao, minhaBarbearia.getId())).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class,
                () -> horarioService.salvarDoProfissional(deOutroSalao, semana(1)));

        assertEquals(404, erro.getStatus().value());
        verify(profissionalHorarioRepository, never()).save(any());
    }

    // ---------- métodos de apoio ----------

    // semana do front com os dias informados abertos das 9h às 18h ("0" = domingo)
    private SemanaRequest semana(int... diasAbertos) {
        List<Integer> abertos = new ArrayList<>();
        for (int d : diasAbertos) abertos.add(d);
        Map<String, SemanaRequest.Dia> dias = new HashMap<>();
        for (int d = 0; d < 7; d++) {
            dias.put(String.valueOf(d), new SemanaRequest.Dia(abertos.contains(d), hora(9), hora(18)));
        }
        return new SemanaRequest(dias);
    }

    private HorarioFuncionamento doSalao(DayOfWeek dia, boolean ativo, int abre, int fecha) {
        HorarioFuncionamento h = new HorarioFuncionamento();
        h.setBarbearia(minhaBarbearia);
        h.setDiaSemana(dia);
        h.setAtivo(ativo);
        h.setHoraAbertura(hora(abre));
        h.setHoraFechamento(hora(fecha));
        return h;
    }

    private LocalTime hora(int h) {
        return LocalTime.of(h, 0);
    }
}
