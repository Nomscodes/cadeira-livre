-- Recuperação de senha (tela "Recuperar senha" do front): a pessoa informa o e-mail, recebe um código de 6 dígitos
-- e define a nova senha. O código fica guardado só como hash (BCrypt), vale 15 minutos, aceita poucas tentativas
-- e só pode ser usado uma vez. O envio por e-mail está fora do escopo da V7: em desenvolvimento, o código sai no log.
CREATE TABLE recuperacao_senha (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id  uuid        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    codigo_hash varchar(72) NOT NULL,
    criada_em   timestamptz NOT NULL DEFAULT now(),
    expira_em   timestamptz NOT NULL,
    -- cada código errado soma uma; no limite o código para de valer
    tentativas  smallint    NOT NULL DEFAULT 0,
    -- preenchida quando a senha é trocada (o código não vale mais)
    usada_em    timestamptz
);

CREATE INDEX idx_recuperacao_senha_usuario ON recuperacao_senha (usuario_id, criada_em);
