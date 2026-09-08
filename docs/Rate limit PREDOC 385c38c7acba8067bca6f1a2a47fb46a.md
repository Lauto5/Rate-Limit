# Rate limit PREDOC

Este documento es inicial, antes de empezar el proyecto real, por lo tanto, pueden llegar a cambiar varias cosas.

# 1. Objetivo personal

Aprender mas sobre java, agregar proyecto a mi portfolio, aprender sobre un sistema que se usa en sistema grandes(el rate limit), iniciar a aprender sobre redis.

# 2. Vision general

## 2.1 Definicion

Esta libreria soluciona la necesidad de un rate limit en java, sera una opcion open source la cual se adapte a distintos contextos.

## 2.2 Objetivos

- [ ]  Soporte para múltiples algoritmos
- [ ]  Soporte para diferentes sistemas de persistencia
- [ ]  Arquitectura extensible
- [ ]  Publicación open source

## 2.3 Alcance

la primera version estable deberia poder usarse algoritmos de window y persistencia de datos en memoria, luego en las siguientes versiones deberia completar todos los algoritmos y agregar la posibilidad de uso de redis.

# 3. Fundamentos teoricos

## 3.1 ¿Que es un rate limit?

Un **rate limit** (o limitación de velocidad) es una **técnica de control de tráfico que restringe la cantidad de operaciones asociadas a una clave de identificación dentro de una ventana temporal específica (como enviar una solicitud a una API) dentro de un período de tiempo determinado.**

![Diagrama sin título.drawio(5).png](Rate%20limit%20PREDOC/Diagrama_sin_ttulo.drawio(5).png)

## 3.2 ¿Por que se utiliza?

- **Protección del servidor:** Evita que un pico de tráfico o un uso excesivo tiren abajo un sistema o servicio web.
- **Seguridad:** Bloquea ataques maliciosos, como los de fuerza bruta (cuando un bot intenta adivinar contraseñas probando miles de veces por segundo) o ataques de denegación de servicio (DDoS).
- **Estabilidad:** Asegura que los recursos se compartan de manera justa entre todos los usuarios, impidiendo que una sola persona acapare el sistema.

## 3.3 Flujo interno

![Diagrama sin título.drawio(6).png](Rate%20limit%20PREDOC/Diagrama_sin_ttulo.drawio(6).png)

1. llega una request
2. se extrae el identificador
3. se busca si ya existe un state en la store, en el caso de no existir se crea uno nuevo
4. se usa el state para que el algoritmo decida si esta permitida la request
5. si la request esta permitida, se actualizada el estado , se guardara, en el caso de no estar permitido solo retorna un estado en negativo.

## 3.4 Algoritmos

un rate limit puede utilizar distintos tipos de algoritmos, cada algoritmo presenta una distinta ventaja, por lo cual cada uno de los algoritmo tienen sus diferentes usos

### 3.4.1 Fixed window

El algoritmo **Fixed Window** (Ventana Fija) es un **método simple de limitación de tasa** (*rate limiting*). Divide el tiempo en intervalos fijos (ventanas) y cuenta el número de eventos o solicitudes. Si el contador supera el límite permitido en esa ventana específica, las solicitudes adicionales son rechazadas.

#### **¿Cómo funciona?**

Imagina que un servidor permite un máximo de 5 solicitudes por minuto y la ventana se reinicia a las 12:00:00, 12:01:00, etc.

1. **Aceptación:** Si un usuario envía 3 solicitudes a las 12:00:10, el contador se establece en 3 y se procesan.
2. **Rechazo:** Si envía otras 4 solicitudes a las 12:00:30, las dos primeras son procesadas (alcanzando el límite de 5), pero las siguientes son rechazadas hasta que comience el siguiente minuto.
3. **Reinicio:** Al llegar a las 12:01:00, el contador vuelve a cero y el usuario puede realizar nuevas solicitudes.

![imagen.png](Rate%20limit%20PREDOC/imagen.png)

#### Ventajas y Desventajas

Ventajas:

- **Memoria eficiente:** Solo necesita almacenar el contador actual y el límite temporal.
- **Fácil implementación:** Muy simple de entender y programar.
- **Memoria a corto plazo:** Garantiza que los usuarios recientes utilicen la cuota del periodo actual.

Desventajas:

