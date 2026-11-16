-- RNF03: o telefone que o cliente informa no agendamento passa a ser gravado criptografado (AES-GCM),
-- como já acontece com o e-mail e o telefone do usuário (V2).
-- O texto criptografado é maior que o telefone, por isso a coluna aumenta para 512 caracteres.
--
-- O sistema ainda não está em produção: os agendamentos de teste, gravados com o telefone em texto puro,
-- são apagados, porque depois não seria possível descriptografá-los (mesmo critério da V7).
DELETE FROM avaliacao;
DELETE FROM agendamento;
ALTER TABLE agendamento ALTER COLUMN cliente_telefone TYPE varchar(512);
