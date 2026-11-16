-- Notificações dentro do sistema (RF03 e RF04 dos requisitos de alto nível): avisos para o dono da
-- barbearia e para o cliente. A notificação guarda só o tipo e o agendamento; o texto é montado na hora
-- de mostrar, com os dados do agendamento. Assim, se o cliente excluir a conta, o nome dele não fica
-- guardado aqui (LGPD).
CREATE TABLE notificacao (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo           varchar(30) NOT NULL,
    agendamento_id uuid        NOT NULL REFERENCES agendamento (id),
    -- para quem é: o dono da barbearia OU o cliente (nunca os dois)
    barbearia_id   uuid REFERENCES barbearia (id),
    cliente_id     uuid REFERENCES cliente (id) ON DELETE CASCADE,
    criada_em      timestamptz NOT NULL DEFAULT now(),
    -- quando o destinatário abriu a notificação
    lida_em        timestamptz,
    CONSTRAINT ck_notificacao_um_destino CHECK ((barbearia_id IS NULL) <> (cliente_id IS NULL))
);

CREATE INDEX idx_notificacao_barbearia ON notificacao (barbearia_id, criada_em);
CREATE INDEX idx_notificacao_cliente ON notificacao (cliente_id, criada_em);