- **Efecto ráfaga (*burst*) en los límites:** El mayor problema de este algoritmo ocurre en los bordes de la ventana. Si un usuario agota su cuota al final de un minuto (ej. 12:00:59) y vuelve a enviar solicitudes justo al inicio del siguiente (ej. 12:01:00), el contador se reinicia. Esto permite el doble de solicitudes del límite en un lapso de tiempo muy corto.
- **Picos de tráfico:** En el cambio de ventana, los servidores pueden sufrir caídas momentáneas de sobrecarga si muchos clientes se reinician al mismo tiempo.

#### Rendimiento

Complejidad(1)

#### Especialidad

Es ideal para sistemas simples y de poco trafico

### **3.4.2 Sliding Window Log**

El algoritmo **Sliding Window Log** (Registro de Ventana Deslizante) es más preciso que el Fixed Window porque mantiene un registro con la marca de tiempo de cada solicitud dentro de una ventana de tiempo que se mueve continuamente.

#### **¿Cómo funciona?**

En lugar de dividir el tiempo en ventanas fijas, se almacena un log (registro) de las marcas de tiempo de cada solicitud. Cuando llega una nueva solicitud:

1. **Filtrado:** Se descartan todas las marcas de tiempo que están fuera de la ventana actual (ej. los últimos 60 segundos).
2. **Conteo:** Se cuenta cuántas solicitudes quedan en el log.
3. **Decisión:** Si el conteo es menor al límite, se acepta la solicitud y se agrega su marca de tiempo al log. Si es igual o mayor, se rechaza.

![imagen.png](Rate%20limit%20PREDOC/imagen%201.png)

#### **Ventajas y Desventajas**

**Ventajas:**

- **Precisión milimétrica:** Evita por completo el problema de ráfagas en los bordes del Fixed Window porque la ventana se mueve con el tiempo real.
- **Justo:** El límite se aplica sobre cualquier intervalo de tiempo de tamaño `window`, no solo sobre intervalos fijos predefinidos.

**Desventajas:**

- **Alto consumo de memoria:** Necesita almacenar todas las marcas de tiempo de las solicitudes, lo cual puede ser costoso en sistemas con alta concurrencia.
- **Mayor latencia:** Cada solicitud requiere operaciones de escritura (agregar al log) y de limpieza (eliminar entradas antiguas).

**Rendimiento:**
Menor rendimiento que Fixed Window o Token Bucket debido al costo de almacenar y consultar el log. Se usa cuando la precisión es crítica y el volumen de tráfico es moderado.

### 3.4.3 Sliding Window Counter

El algoritmo **Sliding Window Counter**(Contador de Ventana Deslizante) es un punto intermedio entre Fixed Window y Sliding Window Log. Ofrece una buena precisión sin el costo de almacenar todas las solicitudes individuales.

#### ¿Cómo funciona?

Divide la ventana de tiempo en subventanas más pequeñas (ej. 1 minuto dividido en 6 subventanas de 10 segundos). Mantiene un contador para cada subventana.

1. **Cálculo de peso:** Cuando llega una solicitud, calcula en qué subventana cae y el peso de la subventana anterior en la ventana actual.
2. **Conteo ponderado:** Suma el contador de la subventana actual completa y una fracción de la
subventana anterior (según cuánto solape con la ventana deslizante
actual).
3. **Decisión:** Compara el conteo ponderado con el límite.

![imagen.png](Rate%20limit%20PREDOC/imagen%202.png)

#### Ventajas y Desventajas

**Ventajas:**

- **Buena precisión:** Mucho más preciso que Fixed Window, pero más barato que Sliding Window Log.
- **Memoria eficiente:** Solo almacena un contador por subventana, no cada solicitud individual.

**Desventajas:**

- **Precisión aproximada:** No es 100% exacto porque asume que las solicitudes dentro de una subventana están distribuidas uniformemente.
- **Más complejo que Fixed Window:** Requiere lógica adicional para calcular los pesos.

**Rendimiento:** Muy eficiente y es la opción más común en sistemas como Redis (`INCR` + `EXPIRE` combinado con ventanas de tiempo).

### 3.4.4 Token Bucket

El algoritmo **Token Bucket** (Cubo de Fichas) es uno de los más populares y flexibles.

En lugar de contar solicitudes en ventanas fijas, utiliza un "cubo" que se llena con fichas (tokens) a una tasa constante.

