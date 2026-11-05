-- Profissionais (barbeiros) da barbearia e os serviços que cada um faz.
CREATE TABLE profissional (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    barbearia_id uuid         NOT NULL REFERENCES barbearia (id),
    nome         varchar(120) NOT NULL,
    telefone     varchar(512),   -- gravado criptografado (RNF03)
    ativo        boolean      NOT NULL DEFAULT true
);
CREATE INDEX idx_profissional_barbearia ON profissional (barbearia_id);

-- Um profissional faz vários serviços, e um serviço pode ser feito por vários profissionais.
CREATE TABLE profissional_servico (
    profissional_id uuid NOT NULL REFERENCES profissional (id),
    servico_id      uuid NOT NULL REFERENCES servico (id),
    PRIMARY KEY (profissional_id, servico_id)
);
