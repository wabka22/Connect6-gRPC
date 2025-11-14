package connect6.client.ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;
import javax.swing.*;

public class GameBoardPanel extends JPanel {
  private final int boardSize;
  private final int cellSize = 30;
  private final int offset = 30;
  private char[][] board;
  private Point hoverCell;
  private BiConsumer<Integer, Integer> clickListener;

  public GameBoardPanel(int boardSize) {
    this.boardSize = boardSize;
    this.board = new char[boardSize][boardSize];
    setPreferredSize(
        new Dimension(boardSize * cellSize + offset * 2, boardSize * cellSize + offset * 2));
    setBackground(new Color(222, 184, 135));

    addMouseMotionListener(
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            hoverCell = getCellFromMouse(e.getX(), e.getY());
            repaint();
          }

          @Override
          public void mouseExited(MouseEvent e) {
            hoverCell = null;
            repaint();
          }
        });

    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            Point cell = getCellFromMouse(e.getX(), e.getY());
            if (cell != null && clickListener != null) clickListener.accept(cell.x, cell.y);
          }
        });
  }

  public void setBoard(char[][] newBoard) {
    this.board = newBoard;
    repaint();
  }

  public void setClickListener(BiConsumer<Integer, Integer> listener) {
    this.clickListener = listener;
  }

  private Point getCellFromMouse(int x, int y) {
    int cellX = (x - offset) / cellSize;
    int cellY = (y - offset) / cellSize;
    if (cellX < 0 || cellX >= boardSize || cellY < 0 || cellY >= boardSize) return null;
    return new Point(cellX, cellY);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    BoardRenderer.drawBoard(g2, boardSize, cellSize, offset, offset);

    if (hoverCell != null)
      HoverHighlighter.draw(
          g2, offset + hoverCell.x * cellSize, offset + hoverCell.y * cellSize, cellSize);

    for (int y = 0; y < boardSize; y++) {
      for (int x = 0; x < boardSize; x++) {
        ImageIcon stoneIcon =
            switch (board[y][x]) {
              case 'B' -> Images.BLACK;
              case 'W' -> Images.WHITE;
              default -> null;
            };
        if (stoneIcon != null) {
          g2.drawImage(
              stoneIcon.getImage(),
              offset + x * cellSize - 5,
              offset + y * cellSize - 5,
              cellSize,
              cellSize,
              this);
        }
      }
    }
  }
}
