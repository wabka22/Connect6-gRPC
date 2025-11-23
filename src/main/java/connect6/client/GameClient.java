package connect6.client;

import connect6.client.ui.GameClientUI;
import connect6.client.ui.Images;
import connect6.game.PlayerType;
import connect6.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import javax.swing.*;

public class GameClient extends JFrame {

  private ManagedChannel channel;
  private Connect6GameGrpc.Connect6GameStub asyncStub;
  private Connect6GameGrpc.Connect6GameBlockingStub blockingStub;

  private String playerName;
  private PlayerType playerRole;

  private boolean myTurn = false;
  private boolean gameActive = false;

  private int playerWins = 0;
  private int opponentWins = 0;

  private final GameClientUI ui = new GameClientUI();

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new GameClient().setVisible(true));
  }

  public GameClient() {
    try {
      UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    } catch (Exception ignored) {
    }

    setTitle("Connect6");

    if (Images.ICON != null) setIconImage(Images.ICON.getImage());

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

  // ---------------------------------------------------------------------
  // CONNECT
  // ---------------------------------------------------------------------
  private void onConnectClicked() {
    playerName = ui.nameField.getText().trim();
    if (playerName.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Enter player name");
      return;
    }

    connectToServer(playerName);

    if (channel != null) {
      ui.connectBtn.setEnabled(false);
      ui.disconnectBtn.setEnabled(true);
    }
  }

  private void connectToServer(String name) {
    try {
      channel =
          ManagedChannelBuilder.forAddress(
                  ClientConfig.CFG.SERVER_HOST, ClientConfig.CFG.SERVER_PORT)
              .usePlaintext()
              .build();

      asyncStub = Connect6GameGrpc.newStub(channel);
      blockingStub = Connect6GameGrpc.newBlockingStub(channel);

      asyncStub.register(PlayerInfo.newBuilder().setName(name).build(), new ServerEventObserver());

      ui.statusLabel.setText("Connected as: " + name);

    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Connection failed: " + e.getMessage());
    }
  }

  // ---------------------------------------------------------------------
  // MOVE HANDLING
  // ---------------------------------------------------------------------
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
      MoveResult res =
          blockingStub.makeMove(Move.newBuilder().setPlayer(playerName).setX(x).setY(y).build());

      if (!res.getSuccess())
        JOptionPane.showMessageDialog(this, "Move failed: " + res.getMessage());

    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Move failed: " + e.getMessage());
    }
  }

  // ---------------------------------------------------------------------
  // CALLBACKS (как в RMI)
  // ---------------------------------------------------------------------
  private void updateBoard(char[][] board) {
    SwingUtilities.invokeLater(() -> ui.boardPanel.setBoard(board));
  }

  private void setPlayerRoleFromServer(String role) {
    SwingUtilities.invokeLater(
        () -> {
          playerRole = PlayerType.valueOf(role);
          ui.roleLabel.setText("Role: " + playerRole);
          ui.statusLabel.setText("Connected as: " + playerName + " (" + playerRole + ")");
        });
  }

  private void setCurrentTurn(String player) {
    SwingUtilities.invokeLater(
        () -> {
          myTurn = player.equals(playerName);
          ui.turnLabel.setText(myTurn ? "Your turn (" + playerRole + ")" : "Opponent's turn");
        });
  }

  private void gameStarted() {
    SwingUtilities.invokeLater(
        () -> {
          gameActive = true;
          ui.statusLabel.setText("Game started!");
        });
  }

  private void gameOverInternal(String winner) {
    SwingUtilities.invokeLater(
        () -> {
          gameActive = false;
          myTurn = false;

          if (winner.equals(playerRole != null ? playerRole.name() : "")
              || winner.equals("OPPONENT_DISCONNECTED")) playerWins++;
          else opponentWins++;

          ui.scoreLabel.setText(getScoreText());

          if (winner.equals("OPPONENT_DISCONNECTED")) {
            ui.statusLabel.setText("Opponent disconnected. Waiting for new game...");
            playerRole = null;
            return;
          }

          int opt =
              JOptionPane.showConfirmDialog(
                  this,
                  "Game Over! Winner: " + winner + "\nPlay again?",
                  "Game Over",
                  JOptionPane.YES_NO_OPTION);

          if (opt == JOptionPane.YES_OPTION) {
            try {
              blockingStub.requestRematch(
                  RematchRequest.newBuilder().setPlayer(playerName).build());
            } catch (Exception e) {
              showError("Rematch request failed: " + e.getMessage());
            }
          }
        });
  }

  private void showError(String msg) {
    SwingUtilities.invokeLater(() -> ui.statusLabel.setText(msg));
  }

  // ---------------------------------------------------------------------
  // SERVER STREAM OBSERVER (замена RMI callback)
  // ---------------------------------------------------------------------
  private class ServerEventObserver implements StreamObserver<GameEvent> {

    @Override
    public void onNext(GameEvent e) {

        if (e.hasRole()) {
            setPlayerRoleFromServer(e.getRole());
            return;
        }

        if (e.hasBoard()) {
            Board b = e.getBoard();
            int n = b.getRowsCount();
            char[][] board = new char[n][n];

            for (int i = 0; i < n; i++) {
                Row row = b.getRows(i);
                for (int j = 0; j < row.getCellsCount(); j++) {
                    String c = row.getCells(j);
                    board[i][j] = c.isEmpty() ? '.' : c.charAt(0);
                }
            }

            if (!gameActive) gameStarted();

            updateBoard(board);
            return;
        }

        if (e.hasCurrentTurn()) {
            if (!gameActive) gameStarted();
            setCurrentTurn(e.getCurrentTurn());
            return;
        }

        if (e.hasStatus()) {
            showError(e.getStatus());
            return;
        }

        if (e.hasWinner()) {
            gameOverInternal(e.getWinner());
            return;
        }
    }

    @Override
    public void onError(Throwable t) {
      showError("Connection error: " + t.getMessage());
    }

    @Override
    public void onCompleted() {
      showError("Server closed connection");
    }
  }

  // ---------------------------------------------------------------------
  // CLEANUP
  // ---------------------------------------------------------------------
  @Override
  public void dispose() {
    try {
      if (blockingStub != null && playerName != null) {
        blockingStub.disconnect(DisconnectRequest.newBuilder().setPlayer(playerName).build());
      }
      if (channel != null) channel.shutdownNow();

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