Cada solicitud consume una ficha del cubo; si no hay fichas disponibles, la solicitud es rechazada.

#### ¿Cómo funciona?

El cubo tiene una **capacidad máxima** (número de fichas que puede almacenar) y se llena a una **tasa de refill** (por ejemplo, 10 fichas por segundo).

1. **Aceptación:** Un usuario envía una solicitud. Si hay fichas disponibles en el cubo, se consume una y se procesa la solicitud.
2. **Rechazo:** Si el cubo está vacío, la solicitud es rechazada.
3. **Refill (Relleno):** El cubo se va llenando continuamente a la tasa configurada hasta alcanzar su capacidad máxima. Las fichas excedentes se descartan.

![imagen.png](Rate%20limit%20PREDOC/imagen%203.png)

#### Ventajas y Desventajas

**Ventajas:**

- **Acepta ráfagas (bursts):** A diferencia del Fixed Window, permite que un cliente consuma fichas acumuladas durante periodos de inactividad, lo cual es ideal para escenarios donde se necesitan picos de tráfico ocasionales.
- **Control fino:** Permite configurar tanto la tasa promedio (refill) como el pico máximo (capacidad).
- **Ampliamente adoptado:** Es el algoritmo detrás de sistemas como AWS API Gateway y Stripe.

**Desventajas:**

- **Complejidad de estado:** Requiere almacenar la cantidad de tokens actuales y la última vez que se rellenó.
- **Configuración delicada:** Si la capacidad es demasiado alta, puede permitir ráfagas que sigan sobrecargando el sistema.

**Rendimiento:**

Bueno para la mayoría de los casos; el cálculo del refill puede hacerse de forma perezosa (lazy) para minimizar escrituras en el store.

### 3.4.5 Leaky Bucket

El algoritmo **Leaky Bucket** (Cubo con Goteo) es similar al Token Bucket pero con un comportamiento diferente. Aquí, las solicitudes entran al "cubo" y se procesan a una tasa constante, como si el cubo tuviera un agujero por donde "gotean" las solicitudes. Si el cubo se llena, las solicitudes nuevas son rechazadas.

#### ¿Cómo funciona?

Imagina un cubo que recibe solicitudes. El cubo tiene una capacidad fija y "gotea" (procesa) solicitudes a una velocidad constante.

1. **Aceptación:** Una solicitud llega. Si el cubo no está lleno, se agrega al cubo para
ser procesada. Sale del cubo (se procesa) a una tasa constante (ej. 1
solicitud por segundo).
2. **Rechazo:** Si el cubo está lleno, la solicitud es rechazada.
3. **Goteo (Drenaje):** El sistema procesa las solicitudes del cubo a una velocidad fija, creando un flujo constante y predecible.

![imagen.png](Rate%20limit%20PREDOC/imagen%204.png)

#### Ventajas y Desventajas

**Ventajas:**

- **Suaviza el tráfico:** Garantiza una tasa de salida constante, eliminando picos y protegiendo el sistema de sobrecargas.
- **Ideal para procesamiento por lotes:** Es perfecto cuando el backend no puede manejar picos de tráfico y necesita un flujo estable de trabajo (ej. colas de mensajes).

**Desventajas:**

- **No permite ráfagas:** Las solicitudes no se procesan más rápido que la tasa de goteo, incluso si el sistema tiene recursos disponibles. Esto puede ser ineficiente.
- **Retraso en el procesamiento:** Las solicitudes pueden esperar en el cubo durante un tiempo hasta ser procesadas.

**Rendimiento:** Generalmente eficiente, pero requiere almacenar el estado del cubo (número de solicitudes y marca de tiempo del último goteo).

### 3.4.6 GCRA (Generic Cell Rate Algorithm)

El algoritmo **GCRA**
 (Algoritmo de Tasa de Celdas Genérico) es un algoritmo avanzado 
utilizado originalmente en redes de telecomunicaciones (ATM) para 
controlar el tráfico de celdas. Su versión más conocida en rate limiting
 es el **Algoritmo de Llegada de Tráfico de Redis** (utilizado por `redis-cell`).

#### ¿Cómo funciona?

GCRA utiliza el concepto de **Tiempo de Llegada Teórico**(TAT). Cada solicitud tiene una marca de tiempo de llegada. El algoritmo calcula el próximo tiempo en que se podrá aceptar una  solicitud basado en la tasa y la ráfaga permitida.

