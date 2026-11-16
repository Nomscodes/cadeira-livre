-- RNF09: registro das ações críticas, com data/hora e usuário.
-- Sem chave estrangeira de propósito: o registro continua existindo
-- mesmo se o usuário ou a barbearia forem excluídos (LGPD, direito ao esquecimento).
CREATE TABLE log_auditoria (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    data_hora    timestamptz  NOT NULL,
    usuario_id   uuid,
    barbearia_id uuid,
    acao         varchar(40)  NOT NULL,
    detalhe      varchar(500)
);

CREATE INDEX idx_log_auditoria_barbearia ON log_auditoria (barbearia_id, data_hora);
