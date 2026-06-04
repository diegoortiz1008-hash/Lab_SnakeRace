package co.eci.snake.concurrency;
 
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;
 
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
 
public final class SnakeRunner implements Runnable {
  private final Snake snake;
  private final Board board;
  private final List<Snake> allSnakes;
  private final Runnable onDeath;
  private final boolean autonomous; // true = IA, false = jugador humano
  private final int baseSleepMs  = 80;
  private final int turboSleepMs = 40;
  private int turboTicks = 0;
 
  // Monitor de pausa compartido entre todos los runners
  private final PauseMonitor pauseMonitor;
 
  public SnakeRunner(Snake snake, Board board, List<Snake> allSnakes,
                     Runnable onDeath, boolean autonomous, PauseMonitor pauseMonitor) {
    this.snake        = snake;
    this.board        = board;
    this.allSnakes    = allSnakes;
    this.onDeath      = onDeath;
    this.autonomous   = autonomous;
    this.pauseMonitor = pauseMonitor;
  }
 
  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted() && snake.isAlive()) {
        pauseMonitor.checkPause(); // se bloquea aquí si el juego está pausado
 
        if (autonomous) maybeTurn(); // solo la IA gira sola
        var res = board.step(snake);
        if (res == Board.MoveResult.HIT_OBSTACLE) {
          if (autonomous) randomTurn();
        } else if (res == Board.MoveResult.ATE_TURBO) {
          turboTicks = 100;
        }
 
        board.checkCollisions(allSnakes);
        if (!snake.isAlive()) break;
 
        int sleep = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
        if (turboTicks > 0) turboTicks--;
        Thread.sleep(sleep);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    } finally {
      if (onDeath != null) onDeath.run();
    }
  }
 
  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p) randomTurn();
  }
 
  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }
}