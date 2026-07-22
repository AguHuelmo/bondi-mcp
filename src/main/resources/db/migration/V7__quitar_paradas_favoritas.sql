-- Se va `parada_favorita`: la creo V2 "preparada para v1" y nunca se uso.
--
-- Las favoritas del frontend viven en localStorage del navegador (hooks/almacenamiento.ts), no
-- en la base, y exponerlas del lado del server no tiene sentido hasta que exista autenticacion
-- de usuarios. Una tabla vacia que ninguna entidad mapea solo confunde a quien lee el esquema.
--
-- No se borra el V2: ya corrio en bases existentes y Flyway valida por checksum. El camino
-- correcto para deshacer una migracion aplicada es otra migracion.

DROP TABLE IF EXISTS parada_favorita;
