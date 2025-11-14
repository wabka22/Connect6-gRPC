package connect6.client.ui;

import java.awt.*;

public class BoardRenderer {
  public static void drawBoard(
      Graphics2D g, int boardSize, int cellSize, int offsetX, int offsetY) {
    g.setColor(Color.BLACK);
    for (int i = 0; i < boardSize; i++) {
      int x = offsetX + i * cellSize + cellSize / 2;
      int y = offsetY + i * cellSize + cellSize / 2;
      g.drawLine(offsetX + cellSize / 2, y, offsetX + cellSize / 2 + (boardSize - 1) * cellSize, y);
      g.drawLine(x, offsetY + cellSize / 2, x, offsetY + cellSize / 2 + (boardSize - 1) * cellSize);
    }
  }
}
