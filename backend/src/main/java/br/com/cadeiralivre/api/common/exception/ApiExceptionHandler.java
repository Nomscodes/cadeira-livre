package br.com.cadeiralivre.api.common.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Ponto único que converte exceções em respostas JSON (fim do fluxo em caso de erro). */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErroResponse> negocio(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErroResponse(e.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> validacao(MethodArgumentNotValidException e) {
        List<String> problemas = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return ResponseEntity.status(422).body(new ErroResponse("Dados inválidos.", problemas));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponse> integridade(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(new ErroResponse("Operação não permitida: registro em uso ou duplicado.", List.of()));
    }

    // JSON quebrado, ou data/hora em formato errado no corpo
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> corpoInvalido(HttpMessageNotReadableException e) {
        return erro(400, "Não foi possível ler os dados enviados. Confira o formato do JSON, das datas e dos horários.");
    }

    // ex.: /painel/servicos/abc, quando o id deveria ser um UUID
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponse> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return erro(400, "Valor inválido para o parâmetro '" + e.getName() + "'.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErroResponse> parametroFaltando(MissingServletRequestParameterException e) {
        return erro(400, "Parâmetro obrigatório não informado: '" + e.getParameterName() + "'.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResponse> rotaNaoEncontrada(NoResourceFoundException e) {
        return erro(404, "Endereço não encontrado.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResponse> metodoNaoPermitido(HttpRequestMethodNotSupportedException e) {
        return erro(405, "O método " + e.getMethod() + " não é aceito neste endereço.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroResponse> formatoNaoSuportado(HttpMediaTypeNotSupportedException e) {
        return erro(415, "Envie os dados em JSON (Content-Type: application/json).");
    }

    // Qualquer erro que não foi tratado acima. O detalhe vai pro log, nunca pro usuário.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> erroInesperado(Exception e) {
        log.error("Erro inesperado", e);
        return erro(500, "Erro interno no servidor. Tente novamente mais tarde.");
    }

    private ResponseEntity<ErroResponse> erro(int status, String mensagem) {
        return ResponseEntity.status(status).body(new ErroResponse(mensagem, List.of()));
    }
}
