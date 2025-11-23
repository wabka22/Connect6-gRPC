package connect6.client;

import connect6.game.PlayerType;
import connect6.grpc.*;
import connect6.client.ui.GameClientUI;
import connect6.client.ui.Images;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

public class GameClient extends JFrame {

    private ManagedChannel channel;
    private Connect6GameGrpc.Connect6GameStub asyncStub;
    private Connect6GameGrpc.Connect6GameBlockingStub blockingStub;

    private String playerName;
    private PlayerType playerRole;
    private boolean myTurn = false;
    private boolean gameActive = false;

    private final GameClientUI ui = new GameClientUI();
    private int playerWins = 0;
    private int opponentWins = 0;

    private final AtomicReference<StreamObserver<GameEvent>> currentObserver = new AtomicReference<>(null);

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
        ui.disconnectBtn.addActionListener(e -> onDisconnectClicked());
    }

    private void onConnectClicked() {
        playerName = ui.nameField.getText().trim();
        if (playerName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter player name");
            return;
        }
        connectToServer(playerName);
        if (channel != null && !channel.isShutdown()) {
            ui.connectBtn.setEnabled(false);
            ui.disconnectBtn.setEnabled(true);
        }
    }

    private void connectToServer(String name) {
        try {
            channel = ManagedChannelBuilder.forAddress(ClientConfig.CFG.SERVER_HOST, ClientConfig.CFG.SERVER_PORT)
                    .usePlaintext()
                    .build();
            asyncStub = Connect6GameGrpc.newStub(channel);
            blockingStub = Connect6GameGrpc.newBlockingStub(channel);

            StreamObserver<GameEvent> clientObserver = new StreamObserver<>() {
                @Override
                public void onNext(GameEvent event) {
                    handleGameEvent(event);
                }

                @Override
                public void onError(Throwable t) {
                    SwingUtilities.invokeLater(() -> ui.statusLabel.setText("Connection error: " + t.getMessage()));
                }

                @Override
                public void onCompleted() {
                    SwingUtilities.invokeLater(() -> ui.statusLabel.setText("Server closed connection"));
                }
            };

            asyncStub.register(PlayerInfo.newBuilder().setName(name).build(), clientObserver);
            currentObserver.set(clientObserver);

            SwingUtilities.invokeLater(() -> ui.statusLabel.setText("Connected as: " + name));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Connection failed: " + e.getMessage());
        }
    }

    private void handleGameEvent(GameEvent event) {
        if (event.hasBoard()) {
            Board b = event.getBoard();
            int n = b.getRowsCount();
            char[][] board = new char[n][n];
            for (int i = 0; i < n; i++) {
                Row row = b.getRows(i);
                for (int j = 0; j < row.getCellsCount(); j++) {
                    String cell = row.getCells(j);
                    board[i][j] = (cell.length() > 0) ? cell.charAt(0) : '.';
                }
            }
            SwingUtilities.invokeLater(() -> ui.boardPanel.setBoard(board));
            return;
        }

        if (event.hasStatus()) {
            String s = event.getStatus();
            SwingUtilities.invokeLater(() -> ui.statusLabel.setText(s));
            return;
        }

        if (event.hasRole()) {
            String role = event.getRole();
            SwingUtilities.invokeLater(() -> {
                playerRole = PlayerType.valueOf(role);
                ui.roleLabel.setText("Role: " + playerRole);
                ui.statusLabel.setText("Connected as: " + playerName + " (" + playerRole + ")");
            });
            return;
        }

        if (event.hasCurrentTurn()) {
            String player = event.getCurrentTurn();
            myTurn = player.equals(playerName);
            SwingUtilities.invokeLater(() -> ui.turnLabel.setText(myTurn ? "Your turn (" + playerRole + ")" : "Opponent's turn"));
            gameActive = true;
            return;
        }

        if (event.hasWinner()) {
            String winner = event.getWinner();
            SwingUtilities.invokeLater(() -> handleGameOver(winner));
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
            Move mv = Move.newBuilder().setPlayer(playerName).setX(x).setY(y).build();
            MoveResult res = blockingStub.makeMove(mv);
            if (!res.getSuccess()) {
                JOptionPane.showMessageDialog(this, "Move failed: " + res.getMessage());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Move failed: " + e.getMessage());
        }
    }

    private void handleGameOver(String winner) {
        gameActive = false;
        myTurn = false;

        if (winner.equals(playerRole != null ? playerRole.name() : "") || "OPPONENT_DISCONNECTED".equals(winner)) playerWins++;
        else opponentWins++;

        ui.scoreLabel.setText(getScoreText());

        if ("OPPONENT_DISCONNECTED".equals(winner)) {
            ui.statusLabel.setText("Opponent disconnected. Waiting for new game...");
            playerRole = null;
            return;
        }

        int option = JOptionPane.showConfirmDialog(
                this,
                "Game Over! Winner: " + winner + "\nDo you want to play again?",
                "Game Over",
                JOptionPane.YES_NO_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            try {
                MoveResult r = blockingStub.requestRematch(RematchRequest.newBuilder().setPlayer(playerName).build());
                if (!r.getSuccess()) {
                    showError("Rematch request failed: " + r.getMessage());
                }
            } catch (Exception e) {
                showError("Rematch request failed: " + e.getMessage());
            }
        }
    }

    private void onDisconnectClicked() {
        try {
            if (blockingStub != null && playerName != null) {
                blockingStub.disconnect(DisconnectRequest.newBuilder().setPlayer(playerName).build());
            }
            if (channel != null) {
                channel.shutdownNow();
            }
        } catch (Exception ignored) {
        } finally {
            dispose();
        }
    }

    private void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message == null || message.isEmpty()) {
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
            if (blockingStub != null && playerName != null) {
                try {
                    blockingStub.disconnect(DisconnectRequest.newBuilder().setPlayer(playerName).build());
                } catch (Exception ignored) {
                }
            }
            if (channel != null) {
                channel.shutdownNow();
            }
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
