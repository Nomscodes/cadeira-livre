-- Profissional completo, como o front usa: a conta que entra no sistema como este profissional (o dono ou,
-- depois, um funcionário), o nome social mostrado aos clientes, se ele atende clientes, se é administrador
-- da barbearia e se os avisos de agendamento vão para o WhatsApp dele.
ALTER TABLE profissional
    -- vazio = profissional sem login
    ADD COLUMN usuario_id    uuid        UNIQUE REFERENCES usuario (id) ON DELETE SET NULL,
    ADD COLUMN nome_social   varchar(60),
    -- false = não atende clientes (ex.: dono de salão com equipe que só gerencia)
    ADD COLUMN atende        boolean     NOT NULL DEFAULT true,
    ADD COLUMN administrador boolean     NOT NULL DEFAULT false,
    ADD COLUMN encaminhar    boolean     NOT NULL DEFAULT false;

COMMENT ON COLUMN profissional.telefone IS 'telefone/WhatsApp do profissional (criptografado, RNF03)';

-- Os donos que já existem entram como profissional administrador da própria barbearia. Ficam sem atender
-- clientes para não mudar a agenda que já existe; o dono liga o "atende" no painel se quiser.
INSERT INTO profissional (barbearia_id, nome, nome_social, atende, administrador, usuario_id)
SELECT d.barbearia_id, u.nome, split_part(u.nome, ' ', 1), false, true, u.id
FROM dono_barbearia d
JOIN usuario u ON u.id = d.id;
