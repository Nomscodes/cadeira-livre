-- Agendamento com itens, como o front faz: o cliente escolhe um ou mais serviços ou combos e, para cada um, o
-- profissional. A duração do agendamento é a soma dos itens e o valor é a soma dos preços.
-- O agendamento continua guardando um serviço e um profissional "principais" (os do primeiro item), que a agenda,
-- as notificações e a regra do RF21 já usam.
CREATE TABLE agendamento_item (
    id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    agendamento_id  uuid          NOT NULL REFERENCES agendamento (id) ON DELETE CASCADE,
    -- o item é um serviço OU um combo (nunca os dois)
    servico_id      uuid          REFERENCES servico (id),
    combo_id        uuid          REFERENCES combo (id),
    profissional_id uuid          NOT NULL REFERENCES profissional (id),
    -- nome, preço e duração do momento em que o cliente agendou (o salão pode mudar o preço depois)
    nome            varchar(120)  NOT NULL,
    preco           numeric(10,2) NOT NULL,
    duracao_minutos integer       NOT NULL,
    ordem           integer       NOT NULL,
    CONSTRAINT ck_item_servico_ou_combo CHECK ((servico_id IS NULL) <> (combo_id IS NULL))
);
CREATE INDEX idx_agendamento_item_agendamento ON agendamento_item (agendamento_id);
CREATE INDEX idx_agendamento_item_profissional ON agendamento_item (profissional_id);

-- valor total do agendamento (soma dos itens)
ALTER TABLE agendamento ADD COLUMN valor numeric(10,2);

-- os agendamentos que já existem viram um item só, com o serviço e o profissional deles
INSERT INTO agendamento_item (agendamento_id, servico_id, profissional_id, nome, preco, duracao_minutos, ordem)
SELECT a.id, a.servico_id, a.profissional_id, s.nome, s.preco,
       (EXTRACT(EPOCH FROM (a.hora_fim - a.hora_inicio)) / 60)::integer, 0
FROM agendamento a
JOIN servico s ON s.id = a.servico_id;

UPDATE agendamento a SET valor = s.preco FROM servico s WHERE s.id = a.servico_id;

-- profissionais que ainda não têm horário de trabalho (como o do dono, criado no cadastro) ganham a semana padrão:
-- segunda a sexta das 9h às 18h, sábado e domingo de folga
INSERT INTO profissional_horario (profissional_id, dia_semana, hora_inicio, hora_fim, ativo)
SELECT p.id, d.dia, TIME '09:00', TIME '18:00', d.dia NOT IN ('SATURDAY', 'SUNDAY')
FROM profissional p
CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'), ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) AS d (dia)
WHERE NOT EXISTS (SELECT 1 FROM profissional_horario h WHERE h.profissional_id = p.id);
