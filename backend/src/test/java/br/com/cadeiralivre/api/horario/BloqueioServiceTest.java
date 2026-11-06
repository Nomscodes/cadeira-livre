package br.com.cadeiralivre.api.horario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.dto.BloqueioRequest;
import br.com.cadeiralivre.api.horario.dto.BloqueioResponse;
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
        bloqueioService = new BloqueioService(bloqueioRepository, profissionalRepository, barbeariaRepository, relogio);

        // simula o que o JwtAuthenticationFilter faz com o token do dono
        TenantContext.set(minhaBarbearia);

        barbearia = new Barbearia();
        barbearia.setId(minhaBarbearia);
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
}
