-- Conta do funcionário (herança JOINED, como cliente e dono_barbearia): criada com o código de convite que o dono
-- recebe ao cadastrar o profissional. A ligação com o profissional fica em profissional.usuario_id.
CREATE TABLE funcionario (
    id           uuid PRIMARY KEY REFERENCES usuario (id) ON DELETE CASCADE,
    barbearia_id uuid NOT NULL REFERENCES barbearia (id)
);

CREATE INDEX idx_funcionario_barbearia ON funcionario (barbearia_id);
