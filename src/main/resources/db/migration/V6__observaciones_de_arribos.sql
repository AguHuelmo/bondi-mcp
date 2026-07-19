-- Historial de esperas observadas: cada consulta de arribos deja registro de lo que el tiempo
-- real decia en ese momento.
--
-- Es el dato que nadie mas tiene y que no se puede reconstruir hacia atras: la espera REAL por
-- linea, parada y hora, contra la programada. Se alimenta solo, sin ninguna llamada extra a la
-- API de la Intendencia: cada consulta de un usuario, del bot o de la guardia de alertas (que
-- revisa cada 30 segundos) ya trae estos numeros; aca solo se dejan anotados.

CREATE TABLE observacion_arribo (
    id            BIGSERIAL   PRIMARY KEY,
    observado_en  TIMESTAMPTZ NOT NULL,
    codigo_parada BIGINT      NOT NULL,
    -- Siempre en MAYUSCULA, como en parada_linea.
    linea         VARCHAR(16) NOT NULL,
    -- La espera que el tiempo real prometia en ese momento.
    espera_minutos SMALLINT   NOT NULL,
    distancia_metros INT,
    -- Identificador del coche, si la API lo informa: algun dia permitira seguir a un bus
    -- hasta su llegada y medir puntualidad estricta (prometido vs real).
    bus_id        INT
);

-- Las consultas del historial son siempre por linea, a veces acotadas a una parada.
CREATE INDEX idx_observacion_linea_parada ON observacion_arribo (linea, codigo_parada, observado_en);
