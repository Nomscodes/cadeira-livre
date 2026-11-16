package br.com.cadeiralivre.api.common.cripto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class CriptografiaTest {

    // 32 bytes em Base64, só para os testes
    private static final String CHAVE = "Y2hhdmUtZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW9rISE=";

    private final Criptografia criptografia = new Criptografia(CHAVE);

    @Test
    void descriptografarDevolveOTextoOriginal() {
        String criptografado = criptografia.criptografar("cliente@teste.com");

        assertEquals("cliente@teste.com", criptografia.descriptografar(criptografado));
    }

    @Test
    void textoCriptografadoNaoMostraOTextoOriginal() {
        String criptografado = criptografia.criptografar("cliente@teste.com");

        assertFalse(criptografado.contains("cliente"));
    }

    @Test
    void mesmoTextoGeraCriptografiasDiferentes() {
        // por causa do IV aleatório, quem vê o banco não descobre que dois usuários têm o mesmo telefone
        assertNotEquals(criptografia.criptografar("62999999999"), criptografia.criptografar("62999999999"));
    }

    @Test
    void textoAlteradoNoBancoNaoDescriptografa() {
        byte[] dados = Base64.getDecoder().decode(criptografia.criptografar("cliente@teste.com"));
        dados[dados.length - 1] ^= 1; // muda um bit
        String alterado = Base64.getEncoder().encodeToString(dados);

        assertThrows(IllegalStateException.class, () -> criptografia.descriptografar(alterado));
    }

    @Test
    void hashDoMesmoTextoEhSempreIgual() {
        assertEquals(criptografia.hash("cliente@teste.com"), criptografia.hash("cliente@teste.com"));
        assertNotEquals(criptografia.hash("cliente@teste.com"), criptografia.hash("outro@teste.com"));
    }

    @Test
    void valorNuloContinuaNulo() {
        assertNull(criptografia.criptografar(null));
        assertNull(criptografia.descriptografar(null));
    }

    @Test
    void chaveComTamanhoErradoEhRecusada() {
        String chaveDe16Bytes = "c28tMTYtYnl0ZXMtYXF1aQ==";

        assertThrows(IllegalStateException.class, () -> new Criptografia(chaveDe16Bytes));
    }
}
