-- Horarios teoricos por parada y linea, del GTFS estatico.
--
-- Es la respuesta a "cuando pasa la 185 por aca" cuando el tiempo real no alcanza: de noche,
-- con la linea sin unidades en la calle, o para planificar a futuro. Complementa a los arribos,
-- no los reemplaza.
--
-- El dia se clasifica en tres tipos (habil, sabado, domingo) que salen de calendar.txt; los
-- feriados y otras excepciones de calendar_dates.txt no se contemplan.

CREATE TABLE horario_teorico (
    codigo_parada BIGINT      NOT NULL,
    -- Siempre en MAYUSCULA, como en parada_linea.
    linea         VARCHAR(16) NOT NULL,
    -- HABIL | SABADO | DOMINGO
    tipo_dia      VARCHAR(8)  NOT NULL,
    -- Minutos desde la medianoche del dia de servicio. Puede superar 1440: el GTFS escribe
    -- "24:30" para el bondi de la 00:30 que pertenece al servicio del dia anterior.
    minuto        SMALLINT    NOT NULL,
    -- La PK ademas dedupe: muchos viajes distintos pisan el mismo (parada, linea, dia, hora).
    PRIMARY KEY (codigo_parada, linea, tipo_dia, minuto)
);
