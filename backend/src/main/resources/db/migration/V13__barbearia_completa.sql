-- Dados do portal que o front já usa e a barbearia ainda não tinha: cidade e categoria (busca de salões),
-- tema de cor, texto "sobre", redes sociais, imagem de capa, tipo de atendimento, prazo de cancelamento,
-- se os funcionários editam o próprio horário e como o dono atua (autônomo ou salão com equipe).
-- As barbearias que já existem ficam com os valores padrão.
ALTER TABLE barbearia
    -- cidade e UF como o dono escreve ("Goiânia, GO")
    ADD COLUMN cidade                      varchar(120),
    ADD COLUMN categoria                   varchar(20)  NOT NULL DEFAULT 'barbearia',
    -- um dos 14 temas de cor do front; o vermelho escuro tem a mesma cor do padrão antigo (#9B1C2E)
    ADD COLUMN tema_id                     varchar(40)  NOT NULL DEFAULT 'vermelho-escuro',
    ADD COLUMN sobre                       varchar(600),
    ADD COLUMN redes                       varchar(255),
    -- imagem de capa do portal (data URL, igual à logo)
    ADD COLUMN capa_url                    text,
    -- local: o cliente vai até o salão; domicilio: o profissional vai até o cliente
    ADD COLUMN atendimento                 varchar(10)  NOT NULL DEFAULT 'local',
    -- até quantas horas antes do horário o cliente pode cancelar
    ADD COLUMN cancelamento_horas          integer      NOT NULL DEFAULT 4,
    ADD COLUMN funcionarios_editam_horario boolean      NOT NULL DEFAULT false,
    -- autonomo: o dono também atende; equipe: o dono só gerencia
    ADD COLUMN atuacao                     varchar(10)  NOT NULL DEFAULT 'autonomo',
    ADD CONSTRAINT ck_barbearia_categoria CHECK (categoria IN ('barbearia', 'salão de beleza', 'unhas')),
    ADD CONSTRAINT ck_barbearia_atendimento CHECK (atendimento IN ('local', 'domicilio')),
    ADD CONSTRAINT ck_barbearia_cancelamento_horas CHECK (cancelamento_horas BETWEEN 0 AND 72),
    ADD CONSTRAINT ck_barbearia_atuacao CHECK (atuacao IN ('autonomo', 'equipe'));

-- O telefone que já existe passa a ser também o WhatsApp de contato mostrado no portal.
COMMENT ON COLUMN barbearia.telefone IS 'telefone/WhatsApp de contato mostrado no portal';

-- Galeria de portfólio do portal. Fica numa tabela separada para as fotos não serem carregadas
-- junto com a barbearia em toda requisição. Se a barbearia for apagada, as fotos vão junto.
CREATE TABLE barbearia_foto (
    id           uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    barbearia_id uuid    NOT NULL REFERENCES barbearia (id) ON DELETE CASCADE,
    -- data URL da imagem (PNG/JPG/WEBP), igual à logo
    imagem       text    NOT NULL,
    -- posição na galeria (0 = primeira)
    ordem        integer NOT NULL
);

CREATE INDEX idx_barbearia_foto_barbearia ON barbearia_foto (barbearia_id, ordem);
