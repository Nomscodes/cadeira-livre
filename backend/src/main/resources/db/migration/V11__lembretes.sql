-- Lembretes automáticos (RF03): cada lembrete é criado uma vez só por agendamento, mesmo que a
-- tarefa automática rode de novo ou que duas rodadas aconteçam ao mesmo tempo.
CREATE UNIQUE INDEX uk_notificacao_lembrete ON notificacao (agendamento_id, tipo)
    WHERE tipo IN ('LEMBRETE_DONO', 'LEMBRETE_CLIENTE');
