package co.eci.snake.ui.legacy;
 
import co.eci.snake.concurrency.PauseMonitor;
import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.GameState;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;
 
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
 
public final class SnakeApp extends JFrame {
 
  private final Board board;
  private final GamePanel gamePanel;
  private final JButton actionButton;
  private final JLabel statusLabel;
  private final GameClock clock;
  private final List<Snake> snakes = new ArrayList<>();
  private final List<Snake> deathOrder = new ArrayList<>();
  private final AtomicReference<GameState> gameState = new AtomicReference<>(GameState.STOPPED);
  private final PauseMonitor pauseMonitor = new PauseMonitor();
  private ExecutorService exec;
 
  public SnakeApp() {
    super("The Snake Race");
    this.board = new Board(35, 28);
 
    int N = Integer.getInteger("snakes", 2);
    for (int i = 0; i < N; i++) {
      int x = 2 + (i * 3) % board.width();
      int y = 2 + (i * 2) % board.height();
      var dir = Direction.values()[i % Direction.values().length];
      snakes.add(Snake.of(x, y, dir));
    }
 
    this.gamePanel    = new GamePanel(board, () -> snakes);
    this.actionButton = new JButton("Iniciar");
    this.statusLabel  = new JLabel(" ", SwingConstants.CENTER);
    statusLabel.setFont(new Font("Arial", Font.BOLD, 13));
 
    JPanel south = new JPanel(new BorderLayout());
    south.add(actionButton, BorderLayout.CENTER);
    south.add(statusLabel,  BorderLayout.NORTH);
 
    setLayout(new BorderLayout());
    add(gamePanel, BorderLayout.CENTER);
    add(south,     BorderLayout.SOUTH);
 
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null);
 
