package br.com.cadeiralivre.api.barbearia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.cadeiralivre.api.auditoria.AcaoAuditoria;
import br.com.cadeiralivre.api.auditoria.AuditoriaService;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.tenant.TenantContext;
import br.com.cadeiralivre.api.usuario.DonoBarbearia;
import br.com.cadeiralivre.api.usuario.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class ContaDoPainelServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private ContaDoPainelService contaService;

    private final UUID barbeariaId = UUID.randomUUID();
    private final DonoBarbearia gabriel = new DonoBarbearia();

    @BeforeEach
    void logarComoDono() {
        gabriel.setId(UUID.randomUUID());
        gabriel.setNome("Gabriel Naoki");
        gabriel.setSenhaHash("hash-antigo");
        TenantContext.set(barbeariaId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                gabriel.getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_DONO"))));
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void trocarSenhaComASenhaAtualCertaGravaONovoHash() {
        when(usuarioRepository.findById(gabriel.getId())).thenReturn(Optional.of(gabriel));
        when(passwordEncoder.matches("12345678", "hash-antigo")).thenReturn(true);
        when(passwordEncoder.encode("senhaNova123")).thenReturn("hash-novo");

        contaService.trocarSenha("12345678", "senhaNova123");

        assertEquals("hash-novo", gabriel.getSenhaHash());
    }

    @Test
    void senhaAtualErradaDevolve422ENaoTroca() {
        when(usuarioRepository.findById(gabriel.getId())).thenReturn(Optional.of(gabriel));
        when(passwordEncoder.matches("errada", "hash-antigo")).thenReturn(false);

        ApiException erro = assertThrows(ApiException.class, () -> contaService.trocarSenha("errada", "senhaNova123"));

        assertEquals("A senha atual está incorreta.", erro.getMessage());
        assertEquals("hash-antigo", gabriel.getSenhaHash());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void apagarDadosApagaPrimeiroOQueDependeEOcultaOPortal() {
        senhaCerta();

        contaService.apagarDados("12345678");

        ArgumentCaptor<String> sqls = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sqls.capture(), eq(barbeariaId));
        List<String> ordem = sqls.getAllValues();
        // as notificações e avaliações saem antes dos agendamentos; os vínculos, antes dos combos e serviços
        assertTrue(ordem.indexOf(ordem.stream().filter(s -> s.startsWith("delete from avaliacao")).findFirst().orElseThrow())
                < ordem.indexOf("delete from agendamento where barbearia_id = ?"));
        assertTrue(ordem.indexOf(ordem.stream().filter(s -> s.startsWith("delete from combo_servico")).findFirst().orElseThrow())
                < ordem.indexOf("delete from combo where barbearia_id = ?"));
        assertEquals("update barbearia set publico = false where id = ?", ordem.get(ordem.size() - 1));
        verify(jdbc, never()).update(eq("delete from barbearia where id = ?"), any(Object[].class));
        verify(auditoriaService).registrar(eq(AcaoAuditoria.PORTAL_OCULTADO), anyString());
    }

    @Test
    void encerrarContaApagaOsDadosAEquipeODonoEPorUltimoABarbearia() {
        senhaCerta();

        contaService.encerrarConta("12345678");

        InOrder ordem = inOrder(jdbc);
        ordem.verify(jdbc).update("delete from agendamento where barbearia_id = ?", barbeariaId);
        ordem.verify(jdbc).update("delete from profissional where barbearia_id = ?", barbeariaId);
        // as contas dos funcionários saem junto com a do dono
        ordem.verify(jdbc).update("delete from usuario where id in (select id from funcionario where barbearia_id = ?)", barbeariaId);
        ordem.verify(jdbc).update("delete from usuario where id in (select id from dono_barbearia where barbearia_id = ?)", barbeariaId);
        ordem.verify(jdbc).update("delete from barbearia where id = ?", barbeariaId);
    }

    @Test
    void apagarDadosComSenhaErradaNaoApagaNada() {
        when(usuarioRepository.findById(gabriel.getId())).thenReturn(Optional.of(gabriel));
        when(passwordEncoder.matches("errada", "hash-antigo")).thenReturn(false);

        ApiException erro = assertThrows(ApiException.class, () -> contaService.apagarDados("errada"));

        assertEquals(422, erro.getStatus().value());
        assertEquals("A senha está incorreta.", erro.getMessage());
        verifyNoInteractions(jdbc, auditoriaService);
    }

    @Test
    void encerrarContaComSenhaErradaNaoApagaNada() {
        when(usuarioRepository.findById(gabriel.getId())).thenReturn(Optional.of(gabriel));
        when(passwordEncoder.matches("errada", "hash-antigo")).thenReturn(false);

        ApiException erro = assertThrows(ApiException.class, () -> contaService.encerrarConta("errada"));

        assertEquals("A senha está incorreta.", erro.getMessage());
        verifyNoInteractions(jdbc, auditoriaService);
    }

    // a senha digitada confere com o hash da conta logada
    private void senhaCerta() {
        when(usuarioRepository.findById(gabriel.getId())).thenReturn(Optional.of(gabriel));
        when(passwordEncoder.matches("12345678", "hash-antigo")).thenReturn(true);
    }
}
