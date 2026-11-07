package br.com.cadeiralivre.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class RespostaErroJsonTest {

    @Test
    void escreveOErroNoMesmoFormatoDoTratadorDeErros() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        RespostaErroJson.escrever(response, 401, "Faça login para continuar.");

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("UTF-8", response.getCharacterEncoding());
        // mesmo formato do ErroResponse: o front lê sempre o "message"
        assertEquals("{\"message\":\"Faça login para continuar.\",\"problemas\":[]}", response.getContentAsString());
    }
}