    this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(gamePanel::repaint));
 
    setupControls();
    actionButton.addActionListener(e -> handleButton());
    setVisible(true);
  }
 
  private void setupControls() {
    InputMap im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap am = gamePanel.getActionMap();
 
    // SPACE → pausa
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "pause");
    am.put("pause", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { handleButton(); }
    });
 
    // Jugador 1 — flechas
    var p1 = snakes.get(0);
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0), "p1-left");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "p1-right");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,    0), "p1-up");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,  0), "p1-down");
    am.put("p1-left",  new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { p1.turn(Direction.LEFT); }
    });
    am.put("p1-right", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { p1.turn(Direction.RIGHT); }
    });
    am.put("p1-up",    new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { p1.turn(Direction.UP); }
    });
    am.put("p1-down",  new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { p1.turn(Direction.DOWN); }
    });
 
    // Jugador 2 — WASD
    if (snakes.size() > 1) {
      var p2 = snakes.get(1);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "p2-left");
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "p2-right");
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), "p2-up");
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), "p2-down");
      am.put("p2-left",  new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.LEFT); }
      });
      am.put("p2-right", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.RIGHT); }
      });
      am.put("p2-up",    new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.UP); }
      });
      am.put("p2-down",  new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { p2.turn(Direction.DOWN); }
      });
    }
  }
 
  private void handleButton() {
    GameState current = gameState.get();
 
    if (current == GameState.STOPPED) {
      gameState.set(GameState.RUNNING);
      actionButton.setText("Pausar");
      statusLabel.setText(" ");
      deathOrder.clear();
 
      exec = Executors.newVirtualThreadPerTaskExecutor();
      for (int i = 0; i < snakes.size(); i++) {
        Snake s = snakes.get(i);
        // Las primeras 2 serpientes son jugadores humanos, el resto IA
        boolean autonomous = i >= 2;
        exec.submit(new SnakeRunner(s, board, snakes, () -> onSnakeDied(s), autonomous, pauseMonitor));
      }
 
      clock.start();
 
    } else if (current == GameState.RUNNING) {
      gameState.set(GameState.PAUSED);
      pauseMonitor.pause(); // pausa los runners
      clock.pause();        // pausa el repaint
      actionButton.setText("Reanudar");
 
      SwingUtilities.invokeLater(() -> {
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        gamePanel.repaint();
        showPauseStats();
      });
 
    } else if (current == GameState.PAUSED) {
      gameState.set(GameState.RUNNING);
      pauseMonitor.resume(); // despierta los runners
      clock.resume();
      actionButton.setText("Pausar");
      statusLabel.setText(" ");
    }
  }
 
  private synchronized void onSnakeDied(Snake s) {
    if (!deathOrder.contains(s)) deathOrder.add(s);
  }
 
  private void showPauseStats() {
    Snake longest = snakes.stream()
        .filter(Snake::isAlive)
        .max((a, b) -> Integer.compare(a.length(), b.length()))
        .orElse(null);
    Snake firstDead = deathOrder.isEmpty() ? null : deathOrder.get(0);
 
    StringBuilder sb = new StringBuilder();
    if (longest != null) {
      int idx = snakes.indexOf(longest) + 1;
      sb.append("Más larga: Serpiente ").append(idx)
        .append(" (").append(longest.length()).append(" seg)");
    }
    if (firstDead != null) {
      int idx = snakes.indexOf(firstDead) + 1;
      if (sb.length() > 0) sb.append("   |   ");
      sb.append("Primera en morir: Serpiente ").append(idx);
    }
    if (sb.length() == 0) sb.append("Juego en curso...");
    statusLabel.setText(sb.toString());
  }
 
  // -------------------------------------------------------------------------
 
  public static final class GamePanel extends JPanel {
    private final Board board;
    private final Supplier snakesSupplier;
    private final int cell = 20;
 
    @FunctionalInterface
    public interface Supplier { List<Snake> get(); }
 
    public GamePanel(Board board, Supplier snakesSupplier) {
      this.board          = board;
      this.snakesSupplier = snakesSupplier;
      setPreferredSize(new Dimension(board.width() * cell + 1, board.height() * cell + 40));
      setBackground(Color.WHITE);
    }
 
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
 
      g2.setColor(new Color(220, 220, 220));
      for (int x = 0; x <= board.width(); x++)
        g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      for (int y = 0; y <= board.height(); y++)
        g2.drawLine(0, y * cell, board.width() * cell, y * cell);
 
      g2.setColor(new Color(255, 102, 0));
      for (var p : board.obstacles()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillRect(x + 2, y + 2, cell - 4, cell - 4);
        g2.setColor(Color.RED);
        g2.drawLine(x + 4, y + 4, x + cell - 6, y + 4);
        g2.drawLine(x + 4, y + 8, x + cell - 6, y + 8);
        g2.drawLine(x + 4, y + 12, x + cell - 6, y + 12);
        g2.setColor(new Color(255, 102, 0));
      }
 
      g2.setColor(Color.BLACK);
      for (var p : board.mice()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillOval(x + 4, y + 4, cell - 8, cell - 8);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 8, y + 8, cell - 16, cell - 16);
        g2.setColor(Color.BLACK);
      }
 
      Map<Position, Position> tp = board.teleports();
      g2.setColor(Color.RED);
      for (var entry : tp.entrySet()) {
        Position from = entry.getKey();
        int x = from.x() * cell, y = from.y() * cell;
        int[] xs = { x + 4, x + cell - 4, x + cell - 10, x + cell - 10, x + 4 };
        int[] ys = { y + cell / 2, y + cell / 2, y + 4, y + cell - 4, y + cell / 2 };
        g2.fillPolygon(xs, ys, xs.length);
      }
 
      g2.setColor(Color.BLACK);
      for (var p : board.turbo()) {
        int x = p.x() * cell, y = p.y() * cell;
        int[] xs = { x + 8, x + 12, x + 10, x + 14, x + 6, x + 10 };
        int[] ys = { y + 2, y + 2, y + 8, y + 8, y + 16, y + 10 };
        g2.fillPolygon(xs, ys, xs.length);
      }
 
      // Paleta de colores — el índice original determina el color, no la posición en lista filtrada
      Color[] palette = {
          new Color(0, 170, 0),   new Color(0, 160, 180), new Color(180, 0, 180),
          new Color(200, 140, 0), new Color(180, 0, 0),   new Color(0, 120, 60),
          new Color(100, 0, 180), new Color(180, 100, 0), new Color(0, 80, 180),
          new Color(160, 160, 0)
      };
      var allSnakes = snakesSupplier.get();
      for (int idx = 0; idx < allSnakes.size(); idx++) {
        Snake s = allSnakes.get(idx);
        if (!s.isAlive()) continue; // solo dibujar vivas
        Color base = palette[idx % palette.length];
        var body = s.snapshot().toArray(new Position[0]);
        for (int i = 0; i < body.length; i++) {
          var p = body[i];
          int shade = Math.max(0, 40 - i * 4);
          g2.setColor(new Color(
              Math.min(255, base.getRed()   + shade),
              Math.min(255, base.getGreen() + shade),
              Math.min(255, base.getBlue()  + shade)));
          g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
        }
      }
      g2.dispose();
    }
  }
 
  public static void launch() {
    SwingUtilities.invokeLater(SnakeApp::new);
  }
}