-- O histórico do cliente busca os agendamentos pelo cliente_id, do mais recente para o mais antigo.
CREATE INDEX idx_agendamento_cliente_data ON agendamento (cliente_id, data);
