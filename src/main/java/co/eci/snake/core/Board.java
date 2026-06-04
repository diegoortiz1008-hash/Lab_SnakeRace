package co.eci.snake.core;
 
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
 
public final class Board {
  private final int width;
  private final int height;
 
  private final Set<Position> mice      = new HashSet<>();
  private final Set<Position> obstacles = new HashSet<>();
  private final Set<Position> turbo     = new HashSet<>();
  private final Map<Position, Position> teleports = new HashMap<>();
 
  public enum MoveResult { MOVED, ATE_MOUSE, HIT_OBSTACLE, ATE_TURBO, TELEPORTED, DIED }
 
  public Board(int width, int height) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
    this.width  = width;
    this.height = height;
    for (int i = 0; i < 6; i++) mice.add(randomEmpty());
    for (int i = 0; i < 4; i++) obstacles.add(randomEmpty());
    for (int i = 0; i < 3; i++) turbo.add(randomEmpty());
    createTeleportPairs(2);
  }
 
  public int width()  { return width; }
  public int height() { return height; }
 
  public synchronized Set<Position>          mice()      { return new HashSet<>(mice); }
  public synchronized Set<Position>          obstacles() { return new HashSet<>(obstacles); }
  public synchronized Set<Position>          turbo()     { return new HashSet<>(turbo); }
  public synchronized Map<Position,Position> teleports() { return new HashMap<>(teleports); }
 
  /**
   * Mueve una serpiente y verifica colisiones con el tablero.
   * La colisión entre serpientes se verifica aparte en checkCollisions().
   */
  public synchronized MoveResult step(Snake snake) {
    Objects.requireNonNull(snake, "snake");
    if (!snake.isAlive()) return MoveResult.DIED;
 
    var head = snake.head();
    var dir  = snake.direction();
    Position next = new Position(head.x() + dir.dx, head.y() + dir.dy).wrap(width, height);
 
    if (obstacles.contains(next)) return MoveResult.HIT_OBSTACLE;
 
    boolean teleported = false;
    if (teleports.containsKey(next)) {
      next = teleports.get(next);
      teleported = true;
    }
 
    boolean ateMouse = mice.remove(next);
    boolean ateTurbo = turbo.remove(next);
 
    snake.advance(next, ateMouse);
 
    if (ateMouse) {
      mice.add(randomEmpty());
      obstacles.add(randomEmpty());
      if (ThreadLocalRandom.current().nextDouble() < 0.2) turbo.add(randomEmpty());
    }
 
    if (ateTurbo)   return MoveResult.ATE_TURBO;
    if (ateMouse)   return MoveResult.ATE_MOUSE;
    if (teleported) return MoveResult.TELEPORTED;
    return MoveResult.MOVED;
  }
 
  /**
   * Verifica colisiones entre serpientes después de que todas se movieron.
   * - Si la cabeza de A está en el cuerpo de B → A muere.
   * - Si las cabezas de A y B coinciden → las dos mueren.
   */
  public synchronized void checkCollisions(List<Snake> snakes) {
    int n = snakes.size();
    for (int i = 0; i < n; i++) {
      Snake a = snakes.get(i);
      if (!a.isAlive()) continue;
      Position headA = a.head();
 
      for (int j = 0; j < n; j++) {
        if (i == j) continue;
        Snake b = snakes.get(j);
        if (!b.isAlive()) continue;
 
        // Choque de frente: ambas cabezas en la misma posición
        if (headA.equals(b.head())) {
          a.die();
          b.die();
          break;
        }
 
        // A choca contra el cuerpo de B (excluyendo la cabeza ya revisada)
        var bodyB = b.snapshot();
        bodyB.pollFirst(); // quita cabeza
        if (bodyB.contains(headA)) {
          a.die();
          break;
        }
      }
    }
  }
 
  private void createTeleportPairs(int pairs) {
    for (int i = 0; i < pairs; i++) {
      Position a = randomEmpty();
      Position b = randomEmpty();
      teleports.put(a, b);
      teleports.put(b, a);
    }
  }
 
  private Position randomEmpty() {
    var rnd = ThreadLocalRandom.current();
    Position p;
    int guard = 0;
    do {
      p = new Position(rnd.nextInt(width), rnd.nextInt(height));
      guard++;
      if (guard > width * height * 2) break;
    } while (mice.contains(p) || obstacles.contains(p) || turbo.contains(p) || teleports.containsKey(p));
    return p;
  }
}