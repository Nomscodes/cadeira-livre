package br.com.cadeiralivre.api.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Escreve um erro no mesmo formato do ApiExceptionHandler: { "message": ..., "problemas": [] }.
 * Usado pelo Spring Security, que barra a requisição antes dela chegar no controller.
 * Só use com mensagens fixas, escritas no código (sem aspas).
 */
public final class RespostaErroJson {

    private RespostaErroJson() {
    }

    public static void escrever(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + mensagem + "\",\"problemas\":[]}");
    }
}
