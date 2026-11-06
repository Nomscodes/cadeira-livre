-- A partir de agora todo agendamento é feito com um profissional (o cliente escolhe com quem agendar).
-- O sistema ainda não está em produção: os agendamentos de teste sem profissional são apagados
-- para a coluna poder virar obrigatória.
DELETE FROM avaliacao WHERE agendamento_id IN (SELECT id FROM agendamento WHERE profissional_id IS NULL);
DELETE FROM agendamento WHERE profissional_id IS NULL;
ALTER TABLE agendamento ALTER COLUMN profissional_id SET NOT NULL;

-- a regra de um agendamento aprovado por horário (RF21) procura por profissional e data
CREATE INDEX idx_agendamento_profissional_data ON agendamento (profissional_id, data);
