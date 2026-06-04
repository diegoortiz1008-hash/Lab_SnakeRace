# Snake Race — ARSW Lab #2 (Java 21, Virtual Threads)

**Escuela Colombiana de Ingeniería – Arquitecturas de Software**  
Laboratorio de programación concurrente: condiciones de carrera, sincronización y colecciones seguras.

---

## Requisitos

- **JDK 21** (Temurin recomendado)
- **Maven 3.9+**
- SO: Windows, macOS o Linux

---

## Cómo ejecutar

```bash
mvn clean verify
mvn -q -DskipTests exec:java -Dsnakes=4
```

- `-Dsnakes=N` → inicia el juego con **N** serpientes (por defecto 2).
- **Controles**:
  - **Flechas**: serpiente **0** (Jugador 1).
  - **WASD**: serpiente **1** (si existe).
  - **Espacio** o botón **Action**: Pausar / Reanudar.

---

## Reglas del juego (resumen)

- **N serpientes** corren de forma autónoma (cada una en su propio hilo).
- **Ratones**: al comer uno, la serpiente **crece** y aparece un **nuevo obstáculo**.
- **Obstáculos**: si la cabeza entra en un obstáculo hay **rebote**.
- **Teletransportadores** (flechas rojas): entrar por uno te **saca por su par**.
- **Rayos (Turbo)**: al pisarlos, la serpiente obtiene **velocidad aumentada** temporal.
- Movimiento con **wrap-around** (el tablero “se repite” en los bordes).

---

## Arquitectura (carpetas)

```
co.eci.snake
├─ app/                 # Bootstrap de la aplicación (Main)
├─ core/                # Dominio: Board, Snake, Direction, Position
├─ core/engine/         # GameClock (ticks, Pausa/Reanudar)
├─ concurrency/         # SnakeRunner (lógica por serpiente con virtual threads)
└─ ui/legacy/           # UI estilo legado (Swing) con grilla y botón Action
```

---

# Actividades del laboratorio

## Parte I — (Calentamiento) `wait/notify` en un programa multi-hilo

