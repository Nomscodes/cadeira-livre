-- Schema inicial (Parte 1). Multi-tenant por coluna: tabelas de negócio carregam barbearia_id.

CREATE TABLE barbearia (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug          varchar(80)  NOT NULL UNIQUE,
    nome_fantasia varchar(120) NOT NULL,
    razao_social  varchar(160) NOT NULL,
    documento     varchar(14)  NOT NULL,
    logo_url      text,
    cor_primaria  varchar(7)   NOT NULL DEFAULT '#9B1C2E',
    endereco      varchar(255),
    telefone      varchar(20),
    publico       boolean      NOT NULL DEFAULT false,
    criado_em     timestamptz  NOT NULL DEFAULT now()
);

-- Herança JOINED: usuario (comum) + cliente / dono_barbearia (específicos)
CREATE TABLE usuario (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nome       varchar(120) NOT NULL,
    email      varchar(160) NOT NULL UNIQUE,
    senha_hash varchar(100) NOT NULL,
    telefone   varchar(20),
    papel      varchar(20)  NOT NULL,
    criado_em  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE cliente (
    id uuid PRIMARY KEY REFERENCES usuario (id) ON DELETE CASCADE
);

CREATE TABLE dono_barbearia (
    id           uuid PRIMARY KEY REFERENCES usuario (id) ON DELETE CASCADE,
    barbearia_id uuid NOT NULL REFERENCES barbearia (id)
);

CREATE TABLE servico (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    barbearia_id     uuid          NOT NULL REFERENCES barbearia (id),
    nome             varchar(120)  NOT NULL,
    descricao        varchar(500),
    preco            numeric(10,2) NOT NULL,
    duracao_minutos  integer       NOT NULL,
    ativo            boolean       NOT NULL DEFAULT true
);

CREATE TABLE horario_funcionamento (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    barbearia_id    uuid        NOT NULL REFERENCES barbearia (id),
    dia_semana      varchar(10) NOT NULL,
    hora_abertura   time        NOT NULL,
    hora_fechamento time        NOT NULL,
    ativo           boolean     NOT NULL DEFAULT true,
    UNIQUE (barbearia_id, dia_semana)
);

CREATE TABLE bloqueio_horario (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    barbearia_id uuid NOT NULL REFERENCES barbearia (id),
    data         date NOT NULL,
    hora_inicio  time,            -- nulo = dia inteiro
    hora_fim     time,
    motivo       varchar(160)
);

CREATE TABLE agendamento (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    barbearia_id     uuid         NOT NULL REFERENCES barbearia (id),
    servico_id       uuid         NOT NULL REFERENCES servico (id),
    cliente_id       uuid         REFERENCES cliente (id),
    cliente_nome     varchar(120) NOT NULL,
    cliente_telefone varchar(20)  NOT NULL,
    data             date         NOT NULL,
    hora_inicio      time         NOT NULL,
    hora_fim         time         NOT NULL,
    status           varchar(20)  NOT NULL,
    criado_em        timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_agendamento_barbearia_data ON agendamento (barbearia_id, data);

CREATE TABLE avaliacao (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    agendamento_id uuid     NOT NULL UNIQUE REFERENCES agendamento (id),
    nota           smallint NOT NULL CHECK (nota BETWEEN 1 AND 5),
    comentario     text,
    criado_em      timestamptz NOT NULL DEFAULT now()
);
