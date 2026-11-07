package br.com.cadeiralivre.api.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Chama cada método do tratador com a exceção que o Spring lançaria
 * e confere o status HTTP e a mensagem que o front mostra para o usuário.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void erroDeNegocioUsaOStatusEAMensagemDoService() {
        ResponseEntity<ErroResponse> resposta = handler.negocio(
                ApiException.conflito("Esse horário não está disponível. Escolha outro horário."));

        confere(resposta, 409, "Esse horário não está disponível. Escolha outro horário.");
    }

    @Test
    void validacaoListaCadaCampoComProblema() {
        BeanPropertyBindingResult erros = new BeanPropertyBindingResult(new Object(), "servicoRequest");
        erros.addError(new FieldError("servicoRequest", "nome", "não deve estar em branco"));
        erros.addError(new FieldError("servicoRequest", "preco", "deve ser maior que 0.0"));

        ResponseEntity<ErroResponse> resposta = handler.validacao(
                new MethodArgumentNotValidException(mock(MethodParameter.class), erros));

        assertEquals(422, resposta.getStatusCode().value());
        assertEquals("Dados inválidos.", resposta.getBody().message());
        assertEquals(List.of("nome: não deve estar em branco", "preco: deve ser maior que 0.0"),
                resposta.getBody().problemas());
    }

    @Test
    void registroDuplicadoNoBancoViraConflito() {
        ResponseEntity<ErroResponse> resposta = handler.integridade(
                new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        confere(resposta, 409, "Operação não permitida: registro em uso ou duplicado.");
    }

    @Test
    void jsonQuebradoViraErro400() {
        ResponseEntity<ErroResponse> resposta = handler.corpoInvalido(
                new HttpMessageNotReadableException("JSON parse error", mock(HttpInputMessage.class)));

        confere(resposta, 400, "Não foi possível ler os dados enviados. Confira o formato do JSON, das datas e dos horários.");
    }

    @Test
    void idQueNaoEhUuidViraErro400ComONomeDoParametro() {
        // ex.: /painel/servicos/abc
        ResponseEntity<ErroResponse> resposta = handler.parametroInvalido(
                new MethodArgumentTypeMismatchException("abc", UUID.class, "id", mock(MethodParameter.class), null));

        confere(resposta, 400, "Valor inválido para o parâmetro 'id'.");
    }

    @Test
    void parametroObrigatorioFaltandoViraErro400() {
        // ex.: /saloes/barbearia-teste/disponibilidade sem o servicoId
        ResponseEntity<ErroResponse> resposta = handler.parametroFaltando(
                new MissingServletRequestParameterException("servicoId", "UUID"));

        confere(resposta, 400, "Parâmetro obrigatório não informado: 'servicoId'.");
    }

    @Test
    void enderecoQueNaoExisteViraErro404() {
        ResponseEntity<ErroResponse> resposta = handler.rotaNaoEncontrada(
                new NoResourceFoundException(HttpMethod.GET, "/api/nao-existe", "nao-existe"));

        confere(resposta, 404, "Endereço não encontrado.");
    }

    @Test
    void metodoErradoViraErro405() {
        ResponseEntity<ErroResponse> resposta = handler.metodoNaoPermitido(
                new HttpRequestMethodNotSupportedException("DELETE"));

        confere(resposta, 405, "O método DELETE não é aceito neste endereço.");
    }

    @Test
    void corpoQueNaoEhJsonViraErro415() {
        ResponseEntity<ErroResponse> resposta = handler.formatoNaoSuportado(
                new HttpMediaTypeNotSupportedException("Content-Type 'text/plain' is not supported"));

        confere(resposta, 415, "Envie os dados em JSON (Content-Type: application/json).");
    }

    @Test
    void erroInesperadoViraErro500SemMostrarODetalhe() {
        // o detalhe do erro vai só para o log; o usuário recebe uma mensagem genérica
        ResponseEntity<ErroResponse> resposta = handler.erroInesperado(
                new IllegalStateException("falha ao conectar no banco cadeira_livre"));

        confere(resposta, 500, "Erro interno no servidor. Tente novamente mais tarde.");
        assertFalse(resposta.getBody().message().contains("cadeira_livre"));
    }

    private void confere(ResponseEntity<ErroResponse> resposta, int status, String mensagem) {
        assertEquals(status, resposta.getStatusCode().value());
        assertEquals(mensagem, resposta.getBody().message());
        assertTrue(resposta.getBody().problemas().isEmpty());
    }
}
