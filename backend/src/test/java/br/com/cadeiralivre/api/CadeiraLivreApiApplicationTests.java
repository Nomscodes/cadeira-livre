package br.com.cadeiralivre.api;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Sobe a aplicação inteira e precisa do PostgreSQL e das variáveis de ambiente. Os testes unitários ficam nos pacotes de cada funcionalidade.")
@SpringBootTest
class CadeiraLivreApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
