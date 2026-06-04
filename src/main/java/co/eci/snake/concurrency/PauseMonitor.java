package co.eci.snake.concurrency;
 
public final class PauseMonitor {
 
  private boolean paused = false;
 
  public synchronized void checkPause() throws InterruptedException {
    while (paused) {
      wait();
    }
  }
 
  public synchronized void pause() {
    paused = true;
  }
 
  public synchronized void resume() {
    paused = false;
    notifyAll();
  }
}