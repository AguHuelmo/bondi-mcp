-- Recorridos: la secuencia ORDENADA de paradas de cada variante de linea.
--
-- Por que hace falta, con un caso real: la linea 62 aparece en la parada 3977 (Gabriel Pereira y
-- Berro) y tambien en la 3179 (18 de Julio y Ejido). Cruzar las lineas de las dos paradas diria
-- "tomate la 62"... pero de los 90 viajes de la 62 que tocan 3977 y los 90 que tocan 3179, CERO
-- tocan ambas: son los dos sentidos del recorrido. Sin el orden de las paradas no hay forma de
-- distinguir "pasa por las dos" de "te lleva de una a la otra".
--
-- Los 37.490 viajes del GTFS colapsan en 1082 recorridos distintos (~60.800 filas): muchos viajes
-- comparten exactamente la misma secuencia de paradas y solo cambian de horario.

CREATE TABLE recorrido (
    id        BIGSERIAL   PRIMARY KEY,
    linea     VARCHAR(16) NOT NULL,
    -- direction_id del GTFS: distingue ida de vuelta dentro de una misma linea.
    direccion VARCHAR(8)
);

CREATE INDEX idx_recorrido_linea ON recorrido (linea);

CREATE TABLE recorrido_parada (
    recorrido_id  BIGINT NOT NULL REFERENCES recorrido (id) ON DELETE CASCADE,
    -- Posicion dentro del recorrido. Lo unico que importa es el orden relativo:
    -- subis en una parada y bajas en otra de orden MAYOR.
    orden         INT    NOT NULL,
    codigo_parada BIGINT NOT NULL,
    PRIMARY KEY (recorrido_id, orden)
);

-- Para "que recorridos pasan por esta parada", que es el arranque de toda busqueda de viaje.
CREATE INDEX idx_recorrido_parada_parada ON recorrido_parada (codigo_parada);
