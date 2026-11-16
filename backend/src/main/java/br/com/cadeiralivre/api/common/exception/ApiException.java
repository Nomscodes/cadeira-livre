package br.com.cadeiralivre.api.common.exception;

import java.util.List;

import org.springframework.http.HttpStatusCode;

/**
 * Erro de negócio com status HTTP. Lançado nos services e traduzido pelo ApiExceptionHandler.
 * Pode levar uma lista de problemas (ex.: o que falta para o portal poder ser publicado).
 */
public class ApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final List<String> problemas;

    public ApiException(int status, String message) {
        this(status, message, List.of());
    }

    public ApiException(int status, String message, List<String> problemas) {
        super(message);
        this.status = HttpStatusCode.valueOf(status);
        this.problemas = List.copyOf(problemas);
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public List<String> getProblemas() {
        return problemas;
    }

    public static ApiException naoAutorizado(String m) { return new ApiException(401, m); }
    public static ApiException proibido(String m)      { return new ApiException(403, m); }
    public static ApiException naoEncontrado(String m) { return new ApiException(404, m); }
    public static ApiException conflito(String m)      { return new ApiException(409, m); }
    public static ApiException invalido(String m)      { return new ApiException(422, m); }

    public static ApiException invalido(String m, List<String> problemas) { return new ApiException(422, m, problemas); }
}