1. Toma el programa [**PrimeFinder**](https://github.com/ARSW-ECI/wait-notify-excercise).
- Lo trabajado para este punto se realizó en este repositorio: [**FindPrimeNumber**][https://github.com/diegoortiz1008-hash/FindPrimeNumber).
3. Modifícalo para que **cada _t_ milisegundos**:
   - Se **pausen** todos los hilos trabajadores.
   - Se **muestre** cuántos números primos se han encontrado.
   - El programa **espere ENTER** para **reanudar**.
4. La sincronización debe usar **`synchronized`**, **`wait()`**, **`notify()` / `notifyAll()`** sobre el **mismo monitor** (sin _busy-waiting_).

## Explicación del diseño

Basicamente lo que se realizó fue la implementación del monitor, donde se vinculaba cada uno de los hilos a este y a donde se controlaba el estado de estos, **(pause = true ó pause = false)**, el cual lo modificabá después de que cada hilo terminada su tiempo de ejecucion implementado asi:

```java
try {
    Thread.sleep(TMILISECONDS);
  } catch (InterruptedException e) {
    e.printStackTrace();
}
```

luego se evalua con un while adentro del control, el cual pide confirmación para continuar cada t milisegundos, verifica el estado de cada hilo para saber si seguía corriendo o si su estado era pausado

```java

  while(!finished){
    ....
    ....

    monitor.resumeThreads();
            finished = true;
            for(int i=0;i<NTHREADS;i++){
                if(pft[i].isAlive()){
                    finished = false;
                    break;
                }
          }
    }
```

Una vez finalizado estos procesos simplemente se calcula la cantidad de numeros primos como la suma de la longitud de los arreglos encontrados por cada hilo


> Objetivo didáctico: practicar suspensión/continuación **sin** espera activa y consolidar el modelo de monitores en Java.

---

## Parte II — SnakeRace concurrente (núcleo del laboratorio)

### 1) Análisis de concurrencia

- Explica **cómo** el código usa hilos para dar autonomía a cada serpiente.
  
Cada serpiente (`Snake`) es controlada por un `SnakeRunner`, que implementa `Runnable`. Al iniciar el juego, se crea un `ExecutorService` de hilos virtuales y se somete un `SnakeRunner` por cada serpiente.
Esto significa que cada serpiente corre en su propio hilo independiente, ejecutando su ciclo de vida, de manera paralela, el GameClock de cada serpiente se ejecuta y dispara un repaint() del panel gráfico cada 60ms.

  - Posibles **condiciones de carrera**:

`direction` está marcado como `volatile`, lo que garantiza visibilidad. Sin embargo, `turn()` hace una **lectura-evaluación-escritura** que no es atómica. El hilo del jugador (EDT de Swing) puede llamar `turn()` simultáneamente con `SnakeRunner` leyendo `direction` en `board.step()`. `volatile` garantiza que no se lean valores cacheados, pero no garantiza atomicidad de la operación completa de validación + escritura. En este caso el riesgo es bajo (la peor consecuencia es que se acepte un giro inverso), pero sigue siendo una condición de carrera técnica.

  - **Colecciones** o estructuras **no seguras** en contexto concurrente.

```java
public synchronized MoveResult step(Snake snake) { ... }
```
 
El método `step()` está sincronizado sobre el objeto `Board`. Con N serpientes, todos sus hilos compiten por el mismo lock en cada ciclo. Esto no es una condición de carrera per se, pero sí un cuello de botella de sincronización que se agrava con N alto.
  - Ocurrencias de **espera activa** (busy-wait) o de sincronización innecesaria.

En `SnakeApp`, la lista `snakes` es un `ArrayList` que se pasa al panel como un `Supplier`:
 
```java
this.gamePanel = new GamePanel(board, () -> snakes);
```
 
El panel la itera en `paintComponent()` (hilo de Swing/EDT) mientras los hilos de `SnakeRunner` modifican los objetos `Snake` dentro de esa lista. La lista en sí no es modificada después de la construcción, pero los objetos `Snake` internos sí lo son concurrentemente.

### 2) Correcciones mínimas y regiones críticas

## Corrección 1: `Snake.body` — sincronización de acceso al cuerpo
 
`ArrayDeque` no es thread-safe. El hilo de `SnakeRunner` escribe en `body` con `advance()` mientras el EDT de Swing lo lee con `snapshot()` para dibujarlo, lo que puede causar `ConcurrentModificationException` o tearing visual.
 
**Solución:** agregar `synchronized` a los métodos que tocan `body`:
 
```java
public synchronized Deque<Position> snapshot() { return new ArrayDeque<>(body); }
 
public synchronized void advance(Position newHead, boolean grow) { ... }
```
 
## Corrección 2: `Snake.direction` — atomicidad en giros
 
`volatile` garantiza visibilidad pero no atomicidad. `turn()` hace lectura-validación-escritura, que puede ser interrumpida entre pasos por otro hilo.
 
**Solución:** sincronizar `turn()` y `direction()` sobre el mismo monitor, y eliminar `volatile`:
 
```java
private Direction direction; // volatile eliminado
 
public synchronized Direction direction() { return direction; }
public synchronized void turn(Direction dir) { ... }
```
 
## Corrección 3: Estado consistente al pausar
 
Al pausar el clock, los `SnakeRunner` pueden estar a mitad de un `step()`, dejando el estado del tablero incompleto en el último frame dibujado.
 
**Solución:** forzar un repaint adicional 100ms después de pausar, cuando todos los runners ya terminaron su ciclo:
 
```java
clock.pause();
SwingUtilities.invokeLater(() -> {
    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    gamePanel.repaint();
});
```
### 3) Control de ejecución seguro (UI)

## Botón Iniciar / Pausar / Reanudar
 
Se reemplazó el botón *Action* por un flujo de 3 estados usando `AtomicReference<GameState>`:
 
```java
private final AtomicReference<GameState> gameState = new AtomicReference<>(GameState.STOPPED);
 
// STOPPED → RUNNING → PAUSED → RUNNING
if (current == GameState.STOPPED) {
    gameState.set(GameState.RUNNING);
    actionButton.setText("Pausar");
    clock.start();
} else if (current == GameState.RUNNING) {
    gameState.set(GameState.PAUSED);
    pauseMonitor.pause();
    clock.pause();
    actionButton.setText("Reanudar");
} else if (current == GameState.PAUSED) {
    gameState.set(GameState.RUNNING);
    pauseMonitor.resume();
    clock.resume();
    actionButton.setText("Pausar");
}
```
 
## Pausa real de los runners — `PauseMonitor`
 
`clock.pause()` solo detiene el repaint visual. Los `SnakeRunner` seguían moviéndose en segundo plano. Se creó un `PauseMonitor` compartido que bloquea cada hilo con `wait()` al pausar:
 
```java
public synchronized void checkPause() throws InterruptedException {
    while (paused) { wait(); }
}
public synchronized void pause()  { paused = true; }
public synchronized void resume() { paused = false; notifyAll(); }
```
 
Cada runner lo consulta al inicio de su ciclo:
 
```java
while (!Thread.currentThread().isInterrupted() && snake.isAlive()) {
    pauseMonitor.checkPause(); // se bloquea aquí si está pausado
    ...
}
```
 
## Serpientes controladas por jugador no se mueven solas
 
Se agregó el flag `autonomous` al runner. Solo la IA llama `maybeTurn()`:
 
```java
if (autonomous) maybeTurn(); // jugadores humanos no giran solos
```
 
Las primeras 2 serpientes se crean con `autonomous = false`:
 
```java
boolean autonomous = i >= 2;
exec.submit(new SnakeRunner(s, board, snakes, () -> onSnakeDied(s), autonomous, pauseMonitor));
```
 
## Fix de controles WASD
 
`KeyStroke.getKeyStroke('A')` no detectaba las teclas correctamente. Se reemplazó por `KeyEvent.VK_*`:
 
```java
// ANTES — no funcionaba
im.put(KeyStroke.getKeyStroke('A'), "p2-left");
 
// DESPUÉS
im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "p2-left");
```
 
## Estado consistente al pausar
 
Tras pausar ambos mecanismos (clock + runners), se fuerza un repaint final con 100ms de delay para garantizar que todos los runners terminaron su `step()` actual antes de congelar la imagen:
 
```java
pauseMonitor.pause();
clock.pause();
SwingUtilities.invokeLater(() -> {
    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    gamePanel.repaint();
    showPauseStats();
});
```
 
## Estadísticas al pausar
 
Al pausar se muestra la serpiente viva más larga y la primera en morir, registrada en `deathOrder` desde el callback `onSnakeDied()`:
 
```java
Snake longest   = snakes.stream().filter(Snake::isAlive)
                    .max((a, b) -> Integer.compare(a.length(), b.length())).orElse(null);
Snake firstDead = deathOrder.isEmpty() ? null : deathOrder.get(0);
```
 
## Fix obstáculos — serpiente no se queda pegada
 
Al chocar con un obstáculo, el runner ahora gira aleatoriamente para cualquier serpiente, no solo la IA:
 
```java
// ANTES
if (res == Board.MoveResult.HIT_OBSTACLE) {
    if (autonomous) randomTurn();
}
 
// DESPUÉS
if (res == Board.MoveResult.HIT_OBSTACLE) {
    randomTurn(); // siempre, sea jugador o IA
}
```

### 4) Robustez bajo carga

## Cómo ejecutar con N serpientes
 
**Desde la terminal para ejecutar con Maven:**
```bash
mvn exec:java -Dexec.mainClass="co.eci.snake.app.Main" -Dsnakes=20
```

## Qué se verificó
 
Se ejecutó el juego con `-Dsnakes=20` y velocidad aumentada (`baseSleepMs = 40`) durante sesiones extendidas, verificando:
 
- **Sin `ConcurrentModificationException`:** el acceso a `Snake.body` está sincronizado en `advance()` y `snapshot()`, y `Board` sincroniza todas sus colecciones.
- **Sin deadlocks:** cada serpiente tiene su propio hilo independiente. El único lock compartido es el monitor de `Board` (para `step()` y `checkCollisions()`) y el `PauseMonitor`. Ninguno genera espera circular.
- **Sin lecturas inconsistentes:** el panel dibuja serpientes a través de `snapshot()` que toma una copia atómica del cuerpo, evitando tearing visual incluso con 20 hilos activos.
- **Pausa consistente:** `PauseMonitor.checkPause()` bloquea todos los runners simultáneamente. El repaint final se hace 100ms después para capturar un estado estable.
- **Teleports y turbo sin carreras:** ambos son modificados exclusivamente dentro de `board.step()`, que es `synchronized`, por lo que no introducen nuevas condiciones de carrera con N alto.

