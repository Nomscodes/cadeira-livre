-- RNF03: e-mail e telefone passam a ser gravados criptografados (AES).
-- Como o texto criptografado muda a cada gravação, a busca no login e a regra de
-- e-mail único (RF03) passam a usar a coluna email_hash.
-- RNF02: data em que o usuário aceitou os termos de uso e a política de privacidade.
--
-- Esta migration parte do princípio de que ainda não há usuários reais no banco
-- (o sistema não está em produção). Em um banco local com dados de teste,
-- recrie o banco antes: docker compose down -v && docker compose up -d

ALTER TABLE usuario DROP CONSTRAINT usuario_email_key;
ALTER TABLE usuario ALTER COLUMN email TYPE varchar(512);
ALTER TABLE usuario ALTER COLUMN telefone TYPE varchar(512);

ALTER TABLE usuario ADD COLUMN email_hash varchar(64) NOT NULL;
ALTER TABLE usuario ADD CONSTRAINT usuario_email_hash_key UNIQUE (email_hash);

ALTER TABLE usuario ADD COLUMN aceite_termos_em timestamptz NOT NULL;
