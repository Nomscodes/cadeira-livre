package br.com.cadeiralivre.api.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.servico.dto.ComboRequest;
import br.com.cadeiralivre.api.servico.dto.ComboResponse;
import br.com.cadeiralivre.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class ComboServiceTest {

    @Mock
    private ComboRepository comboRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @InjectMocks
    private ComboService comboService;

    private final Barbearia barbearia = new Barbearia();
    private Servico corte;
    private Servico barba;
    private Profissional gabriel;

    @BeforeEach
    void preparar() {
        barbearia.setId(UUID.randomUUID());
        barbearia.setNomeFantasia("Barbearia Gabriel Naoki");
        TenantContext.set(barbearia.getId());
        corte = servico("Corte", "45.00", 30);
        barba = servico("Barba", "30.00", 20);
        gabriel = new Profissional();
        gabriel.setId(UUID.randomUUID());
        gabriel.setNome("Gabriel Naoki");
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    @Test
    void criarComboComDoisServicosEUmProfissional() {
        when(barbeariaRepository.getReferenceById(barbearia.getId())).thenReturn(barbearia);
        when(servicoRepository.findAllByIdInAndBarbeariaId(Set.of(corte.getId(), barba.getId()), barbearia.getId()))
                .thenReturn(List.of(corte, barba));
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(barbearia.getId())).thenReturn(List.of(gabriel));
        when(comboRepository.save(any(Combo.class))).thenAnswer(chamada -> chamada.getArgument(0));

        ComboResponse combo = comboService.criar(new ComboRequest(" Corte + Barba ",
                List.of(corte.getId(), barba.getId()), new BigDecimal("60.00"), List.of(gabriel.getId())));

        assertEquals("Corte + Barba", combo.nome());
        assertEquals(new BigDecimal("60.00"), combo.preco());
        assertEquals(50, combo.duracao()); // 30 + 20
        assertEquals(List.of(barba.getId(), corte.getId()), combo.servicoIds()); // em ordem de nome
        assertEquals(List.of(gabriel.getId()), combo.profissionalIds());
    }

    @Test
    void mesmoServicoRepetidoNaoFormaCombo() {
        ApiException erro = assertThrows(ApiException.class, () -> comboService.criar(new ComboRequest("Corte duplo",
                List.of(corte.getId(), corte.getId()), new BigDecimal("80.00"), null)));

        assertEquals(422, erro.getStatus().value());
        assertEquals("Um combo precisa de pelo menos dois serviços diferentes.", erro.getMessage());
        verify(comboRepository, never()).save(any());
    }

    @Test
    void servicoInativoOuDeOutroSalaoDevolve422() {
        when(barbeariaRepository.getReferenceById(barbearia.getId())).thenReturn(barbearia);
        barba.setAtivo(false);
        UUID deOutroSalao = UUID.randomUUID();
        when(servicoRepository.findAllByIdInAndBarbeariaId(any(), eq(barbearia.getId()))).thenReturn(List.of(corte, barba));

        ApiException erro = assertThrows(ApiException.class, () -> comboService.criar(new ComboRequest("Combo",
                List.of(corte.getId(), barba.getId(), deOutroSalao), new BigDecimal("60.00"), null)));

        assertEquals(422, erro.getStatus().value());
        assertEquals("Escolha serviços ativos do seu salão.", erro.getMessage());
    }

    @Test
    void profissionalDeOutroSalaoDevolve422() {
        when(barbeariaRepository.getReferenceById(barbearia.getId())).thenReturn(barbearia);
        when(servicoRepository.findAllByIdInAndBarbeariaId(any(), eq(barbearia.getId()))).thenReturn(List.of(corte, barba));
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(barbearia.getId())).thenReturn(List.of(gabriel));

        ApiException erro = assertThrows(ApiException.class, () -> comboService.criar(new ComboRequest("Combo",
                List.of(corte.getId(), barba.getId()), new BigDecimal("60.00"), List.of(UUID.randomUUID()))));

        assertEquals("Escolha profissionais do seu salão.", erro.getMessage());
        verify(comboRepository, never()).save(any());
    }

    @Test
    void editarTrocaNomePrecoServicosEProfissionais() {
        Combo existente = combo();
        Servico sobrancelha = servico("Sobrancelha", "15.00", 10);
        when(comboRepository.findByIdAndBarbeariaIdAndAtivoTrue(existente.getId(), barbearia.getId()))
                .thenReturn(Optional.of(existente));
        when(servicoRepository.findAllByIdInAndBarbeariaId(any(), eq(barbearia.getId())))
                .thenReturn(List.of(corte, sobrancelha));
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(barbearia.getId())).thenReturn(List.of(gabriel));

        ComboResponse editado = comboService.atualizar(existente.getId(), new ComboRequest("Corte + Sobrancelha",
                List.of(corte.getId(), sobrancelha.getId()), new BigDecimal("55.00"), List.of()));

        assertEquals("Corte + Sobrancelha", existente.getNome());
        assertEquals(40, editado.duracao());
        assertEquals(Set.of(corte, sobrancelha), existente.getServicos());
        assertEquals(List.of(), editado.profissionalIds());
    }

    @Test
    void excluirDesativaOCombo() {
        Combo existente = combo();
        when(comboRepository.findByIdAndBarbeariaIdAndAtivoTrue(existente.getId(), barbearia.getId()))
                .thenReturn(Optional.of(existente));

        comboService.excluir(existente.getId());

        assertFalse(existente.isAtivo());
    }

    @Test
    void comboDeOutroSalaoOuJaExcluidoDevolve404() {
        UUID id = UUID.randomUUID();
        when(comboRepository.findByIdAndBarbeariaIdAndAtivoTrue(id, barbearia.getId())).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class, () -> comboService.excluir(id));

        assertEquals(404, erro.getStatus().value());
    }

    private Combo combo() {
        Combo c = new Combo();
        c.setId(UUID.randomUUID());
        c.setBarbearia(barbearia);
        c.setNome("Corte + Barba");
        c.setPreco(new BigDecimal("60.00"));
        c.getServicos().addAll(Set.of(corte, barba));
        c.getProfissionais().add(gabriel);
        return c;
    }

    private Servico servico(String nome, String preco, int minutos) {
        Servico s = new Servico();
        s.setId(UUID.randomUUID());
        s.setBarbearia(barbearia);
        s.setNome(nome);
        s.setPreco(new BigDecimal(preco));
        s.setDuracaoMinutos(minutos);
        return s;
    }
}
