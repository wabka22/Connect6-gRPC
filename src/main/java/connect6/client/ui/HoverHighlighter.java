package connect6.client.ui;

import java.awt.*;

public class HoverHighlighter {
  public static void draw(Graphics2D g, int x, int y, int cellSize) {
    g.setColor(new Color(17, 0, 255, 64));
    g.fillRect(x, y, cellSize, cellSize);
    g.setColor(new Color(0, 0, 0, 64));
    g.drawRect(x, y, cellSize, cellSize);
  }
}
