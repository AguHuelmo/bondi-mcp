-- Cache local de las paradas del STM.
--
-- Existe porque GET /buses/busstops de la Intendencia no acepta ningun filtro: devuelve la
-- coleccion completa y no ofrece busqueda por texto. Guardamos el listado entero y buscamos
-- localmente, refrescando cuando vence el TTL.

CREATE TABLE parada_cache (
    codigo      BIGINT PRIMARY KEY,
    calle       VARCHAR(255) NOT NULL,
    esquina     VARCHAR(255),
    latitud     DOUBLE PRECISION,
    longitud    DOUBLE PRECISION,
    -- Calle + esquina normalizado (mayusculas, sin tildes) para poder buscar con LIKE
    -- sin depender de la extension unaccent de Postgres.
    busqueda    VARCHAR(512) NOT NULL,
    actualizado TIMESTAMPTZ  NOT NULL
);

-- pg_trgm hace usable el LIKE '%texto%' sobre la columna normalizada.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_parada_cache_busqueda ON parada_cache USING gin (busqueda gin_trgm_ops);
