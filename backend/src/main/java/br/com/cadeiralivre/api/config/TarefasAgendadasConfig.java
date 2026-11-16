package br.com.cadeiralivre.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as tarefas automáticas (@Scheduled), como a que cria os lembretes. */
@Configuration
@EnableScheduling
public class TarefasAgendadasConfig {
}
