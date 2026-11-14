-- Serviço em destaque (aparece no início do portal) e combos: um pacote de 2 ou mais serviços com preço próprio,
-- feito pelos profissionais escolhidos. A duração do combo é a soma da duração dos serviços.

-- os serviços nascem em destaque, como no front
ALTER TABLE servico ADD COLUMN destaque boolean NOT NULL DEFAULT true;

CREATE TABLE combo (
    id           uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    barbearia_id uuid          NOT NULL REFERENCES barbearia (id),
    nome         varchar(120)  NOT NULL,
    preco        numeric(10,2) NOT NULL CHECK (preco > 0),
    -- exclusão lógica: o combo some do painel e do portal, mas continua nos agendamentos antigos
    ativo        boolean       NOT NULL DEFAULT true
);
CREATE INDEX idx_combo_barbearia ON combo (barbearia_id);

-- serviços que fazem parte do combo
CREATE TABLE combo_servico (
    combo_id   uuid NOT NULL REFERENCES combo (id),
    servico_id uuid NOT NULL REFERENCES servico (id),
    PRIMARY KEY (combo_id, servico_id)
);

-- profissionais que fazem o combo
CREATE TABLE combo_profissional (
    combo_id        uuid NOT NULL REFERENCES combo (id),
    profissional_id uuid NOT NULL REFERENCES profissional (id),
    PRIMARY KEY (combo_id, profissional_id)
);
