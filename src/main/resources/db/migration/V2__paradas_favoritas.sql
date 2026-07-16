-- Paradas favoritas. Preparada para v1, todavia sin uso.
--
-- No se expone en la API ni en el MCP en v0: no hay autenticacion de usuarios todavia, asi que
-- `usuario` queda como identificador opaco hasta que se defina como se autentica la gente.

CREATE TABLE parada_favorita (
    id        BIGSERIAL    PRIMARY KEY,
    usuario   VARCHAR(128) NOT NULL,
    codigo    BIGINT       NOT NULL,
    alias     VARCHAR(128),
    creado    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_parada_favorita_usuario_codigo UNIQUE (usuario, codigo)
);

CREATE INDEX idx_parada_favorita_usuario ON parada_favorita (usuario);
