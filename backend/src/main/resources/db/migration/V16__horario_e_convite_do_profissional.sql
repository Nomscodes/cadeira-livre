-- Horário de trabalho de cada profissional (o front mostra e edita o horário de cada um) e o código de convite
-- que o funcionário usa para criar o próprio login.
CREATE TABLE profissional_horario (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    profissional_id uuid        NOT NULL REFERENCES profissional (id) ON DELETE CASCADE,
    dia_semana      varchar(10) NOT NULL,
    hora_inicio     time        NOT NULL,
    hora_fim        time        NOT NULL,
    ativo           boolean     NOT NULL DEFAULT true,
    UNIQUE (profissional_id, dia_semana),
    CONSTRAINT ck_profissional_horario_horas CHECK (hora_inicio < hora_fim)
);

-- código de 6 letras e números gerado no cadastro do profissional; some quando o funcionário cria o login
ALTER TABLE profissional ADD COLUMN codigo_convite varchar(8) UNIQUE;

-- os profissionais que já existem herdam o horário de funcionamento do salão
INSERT INTO profissional_horario (profissional_id, dia_semana, hora_inicio, hora_fim, ativo)
SELECT p.id, h.dia_semana, h.hora_abertura, h.hora_fechamento, h.ativo
FROM profissional p
JOIN horario_funcionamento h ON h.barbearia_id = p.barbearia_id;