1. **Cálculo del TAT:** Se calcula el tiempo en que la solicitud actual debería haber llegado
para ser aceptada, basado en la última solicitud aceptada.
2. **Decisión:** Si el tiempo actual es menor al TAT, la solicitud se rechaza (está
llegando muy pronto). Si es mayor, se acepta y se actualiza el TAT.
3. **Capacidad de ráfaga:** Permite configurar una ráfaga máxima mediante la capacidad, que se refleja en cuánto puede adelantarse el TAT al tiempo actual.

![imagen.png](Rate%20limit%20PREDOC/imagen%205.png)

#### Ventajas y Desventajas

**Ventajas:**

- **Precisión extrema:** Similar a Sliding Window Log pero con un solo valor de estado (el TAT).
- **Memoria mínima:** Solo necesita almacenar un número (el TAT), no un contador ni un log.
- **Soporte para ráfagas:** Permite picos controlados.
- **Atomicidad nativa:** Es muy fácil de implementar con comandos atómicos en Redis.

**Desventajas:**

- **Complejidad conceptual:** Es más difícil de entender que los algoritmos basados en ventanas.
- **Implementación delicada:** Requiere manejar correctamente el tiempo en milisegundos y el cálculo de intervalos.

**Rendimiento:**
Excelente, tanto en memoria como en velocidad. Es la implementación recomendada para sistemas que requieren alta precisión y eficiencia con Redis.

### **3.4.7 Comparativa de Algoritmos**

![imagen.png](Rate%20limit%20PREDOC/imagen%206.png)

# 4. Desarrollo

## 4.1 Estructura del proyecto

La librería utilizará arquitectura hexagonal para separar el núcleo del dominio de las implementaciones externas.

El core dependerá de abstracciones y no de tecnologías concretas como Redis.

![Diagrama sin título.drawio(7).png](Rate%20limit%20PREDOC/Diagrama_sin_ttulo.drawio(7).png)

## 4.2 Responsabilidades

**Rate limit**

- recibir la solicitud de validación
- obtener el estado actual
- ejecutar el algoritmo
- persistir el nuevo estado
- retornar la decisión

no es responsable:

- saber si el usuario es VIP
- conocer Redis
- conocer HTTP
- manejar autenticación

**Policy**

Define las reglas que debe cumplir una operación.

```java
RateLimitPolicy {

    limit = 100

    window = 1 minute

}
```

- Preguntas que deberia decidir:
    - ¿La policy se crea una vez o en cada llamada?
    - ¿Puede cambiar dinámicamente?
    - ¿Puede existir una policy por endpoint?

**State**

datos actuales

```jsx
user123 hizo 45 requests
```

Ejemplo Fixed Window:

```java
{
  "identifier": "user123",
  "count": 45,
  "windowStart": "10:00"
}
```

- Preguntas:
    - ¿Quién crea el state?
    - ¿Quién lo actualiza?
    - ¿El state depende del algoritmo?

**Store**

persistencia:

```jsx
¿Dónde guardo el estado?
```

**algoritmo**

logica:

```jsx
¿45 < 100?
```

**Identifier**

Representa la clave utilizada para aplicar el límite.

Ejemplos:

```java
userId
apiKey
IP address
tenantId
deviceId
```

La librería no sabe qué significa esa clave.

## 4.3 Decisiones abiertas

- ¿La Policy vive en cada request?
- ¿El State será genérico o específico por algoritmo?
- ¿Quién maneja TTL?
- ¿Cómo se resuelve atomicidad?
- ¿Qué pasa si Redis falla?

TTL: la calculara el algoritmo y lo resolvera el store dentro de su funcion atomica.

# 5. Posibles problematicas

## 5.1 Ciclo de vida del State

El **State** representa la información temporal que el rate limiter necesita conservar para tomar decisiones.

Ejemplo:

```java
{
  "identifier": "user123",
  "count": 45,
  "windowStart": "10:00"
}
```

### Creación

Cuando llega una request de un identificador que no existe:

Flujo:

![Diagrama sin título.drawio(8).png](Rate%20limit%20PREDOC/Diagrama_sin_ttulo.drawio(8).png)

Ejemplo:

Primera request:

