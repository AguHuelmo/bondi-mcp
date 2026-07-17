-- Relacion linea <-> parada, importada del GTFS estatico de la Intendencia.
--
-- Existe porque el endpoint que deberia darla, /buses/busstops/{id}/lines, esta roto y devuelve
-- 400 para cualquier parada. La alternativa en vivo (/buses?busstopId=) solo lista lineas con
-- buses circulando, asi que de noche o con la linea parada no sirve para responder "que lineas
-- pasan por aca".
--
-- El GTFS trae la relacion completa: ~15.900 pares parada-linea sobre ~4.900 paradas.

CREATE TABLE parada_linea (
    codigo_parada BIGINT      NOT NULL,
    -- Siempre en MAYUSCULA. El GTFS escribe "Ce1" pero la API en vivo exige "CE1": es
    -- case-sensitive y responde 400 ante "Ce1". Normalizamos al importar.
    linea         VARCHAR(16) NOT NULL,
    PRIMARY KEY (codigo_parada, linea)
);

-- Para responder "que paradas tiene la linea 183".
CREATE INDEX idx_parada_linea_linea ON parada_linea (linea);

-- Version del GTFS ya importado, para no reprocesar 88 MB en cada arranque.
CREATE TABLE gtfs_import (
    id         SMALLINT     PRIMARY KEY DEFAULT 1,
    version    VARCHAR(32)  NOT NULL,
    importado  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_gtfs_import_fila_unica CHECK (id = 1)
);
