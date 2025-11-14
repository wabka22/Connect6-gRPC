package connect6.client;

import connect6.client.ui.GameClientUI;
import connect6.client.ui.Images;
import connect6.game.PlayerType;
import connect6.rmi.RemoteClientInterface;
import connect6.rmi.RemoteServerInterface;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import javax.swing.*;

public class GameClient extends JFrame implements RemoteClientInterface {

  private RemoteServerInterface gameServer;
  private String playerName;
  private PlayerType playerRole;
  private boolean myTurn = false;
  private boolean gameActive = false;

  private final GameClientUI ui = new GameClientUI();
  private int playerWins = 0;
  private int opponentWins = 0;

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new GameClient().setVisible(true));
  }

  public GameClient() {
    try {
      UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    } catch (Exception ignored) {
    }

    setTitle("Connect6");

    if (Images.ICON != null) {
      setIconImage(Images.ICON.getImage());
    } else {
      System.err.println("Icon not found!");
    }

    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setContentPane(ui.createMainPanel(this));
    setSize(900, 850);
    setLocationRelativeTo(null);

    ui.scoreLabel.setText(getScoreText());
    ui.disconnectBtn.setEnabled(false);
    ui.boardPanel.setClickListener(this::handleBoardClick);

    ui.connectBtn.addActionListener(e -> onConnectClicked());
    ui.disconnectBtn.addActionListener(e -> dispose());
  }

  private void onConnectClicked() {
    playerName = ui.nameField.getText().trim();
    if (playerName.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Enter player name");
      return;
    }
    connectToServer(playerName);
    if (gameServer != null) {
      ui.connectBtn.setEnabled(false);
      ui.disconnectBtn.setEnabled(true);
    }
  }

  private void connectToServer(String name) {
    try {
      System.setProperty("java.security.policy", "client.policy");

      Registry registry =
          LocateRegistry.getRegistry(ClientConfig.CFG.SERVER_HOST, ClientConfig.CFG.RMI_PORT);
      gameServer = (RemoteServerInterface) registry.lookup(ClientConfig.CFG.GAME_SERVER_NAME);

      RemoteClientInterface stub =
          (RemoteClientInterface) UnicastRemoteObject.exportObject(this, 0);
      gameServer.registerClient(stub, name);

      ui.statusLabel.setText("Connected as: " + name);
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Connection failed: " + e.getMessage());
    }
  }

  private void handleBoardClick(int x, int y) {
    if (!gameActive) {
      JOptionPane.showMessageDialog(this, "Game not started yet");
      return;
    }
    if (!myTurn) {
      JOptionPane.showMessageDialog(this, "Not your turn");
      return;
    }
    try {
      gameServer.makeMove(playerName, x, y);
    } catch (RemoteException e) {
      JOptionPane.showMessageDialog(this, "Move failed: " + e.getMessage());
    }
  }

  @Override
  public void updateBoard(char[][] board) {
    SwingUtilities.invokeLater(() -> ui.boardPanel.setBoard(board));
  }

  @Override
  public void setPlayerRole(String role) {
    SwingUtilities.invokeLater(
        () -> {
          playerRole = PlayerType.valueOf(role);
          ui.roleLabel.setText("Role: " + playerRole);
          ui.statusLabel.setText("Connected as: " + playerName + " (" + playerRole + ")");
        });
  }

  @Override
  public void setCurrentTurn(String player) {
    SwingUtilities.invokeLater(
        () -> {
          myTurn = player.equals(playerName);
          ui.turnLabel.setText(myTurn ? "Your turn (" + playerRole + ")" : "Opponent's turn");
        });
  }

  @Override
  public void gameStarted() {
    SwingUtilities.invokeLater(
        () -> {
          gameActive = true;
          ui.statusLabel.setText("Game started!");
        });
  }

  @Override
  public void gameOver(String winner) {
    SwingUtilities.invokeLater(
        () -> {
          gameActive = false;
          myTurn = false;

          if (winner.equals(playerRole != null ? playerRole.name() : "")
              || "OPPONENT_DISCONNECTED".equals(winner)) playerWins++;
          else opponentWins++;

          ui.scoreLabel.setText(getScoreText());

          if ("OPPONENT_DISCONNECTED".equals(winner)) {
            ui.statusLabel.setText("Opponent disconnected. Waiting for new game...");
            playerRole = null;
            return;
          }

          int option =
              JOptionPane.showConfirmDialog(
                  this,
                  "Game Over! Winner: " + winner + "\nDo you want to play again?",
                  "Game Over",
                  JOptionPane.YES_NO_OPTION);

          if (option == JOptionPane.YES_OPTION) {
            try {
              gameServer.requestRematch(playerName);
            } catch (RemoteException e) {
              showError("Rematch request failed: " + e.getMessage());
            }
          }
        });
  }

  @Override
  public void showError(String message) {
    SwingUtilities.invokeLater(
        () -> {
          if (message.isEmpty()) {
            String roleText = (playerRole != null) ? " (" + playerRole + ")" : "";
            ui.statusLabel.setText("Connected as: " + playerName + roleText);
          } else {
            ui.statusLabel.setText(message);
          }
        });
  }

  @Override
  public void dispose() {
    try {
      if (gameServer != null && playerName != null) gameServer.disconnect(playerName);
    } catch (Exception ignored) {
    }

    super.dispose();
    System.exit(0);
  }

  private String getScoreText() {
    return "<html>Score<br>Your wins: "
        + playerWins
        + "<br>Opponent wins: "
        + opponentWins
        + "</html>";
  }
}
