# Integración Spring Boot (`rate-limit-spring-boot`) — AutoConfiguration + configuración externa

| | |
|---|---|
| **Date** | 2026-09-15 |
| **Type** | Feature |
| **Module(s)** | `rate-limit-spring-boot` (nuevo), `rate-limit/pom.xml` |
| **Status** | Not merged — branch `feature/spring-boot`. |
| **Branch** | `feature/spring-boot` |
| **Ticket / issue** | `N/A` |
| **Author(s)** | `N/A` (opencode-assisted session) |

## Summary

Agrega el módulo `rate-limit-spring-boot`, un adapter de entrada de Spring Boot hacia el
dominio existente: su `RateLimitAutoConfiguration` provisiona la persistencia (`RateLimitStore`
InMemory o Redis según `application.yml`) y un `Clock`, expuestos como beans `@ConditionalOnMissingBean`.
El consumidor sigue usando la API de dominio directamente: inyecta el store y construye su propio
`RateLimit` con `RateLimit.build(algorithm, store)`.

## Motivation

El proyecto ya tiene `core` (dominio/algoritmos/ports), `inmemory` y `redis`, con el core libre de
dependencias de framework. Falta la integración declarativa con Spring Boot: que una aplicación
Spring pueda usar Rate Limit mediante configuración y beans de Spring sin conocer los detalles de
construcción de `RateLimit`, `Persistence`, `Clock`, etc. La integración es deliberadamente
"thin": Spring provisiona la infraestructura y el usuario sigue usando la API core fuertemente
tipada. Alcanza V1 (foundation) y V1.1 (configuración externa tipada) del roadmap de la feature,
dejando fuera V1.2 (observabilidad) y V2 (`@RateLimited`/AOP).

## What changed

- **`rate-limit/pom.xml`**: el reactor registra `<module>rate-limit-spring-boot</module>`.
- **`rate-limit-spring-boot/pom.xml`** (nuevo): Java 17 (`maven.compiler.release=17`, enforcer
  `[17,)`) con BOM `spring-boot-dependencies:3.5.16`; dependencias del proyecto con
  `rate-limit-inmemory` y `rate-limit-redis` en `<optional>true</optional>` (el consumidor elige
  el adapter); `spring-boot-autoconfigure` en compile; test-scope con
  `spring-boot-test(-autoconfigure)`, `assertj-core` y JUnit 5.12.2 (alineado con el JUnit
  Platform 1.12.x del BOM, incompatible con el 5.10.2 del parent). Sources y javadoc JAR por
  requisito de Central, espejo de los otros módulos.
- **`properties`** (`io.github.lauto5.rateLimit.spring.properties`): `RateLimitProperties`
  (`@ConfigurationProperties("rate-limit")`, `enabled` default `true`), `PersistenceProperties`
  (`type` default `inmemory`), `PersistenceType`, `InMemoryProperties` (placeholder tipado) y
  `RedisProperties` (`url`, `namespace` default `rate-limit`). Configuración tipada por adapter,
  sin bloque plano de algoritmo.
- **`RateLimitAutoConfiguration`**: `@AutoConfiguration` + `@ConditionalOnClass(RateLimit.class)`
  + `@ConditionalOnProperty(rate-limit.enabled, matchIfMissing=true)` +
  `@EnableConfigurationProperties`. Expone `Clock` (`Clock.systemUTC()`, `@ConditionalOnMissingBean`)
  y `RateLimitStore` a través de sub-configuraciones: InMemory (`@ConditionalOnClass(InMemoryStore)`,
  `type=inmemory` por defecto) y Redis (`@ConditionalOnClass(RedisStore)`, `type=redis`,
  `destroyMethod="close"`), ambas `@ConditionalOnMissingBean`. No crea beans `RateLimit<?,?>`.
- **`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`**:
  registra la auto-configuración.
- **Tests**: `RateLimitAutoConfigurationTest` (contexto por defecto InMemory, `type=redis`,
  `enabled=false`, adaptadores ausentes vía `FilteredClassLoader`, override por bean de usuario,
  binding de properties) e integración end-to-end `RateLimitSpringIntegrationTest` (el usuario
  arma su `@Bean RateLimit` inyectando el store y `use()` aplica la policy).

La definición de "cómo" sigue `docs-for-agent-ia/feature-springboot/spring-boot-integration-opencode.md`.

## What did NOT change

- `rate-limit-core`, `rate-limit-inmemory` y `rate-limit-redis` no se modifican (dirección de
  dependencias hacia adentro intacta: `core` no conoce Spring).
- No se introducen `@RateLimited`, AOP/SpEL ni Micrometer en V1.
- No se crea una API Spring que reemplace la API de dominio (este módulo no fabrica beans
  `RateLimit<?,?>`; el algoritmo y la policy siguen siendo decisión del consumidor).
- No se separa `autoconfigure`/`starter` en módulos distintos y no se soporta Spring Boot 2.7.
- No se cambia la versión del reactor (sigue `1.0.1`).

## Breaking changes

- [x] No, it is backward compatible.
- [ ] Yes — see details and migration guide below.

No toca la API pública existente ni el comportamiento del core. Todas las clases nuevas viven en
el paquete `io.github.lauto5.rateLimit.spring.*`.

### Migration guide

`N/A`.

## Public API impact

Adición de un módulo y paquete nuevo; el consumidor usuario de `core`/`inmemory`/`redis` no ve
cambios.

```java
// Nuevo: Spring provisiona el store, el usuario construye su RateLimit tipado.
@Bean
RateLimit<FixedWindowPolicy> rateLimit(RateLimitStore store) {
    return RateLimit.build(Algorithm.fixedWindow(), store);
}
```

```yaml
# application.yml — configuración externa (V1.1)
rate-limit:
  persistence:
    type: inmemory   # o redis con url/namespace
```

## Testing

- [x] New or updated unit tests.
- [x] New or updated integration tests.
- [ ] Concurrency tests (if applicable).
- [x] Manual verification (describe briefly if applicable).

`mvn -B -f rate-limit/pom.xml clean install` (reactor completo, incluido el nuevo módulo y el
test de integración de Redis con Testcontainers) y `mvn -B -f examples/pom.xml clean verify`.
Los tests del nuevo módulo usan `ApplicationContextRunner` (no requieren servidor Redis; la
conexión Lettuce es perezosa).

## Known risks and considerations

- El módulo compila con Java 17 mientras que el resto del reactor usa release 8; es intencional
  y no altera el contrato del core (la decisión de plataforma está documentada en la feature).
- Al compilar el reactor, tanto `inmemory` como `redis` están en el classpath del módulo; el
  default `type=inmemory` + la condición `type=redis` evitan beans duplicados.
- Javadoc "no main description" sobre propiedades: consistente con el estilo de los módulos
  existentes; no bloquea el javadoc JAR.

## Related work left out of this change

- V2 — `@RateLimited`/AOP/SpEL (anotación declarativa sobre métodos).
- V1.2 — observabilidad opcional con Micrometer.
- Separación futura `rate-limit-spring-boot-autoconfigure` / `-starter`.
- Configuración de algoritmo tipada por policy en `application.yml`.

Todo ello está documentado como trabajo futuro en
`docs-for-agent-ia/feature-springboot/spring-boot-integration-opencode.md`.

## References

- Feature documentation: `docs-for-agent-ia/feature-springboot/spring-boot-integration-opencode.md`
- Change doc template: `docs/changes/TEMPLATE.md`
- PR: `N/A` (pendiente de merge a `main`)