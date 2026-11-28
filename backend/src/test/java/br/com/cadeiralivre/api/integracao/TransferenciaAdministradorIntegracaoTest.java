package br.com.cadeiralivre.api.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import br.com.cadeiralivre.api.auth.dto.SessaoResponse;

/**
 * Transferência do cargo de administrador (RF28 / D9) com o banco de verdade: a troca de papel usa SQL
 * direto nas tabelas da herança (dono_barbearia e funcionario), e só um PostgreSQL real prova que funciona.
 * Sem Docker, o teste é pulado e o resto da suíte continua normal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class TransferenciaAdministradorIntegracaoTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Value("${local.server.port}")
    int porta;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", postgres::getJdbcUrl);
        registro.add("spring.datasource.username", postgres::getUsername);
        registro.add("spring.datasource.password", postgres::getPassword);
        registro.add("app.jwt.secret", () -> gerarChaveBase64(48));
        registro.add("app.cripto.chave", () -> gerarChaveBase64(32));
    }

    @Test
    @SuppressWarnings("unchecked")
    void quemRecebeOCargoVaiParaOPainelEQuemTransferiuVaiParaAAgenda() {
        RestClient api = RestClient.create("http://localhost:" + porta + "/api");
        String emailDono = "dono+" + UUID.randomUUID() + "@teste.com";
        String emailFuncionario = "func+" + UUID.randomUUID() + "@teste.com";
        String senha = "senha-de-teste-123";

        // 1. o dono cria o salão e cadastra o Diego na equipe (recebe o código de convite)
        SessaoResponse dono = api.post().uri("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nome", "Gabriel Naoki", "razaoSocial", "Barbearia Integração Gabriel Naoki",
                        "documento", "12345678901", "email", emailDono, "senha", senha,
                        "aceitouTermos", true, "atuacao", "equipe"))
                .retrieve().body(SessaoResponse.class);
        Map<String, Object> criado = api.post().uri("/painel/profissionais")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dono.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nome", "Diego Santos", "servicoIds", List.of()))
                .retrieve().body(Map.class);
        String convite = (String) criado.get("convite");
        String diegoProfissionalId = (String) ((Map<String, Object>) criado.get("profissional")).get("id");

        // 2. o Diego cria o login com o código e entra como funcionário
        SessaoResponse diego = api.post().uri("/auth/convite").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("codigo", convite, "email", emailFuncionario, "senha", senha, "aceitouTermos", true))
                .retrieve().body(SessaoResponse.class);
        assertThat(diego.usuario().papel()).isEqualTo("FUNCIONARIO");

        // 3. o dono transfere o cargo para o Diego
        HttpStatusCode transferencia = api.post().uri("/painel/configuracoes/transferir-admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dono.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("profissionalId", diegoProfissionalId))
                .exchange((pedido, resposta) -> resposta.getStatusCode());
        assertThat(transferencia.is2xxSuccessful()).isTrue();

        // 4. o token antigo do dono deixou de valer: a sessão foi encerrada
        assertThat(status(api, "/painel/portal", dono.token()).value()).isEqualTo(401);

        // 5. entrando de novo, os papéis estão trocados
        SessaoResponse diegoDeNovo = login(api, emailFuncionario, senha);
        SessaoResponse gabrielDeNovo = login(api, emailDono, senha);
        assertThat(diegoDeNovo.usuario().papel()).isEqualTo("DONO");
        assertThat(gabrielDeNovo.usuario().papel()).isEqualTo("FUNCIONARIO");
        assertThat(diegoDeNovo.usuario().barbeariaId()).isEqualTo(dono.usuario().barbeariaId());
        assertThat(gabrielDeNovo.usuario().barbeariaId()).isEqualTo(dono.usuario().barbeariaId());

        // 6. o Diego abre o painel e o Gabriel abre a própria agenda
        assertThat(status(api, "/painel/portal", diegoDeNovo.token()).value()).isEqualTo(200);
        assertThat(status(api, "/agenda/resumo", gabrielDeNovo.token()).value()).isEqualTo(200);
        assertThat(status(api, "/painel/portal", gabrielDeNovo.token()).value()).isEqualTo(403);

        // 7. no banco, cada conta está só na tabela do papel novo
        UUID diegoId = diego.usuario().id();
        UUID gabrielId = dono.usuario().id();
        assertThat(contar("dono_barbearia", diegoId)).isEqualTo(1);
        assertThat(contar("funcionario", diegoId)).isZero();
        assertThat(contar("funcionario", gabrielId)).isEqualTo(1);
        assertThat(contar("dono_barbearia", gabrielId)).isZero();
    }

    private SessaoResponse login(RestClient api, String email, String senha) {
        return api.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "senha", senha))
                .retrieve().body(SessaoResponse.class);
    }

    private HttpStatusCode status(RestClient api, String rota, String token) {
        return api.get().uri(rota).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange((pedido, resposta) -> resposta.getStatusCode());
    }

    private Integer contar(String tabela, UUID id) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela + " where id = ?", Integer.class, id);
    }

    private static String gerarChaveBase64(int tamanhoEmBytes) {
        byte[] chave = new byte[tamanhoEmBytes];
        new SecureRandom().nextBytes(chave);
        return Base64.getEncoder().encodeToString(chave);
    }
}
