-- Notificações da equipe, como o front usa: o dono manda avisos para os profissionais, e o funcionário manda alertas
-- para o dono. Essas notificações têm texto livre e remetente (as automáticas continuam montando o texto na hora).
ALTER TABLE notificacao
    -- preenchido quando a notificação é para um profissional (o barbearia_id também fica preenchido)
    ADD COLUMN profissional_id uuid REFERENCES profissional (id) ON DELETE CASCADE,
    -- texto livre do aviso ou do alerta (as outras notificações montam o texto com os dados do agendamento)
    ADD COLUMN texto           varchar(500),
    -- quem mandou (nome de quem estava logado)
    ADD COLUMN remetente       varchar(120);

CREATE INDEX idx_notificacao_profissional ON notificacao (profissional_id, criada_em);