```java
{
 "identifier":"user123",
 "count":1,
 "windowStart":"10:00"
}
```

### Actualización

Cuando el algoritmo determina que la operación está permitida:

![Diagrama sin título.drawio(9).png](Rate%20limit%20PREDOC/Diagrama_sin_ttulo.drawio(9).png)

Ejemplo:

Antes:

```java
{
 "count":45
}
```

Despues:

```java
{
 "count":45
}
```

### ¿El State depende del algoritmo?

si.

Cada algoritmo puede necesitar información diferente.

Ejemplo:

**Fixed Window:**

```java
{
 "count":50,
 "windowStart":"10:00"
}
```

**Token Bucket:**

```java
{
 "tokens":20,
 "lastRefill":"10:00"
}
```

Por lo tanto el diseño debe permitir diferentes representaciones de estado.

## 5.2 Expiración del State

Una problemática importante es definir cuánto tiempo vive un estado.

Ejemplo:

Un usuario realiza una request:

```java
user123
count=1
```

Luego nunca vuelve.

Si tenemos millones de usuarios:

```java
user1
user2
user3
...
```

el almacenamiento puede crecer indefinidamente.

### Soluciones posibles

#### **Memory Store**

Opciones:

- limpieza periódica
- TTL interno
- eliminar estados inactivos

#### Redis Store

Redis permite expiración automática:

```java
key: user123

TTL: 60 segundos
```

- Preguntas:
    - ¿Quién define el TTL?
    - ¿El algoritmo?
    - ¿El Store?
    - ¿La Policy?

## 5.3 Concurrencia

```java
Request A lee count=99

Request B lee count=99

A guarda 100

B guarda 100
```

se perdio un incremento

- Preguntas:
    - ¿El Store debe soportar operaciones atómicas?
    - ¿El algoritmo debe manejar locks?
    - ¿Redis necesita comandos atómicos?

## 5.4 Distribución

En una aplicación con una sola instancia:

```java
Application

Memory Store
```

funciona correctamente.

Pero en sistemas distribuidos:

![Diagrama sin título.drawio(10).png](Rate%20limit%20PREDOC/Diagrama_sin_ttulo.drawio(10).png)

cada instancia tendría su propio estado.

Ejemplo:

```java
App1:
user123 = 50

App2:
user123 = 50
```

El límite real sería incorrecto.

Solución:

Usar un almacenamiento compartido:

```
App1
App2
App3

  |

Redis
```

Todos consultan y actualizan el mismo estado.

# 6 Diseño

## 6.1 Inicio

A diferencia de bucket4j, en este caso quiero que el usuario no maneje las instancia del state, en este caso sera manejado por la libreria.

```java
RateLimit rateLimit = RateLimit.build(Algorithm.FixedWindow() 
, Persistence.Redis(conn));

RateLimitResult result = rateLimit.use()

if(result.alowed == false){
	// logica....
}

```

## 6.2 API publica

### 6.2.1 rateLimit

este contrato es la API publica de la libreria

```java
public class RateLimit <S extends AlgorithmState, P extends RateLimitPolicy>{
	
	public RateLimitResult 
	use(String identifier, P policy);
	
}
```

### 6.2.2 RateLimitResult

este es el modelo que retorna la API publica RateLimit

```java
public class RateLimitResult {
	
	boolean allowed;
	
	long remaining;
	
	Duration retryAfter;
	
	Instant resetAt;
	
}
```

## 6.3 contratos internos

### 6.3.1 Rate limit store

es el contenedor de los states

```java
protected interface RateLimitStore {
    
    public <S extends AlgorithmState,P extends RateLimitPolicy ,R>
    R executeAtomically(
        String identifier,
        P policy,
        AtomicOperation<S, R> operation
    );

}
```

### 6.3.2 Atomic operation

es la interfaz para las operaciones atomicas.

```java
protected interface AtomicOperation<S extends AlgorithmState , R > {
	
	public R apply(StoreState<S> state);
	
}
```

### 6.3.3 Rate limit algorithm

esta sera el contrato que debe implementar cada algoritmo disponible de la libreria.

```java
protected interface RateLimitAlgorithm
<S extends AlgorithmState ,
 P extends RateLimitPolicy> {
 
	 public AlgorithmExecutionResult<S> execute(S state , P policy , AlgorithmContext context);
	 
	 public S initState(P policy);
 
 }
```

