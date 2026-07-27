# 1. Objetivo
El objetivo de los tests es validar el comportamiento funcional de cada algoritmo de Rate Limit. Los tests deben comprobar reglas de negocio y contratos del dominio, evitando depender de detalles internos de implementación.

---

# 2. Principios
Todo test del proyecto debe cumplir los siguientes principios:
- Determinístico (no utilizar `Instant.now()` directamente).
- Independiente (ningún test depende de otro).
- Repetible.
- Una única responsabilidad por test.
- Debe validar comportamiento, no implementación interna.

---

# 3. Organización
Los tests se organizan por módulo.

Cada implementación posee su propia clase de test.

Ejemplo:

```
FixedWindowAlgorithmImplTest
│
├── BasicCases
├── BoundaryCases
├── RemainingCases
├── ResetCases
└── TimeCalculations
```

**BasicCases**
Valida el comportamiento esperado en escenarios normales.

Ejemplos:
- firstRequestShouldBeAllowed
- requestAfterLimitShouldBeDenied

**BoundaryCases**
Valida condiciones límite.

Ejemplos:
- windowShouldExpireExactlyAtBoundary
- windowShouldNotExpireBeforeBoundary

---

# 4. Estructura de un Test
Todos los tests siguen el patrón AAA (Arrange - Act - Assert).

**Arrange**
Preparar el escenario.

```java
FixedWindowState initialState = stateWith(...);
AlgorithmContext context = contextAt(...);
```

**Act**
Ejecutar la acción bajo prueba.

```java
AlgorithmResult<FixedWindowState> result =
execute(initialState, context);
```

**Assert**
Verificar el comportamiento esperado.
```java
assertEquals(...);
assertTrue(...);
```

---

# 5. Helpers
Se recomienda encapsular la creación de objetos repetitivos mediante helpers privados.

Ejemplo:
```java
stateWith(...)

contextAt(...)

execute(...)

extractAllowed(...)

extractDenied(...)
```
Esto reduce duplicación y mejora la legibilidad de los tests.

---

# 6. Uso del tiempo
Los tests nunca deben depender del reloj del sistema.

Incorrecto:
```java
Instant.now()
```

Correcto:
```java
Instant.parse("2026-01-01T10:00:00Z")
```
o utilizar un `Clock` controlado cuando corresponda.

---

# 7. Buenas prácticas

- Nombrar los tests utilizando el formato `should...` o una descripción clara del comportamiento (`firstRequestShouldBeAllowed`).
- Evitar lógica compleja dentro de los tests.
- No duplicar asserts innecesarios.
- Utilizar `assertSame()` y `assertNotSame()` cuando se quiera validar reutilización o creación de nuevas instancias.
- Mantener los tests pequeños y enfocados en una sola regla de negocio.
