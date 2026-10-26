package br.com.cadeiralivre.api.common.exception;

import org.springframework.http.HttpStatusCode;

/** Erro de negócio com status HTTP. Lançado nos services e traduzido pelo ApiExceptionHandler. */
public class ApiException extends RuntimeException {

    private final HttpStatusCode status;

    public ApiException(int status, String message) {
        super(message);
        this.status = HttpStatusCode.valueOf(status);
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public static ApiException naoAutorizado(String m) { return new ApiException(401, m); }
    public static ApiException proibido(String m)      { return new ApiException(403, m); }
    public static ApiException naoEncontrado(String m) { return new ApiException(404, m); }
    public static ApiException conflito(String m)      { return new ApiException(409, m); }
    public static ApiException invalido(String m)      { return new ApiException(422, m); }
}