### 6.3.4 Algorithm result

este sera el modelo que retornada el algoritmo

```java
public interface AlgorithmResult<S extends AlgorithmState>{
	
	private final S state;

	private final AlgorithmDecision decision;
  
  private final Instant resetAt;
	
	private final Duration expireIn;
	
	private static <S extends AlgorithmState> AlgorithmResult<S> allowed(S state,
	long remaining , Instant resetAt , Duration expiresAt);

	private static <S extends AlgorithmState> AlgorithmResult<S> denied(S state,
	Duration retryAfter , Instant resetAt , Duration expiresAt);
	
}
```

### 6.3.5 Algorithm Decision

```java
public interface AlgorithmDecision {
    boolean isAllowed();
}
```

### 6.3.6 Store state

este sera el modelo que “almacenara” el store.

este es un ejemplo, cada implementacion de store debe tener adentro una clase privada parecida a esta

```java
protected final class StoreState<T extends AlgorithmState> {

    private final T state;

    private final Instant expiresAt;

}
```

### 6.3.7 Algorithm state

esta interfaz estara vacia por que es una marca para identificar que es un algorithm state

```java
protected interface AlgorithmState {}
```

### 6.3.8 Rate limit policy

esta tambien sera una marca para que cada politica implemente su contrato.

```java
protected interface RateLimitPolicy {}
```

### 6.3.9 Algorithm context

```java
public class AlgorithmContext {

	final Instant now;

}
```

## 6.4 Flujo interno

![FlujoInternoRateLimit.drawio.png](Rate%20limit%20PREDOC/FlujoInternoRateLimit.drawio.png)

## 6.5 Posibles contratos especificos

### 6.5.1 Algorithm state

```java
public record FixedWindowState(
    int count,
    Instant windowStart
) implements AlgorithmState {}

public record SlidingWindowLogState(
    List<Long> timestamps
) implements AlgorithmState {}

public record SlidingWindowCounterState(
    Map<Long, Integer> windows
) implements AlgorithmState {}

public record LeakyBucketState(
    double water,
    long lastLeak
) implements AlgorithmState {}

public record TokenBucketState(
    double tokens,
    double lastRefill
) implements AlgorithmState {}

public record GcraState(
    long tat
) implements AlgorithmState {}
```

### 6.5.2 rate limit policy

```java
public record FixedWindowPolicy(
    int limit,
    Duration windowSize
) implements RateLimitPolicy {}

public record SlidingWindowLogPolicy(
    int limit,
    Duration windowSize
) implements RateLimitPolicy {}

public record SlidingWindowCounterPolicy(
    int limit,
    Duration windowSize,
    int subWindows
) implements RateLimitPolicy {}

public record TokenBucketPolicy(
    double capacity,
    double refillRate // tokens por unidad de tiempo
) implements RateLimitPolicy {}

public record LeakyBucketPolicy(
    double capacity,
    double leakRate // solicitudes procesadas por unidad de tiempo
) implements RateLimitPolicy {}

public record GcraPolicy(
    double rate,     // tasa media permitida
    Duration burst   // ráfaga máxima permitida (en tiempo)
) implements RateLimitPolicy {}
```

### 6.5.3 Rate limit algorithm

```java
public interface FixedWindowAlgorithm 
    extends RateLimitAlgorithm<FixedWindowState, FixedWindowPolicy> {}

public interface SlidingWindowLogAlgorithm 
    extends RateLimitAlgorithm<SlidingWindowLogState, SlidingWindowLogPolicy> {}

public interface SlidingWindowCounterAlgorithm 
    extends RateLimitAlgorithm<SlidingWindowCounterState, SlidingWindowCounterPolicy> {}

public interface TokenBucketAlgorithm 
    extends RateLimitAlgorithm<TokenBucketState, TokenBucketPolicy> {}

public interface LeakyBucketAlgorithm 
    extends RateLimitAlgorithm<LeakyBucketState, LeakyBucketPolicy> {}

public interface GcraAlgorithm 
    extends RateLimitAlgorithm<GcraState, GcraPolicy> {}
```

### 6.5.4 Algorithm decision

```java
public final class DeniedDecision implements AlgorithmDecision {
	private final Duration retryAfter;
}

public final class AllowedDecision implements AlgorithmDecision {
	private final long remaining;
}
```