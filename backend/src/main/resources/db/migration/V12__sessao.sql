-- RF05 (logout e encerramento de sessão): cada login abre uma sessão e o token leva o id dela.
-- No logout a sessão ganha a data de encerramento e o token para de valer na hora, mesmo antes
-- de completar as 24 horas. Se o usuário excluir a conta, as sessões dele são apagadas junto.
CREATE TABLE sessao (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id   uuid        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    criada_em    timestamptz NOT NULL DEFAULT now(),
    -- vence junto com o token
    expira_em    timestamptz NOT NULL,
    -- preenchida no logout
    encerrada_em timestamptz
);

CREATE INDEX idx_sessao_usuario ON sessao (usuario_id);
-- usado pela limpeza diária das sessões vencidas
CREATE INDEX idx_sessao_expira_em ON sessao (expira_em);
