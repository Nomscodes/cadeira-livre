-- A agenda passa a ser por profissional: cada agendamento é feito com um profissional,
-- e a regra de um agendamento aprovado por horário (RF21) vale para a cadeira de cada um.
-- A coluna aceita vazio porque os agendamentos antigos, de teste, não têm profissional.
ALTER TABLE agendamento ADD COLUMN profissional_id uuid REFERENCES profissional (id);
