package br.com.cadeiralivre.api.agendamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import br.com.cadeiralivre.api.common.cripto.Criptografia;
import br.com.cadeiralivre.api.common.cripto.CriptografiaConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;

/** RNF03: o telefone que o cliente informa no agendamento não pode ficar legível no banco. */
class AgendamentoCriptografiaTest {

    // 32 bytes em Base64, só para os testes
    private static final String CHAVE = "Y2hhdmUtZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW9rISE=";

    @Test
    void telefoneDoClienteUsaOConversorDeCriptografia() throws Exception {
        // se alguém tirar o @Convert do campo, o telefone volta a ser gravado em texto puro
        Convert convert = Agendamento.class.getDeclaredField("clienteTelefone").getAnnotation(Convert.class);

        assertNotNull(convert, "o campo clienteTelefone precisa do @Convert (RNF03)");
        assertEquals(CriptografiaConverter.class, convert.converter());
    }

    @Test
    void telefoneComOTamanhoMaximoCabeNaColunaDepoisDeCriptografado() throws Exception {
        // o formulário aceita até 20 caracteres (@Size(max = 20) no AgendamentoRequest)
        String telefoneMaisLongo = "+55 (62) 99999-99999";
        String criptografado = new Criptografia(CHAVE).criptografar(telefoneMaisLongo);

        Field campo = Agendamento.class.getDeclaredField("clienteTelefone");
        int tamanhoDaColuna = campo.getAnnotation(Column.class).length();

        assertEquals(20, telefoneMaisLongo.length());
        assertTrue(criptografado.length() <= tamanhoDaColuna,
                "o telefone criptografado tem " + criptografado.length() + " caracteres e a coluna só " + tamanhoDaColuna);
    }
}
