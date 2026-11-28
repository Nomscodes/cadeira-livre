-- Duração do descanso do profissional (D8): o administrador escolhe nas Configurações. O descanso continua sendo
-- marcado pela hora na agenda do funcionário, então ele cabe dentro dessa hora (15, 30, 45 ou 60 minutos) e o que
-- sobra volta a ficar livre para o cliente. Os salões que já existem ficam com 60, como era antes.
ALTER TABLE barbearia
    ADD COLUMN descanso_minutos integer NOT NULL DEFAULT 60,
    ADD CONSTRAINT ck_barbearia_descanso_minutos CHECK (descanso_minutos IN (15, 30, 45, 60));
