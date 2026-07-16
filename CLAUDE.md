# mcp-stm-montevideo

Servidor MCP (Model Context Protocol) sobre datos del STM de Montevideo.

## Stack

- Java 25 (toolchain), Gradle (usar `./gradlew`)
- Spring Boot 4.1 + Spring AI 2.0 (`spring-ai-starter-mcp-server-webmvc`)
- Spring Data JPA + PostgreSQL, migraciones con Flyway
- Lombok (`compileOnly` + `annotationProcessor`)
- Tests: JUnit 5 (`./gradlew test`)

Paquete base: `com.bondi_mcp.mcp_stm_montevideo`.

## Comandos

```bash
./gradlew build          # compilar + tests
./gradlew test           # solo tests
./gradlew bootRun        # levantar la app
```

## Convenciones de código

### Inmutabilidad (regla transversal)

Priorizar inmutabilidad siempre que el framework lo permita.

- Campos `final` por defecto; variables locales `final` cuando no se reasignan.
- No exponer colecciones mutables: devolver `List.copyOf(...)` / `Collections.unmodifiableList(...)`
  o construirlas con `Stream.toList()`.
- Nada de setters en objetos de dominio: modelar cambios de estado como métodos que devuelven
  una nueva instancia (`withX(...)`), no como mutaciones.
- Las entidades JPA son la excepción obligada (necesitan constructor sin argumentos y campos
  no finales); mantener la mutabilidad acotada a la entidad y nunca filtrarla hacia afuera:
  mapear a records antes de devolver datos desde un servicio.

### Records para POJOs y DTOs

Todo lo que sea POJO, DTO, request/response, value object o payload de tool MCP se declara como
`record`. No usar clases con `@Data`/`@Value` para esto.

```java
public record ParadaDto(String codigo, String nombre, Coordenada coordenada) {}
```

- Validar invariantes en el constructor compacto.
- Si un record recibe una colección, copiarla en el constructor compacto (`List.copyOf(...)`)
  para que la inmutabilidad sea real y no solo la de la referencia.

### Lombok

- Usar `@RequiredArgsConstructor` para la inyección de dependencias en beans (servicios,
  controllers, componentes): dependencias `private final` + constructor generado. Sin `@Autowired`.
- No usar `@Data`, `@Setter` ni `@AllArgsConstructor` salvo justificación explícita: chocan con
  la inmutabilidad y con el uso de records.
- `@Slf4j` para logging.
- `@Builder` solo cuando hay muchos parámetros opcionales y el record se vuelve incómodo.

```java
@Service
@RequiredArgsConstructor
public class ParadaService {
    private final ParadaRepository paradaRepository;
}
```

### Base de datos

- Todo cambio de esquema va en una migración Flyway en `src/main/resources/db/migration`
  (`V<n>__descripcion.sql`). Nunca depender de `ddl-auto` para generar el esquema.
