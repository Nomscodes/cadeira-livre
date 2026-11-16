-- RF20: bloqueio que vale só para um profissional (folga).
-- profissional_id vazio = o bloqueio vale para a barbearia toda (feriado, manutenção).
ALTER TABLE bloqueio_horario ADD COLUMN profissional_id uuid REFERENCES profissional (id);

-- horas vazias = dia inteiro; preenchidas = as duas juntas e o início antes do fim
ALTER TABLE bloqueio_horario ADD CONSTRAINT ck_bloqueio_horas CHECK (
    (hora_inicio IS NULL AND hora_fim IS NULL)
    OR (hora_inicio IS NOT NULL AND hora_fim IS NOT NULL AND hora_inicio < hora_fim)
);

-- a disponibilidade vai buscar os bloqueios por barbearia e data
CREATE INDEX idx_bloqueio_barbearia_data ON bloqueio_horario (barbearia_id, data);
