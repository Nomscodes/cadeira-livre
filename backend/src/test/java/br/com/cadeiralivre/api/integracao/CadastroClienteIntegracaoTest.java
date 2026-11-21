package br.com.cadeiralivre.api.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import br.com.cadeiralivre.api.auth.dto.SessaoResponse;
import br.com.cadeiralivre.api.usuario.dto.ContaResponse;

/**
 * C-01: testa o cadastro do cliente com as partes reais trabalhando juntas, sem simular nada:
 * API numa porta real, PostgreSQL no Docker, Flyway criando as tabelas, Spring Security e criptografia.
 * Sem Docker, o teste é pulado e o resto da suíte continua normal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class CadastroClienteIntegracaoTest {

    // mesma imagem do docker-compose.yml
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Value("${local.server.port}")
    int porta;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // aponta a API para o banco do container e usa chaves geradas só para este teste
    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", postgres::getJdbcUrl);
        registro.add("spring.datasource.username", postgres::getUsername);
        registro.add("spring.datasource.password", postgres::getPassword);
        registro.add("app.jwt.secret", () -> gerarChaveBase64(48));
        registro.add("app.cripto.chave", () -> gerarChaveBase64(32));
    }

    @Test
    void cadastraEntraConsultaAContaEGravaOsDadosProtegidos() {
        RestClient api = RestClient.create("http://localhost:" + porta + "/api");
        String nome = "Gabriel Naoki";
        String email = "integracao+" + UUID.randomUUID() + "@teste.com";
        String telefone = "(62) 98100-0001";
        String senha = "senha-de-teste-123";

        // 1. cadastra o cliente pela API
        ResponseEntity<SessaoResponse> cadastro = api.post()
                .uri("/auth/cadastro/cliente")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nome", nome, "email", email, "telefone", telefone,
                        "senha", senha, "aceitouTermos", true))
                .retrieve()
                .toEntity(SessaoResponse.class);

        assertThat(cadastro.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cadastro.getBody().token()).isNotBlank();
        assertThat(cadastro.getBody().usuario().papel()).isEqualTo("CLIENTE");
        assertThat(cadastro.getBody().usuario().nome()).isEqualTo(nome);
        UUID idDoUsuario = cadastro.getBody().usuario().id();

        // 2. entra com o mesmo e-mail e senha
        ResponseEntity<SessaoResponse> login = api.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "senha", senha))
                .retrieve()
                .toEntity(SessaoResponse.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().token()).isNotBlank();

        // 3. com o token do login, a API devolve os dados em texto normal
        ContaResponse conta = api.get()
                .uri("/cliente/conta")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.getBody().token())
                .retrieve()
                .body(ContaResponse.class);

        assertThat(conta.email()).isEqualTo(email);
        assertThat(conta.telefone()).isEqualTo(telefone);
        assertThat(conta.aceiteTermosEm()).isNotNull();

        // 4. direto no banco, o e-mail está cifrado e a senha está em BCrypt
        Map<String, Object> linha = jdbcTemplate.queryForMap(
                "select email, email_hash, senha_hash from usuario where id = ?", idDoUsuario);
        String emailNoBanco = (String) linha.get("email");
        String emailHash = (String) linha.get("email_hash");
        String senhaHash = (String) linha.get("senha_hash");

        assertThat(emailNoBanco).isNotEqualTo(email).doesNotContain("@");
        assertThat(emailHash).isNotBlank();
        assertThat(senhaHash).startsWith("$2").isNotEqualTo(senha);
    }

    // gera bytes aleatórios em Base64: serve para a chave do JWT e para a da criptografia
    private static String gerarChaveBase64(int tamanhoEmBytes) {
        byte[] chave = new byte[tamanhoEmBytes];
        new SecureRandom().nextBytes(chave);
        return Base64.getEncoder().encodeToString(chave);
    }
}
