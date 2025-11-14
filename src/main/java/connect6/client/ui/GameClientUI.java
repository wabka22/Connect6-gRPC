package connect6.client.ui;

import connect6.client.GameClient;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class GameClientUI {

  public final JTextField nameField =
      new JTextField("Player" + (System.currentTimeMillis() % 1000), 15);
  public final JButton connectBtn = new JButton("Connect");
  public final JButton disconnectBtn = new JButton("Disconnect");
  public final JLabel statusLabel = new JLabel("Not connected");
  public final JLabel roleLabel = new JLabel("Role: -");
  public final JLabel turnLabel = new JLabel("Turn: -");
  public final JLabel scoreLabel = new JLabel();
  public final GameBoardPanel boardPanel = new GameBoardPanel(19);

  private static final Color BACKGROUND = new Color(40, 44, 52);
  private static final Color PANEL_BG = new Color(50, 55, 65);
  private static final Color PRIMARY = new Color(88, 133, 175);
  private static final Color TEXT_COLOR = new Color(230, 230, 230);

  public JPanel createMainPanel(GameClient client) {
    JPanel root = new JPanel(new BorderLayout(8, 8));
    root.setBackground(BACKGROUND);
    root.setBorder(new EmptyBorder(10, 10, 10, 10));

    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
    top.setBackground(PANEL_BG);

    JLabel nameLabel = new JLabel("Player:");
    nameLabel.setForeground(TEXT_COLOR);
    nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 14f));

    nameField.setBackground(new Color(70, 75, 85));
    nameField.setForeground(TEXT_COLOR);
    nameField.setCaretColor(TEXT_COLOR);
    nameField.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
    nameField.setFont(nameField.getFont().deriveFont(Font.PLAIN, 14f));

    styleButton(connectBtn, PRIMARY, Color.WHITE);
    styleButton(disconnectBtn, new Color(120, 120, 120), Color.WHITE);

    top.add(nameLabel);
    top.add(nameField);
    top.add(connectBtn);
    top.add(disconnectBtn);

    root.add(top, BorderLayout.NORTH);

    JPanel right = new JPanel();
    right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
    right.setBackground(PANEL_BG);
    right.setBorder(new EmptyBorder(10, 10, 10, 10));

    Font infoFont = new Font("SansSerif", Font.PLAIN, 14);
    for (JLabel label : new JLabel[] {statusLabel, roleLabel, turnLabel, scoreLabel}) {
      label.setFont(infoFont);
      label.setForeground(TEXT_COLOR);
      label.setAlignmentX(Component.LEFT_ALIGNMENT);
      right.add(label);
      right.add(Box.createVerticalStrut(8));
    }

    root.add(right, BorderLayout.EAST);

    boardPanel.setBackground(new Color(222, 184, 135));
    root.add(boardPanel, BorderLayout.CENTER);

    return root;
  }

  private void styleButton(JButton button, Color bg, Color fg) {
    button.setBackground(bg);
    button.setForeground(fg);
    button.setFocusPainted(false);
    button.setFont(new Font("SansSerif", Font.BOLD, 13));
    button.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.setOpaque(true);

    button.addMouseListener(
        new java.awt.event.MouseAdapter() {
          public void mouseEntered(java.awt.event.MouseEvent evt) {
            button.setBackground(bg.brighter());
          }

          public void mouseExited(java.awt.event.MouseEvent evt) {
            button.setBackground(bg);
          }
        });
  }
}
