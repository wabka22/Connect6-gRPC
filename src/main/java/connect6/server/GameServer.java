package connect6.server;

import connect6.game.Connect6Game;
import connect6.game.PlaceResult;
import connect6.game.PlayerType;
import connect6.grpc.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameServer {
  private static final Logger LOG = Logger.getLogger(GameServer.class.getName());

  private Connect6Game game;
  private final Map<String, StreamObserver<GameEvent>> clients = new LinkedHashMap<>();
  private boolean gameStarted = false;
  private String currentPlayer;
  private final Map<String, Boolean> rematchRequests = new LinkedHashMap<>();
  private String[] playerOrder;
  private Server server;

  public static void main(String[] args) throws IOException, InterruptedException {
    GameServer gs = new GameServer();
    gs.start();
    gs.blockUntilShutdown();
  }

  private void start() throws IOException {
    int port = ServerConfig.INSTANCE.RMI_PORT;
    server = ServerBuilder.forPort(port).addService(new GameService()).build().start();
    LOG.info("gRPC server started on port " + port);
  }

  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) server.awaitTermination();
  }

  class GameService extends Connect6GameGrpc.Connect6GameImplBase {

    @Override
    public void register(PlayerInfo request, StreamObserver<GameEvent> responseObserver) {
      String player = request.getName();
      synchronized (clients) {
        if (clients.containsKey(player)) {
          sendStatus(responseObserver, "Name already in use");
          return;
        }

        clients.put(player, responseObserver);
        LOG.info("Player connected: " + player);
        sendStatus(responseObserver, "Connected as: " + player);

        if (clients.size() < 2) {
          sendStatus(responseObserver, ServerConfig.INSTANCE.MSG_WAITING_PLAYER);
          return;
        }

        if (!gameStarted) {
          try {
            startGame();
          } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to start game", e);
          }
        }
      }
    }

    @Override
    public void makeMove(Move request, StreamObserver<MoveResult> responseObserver) {
      synchronized (GameServer.this) {
        String player = request.getPlayer();
        if (!gameStarted || !player.equals(currentPlayer)) {
          sendMoveResult(responseObserver, false, "Not your turn or game not started");
          return;
        }

        PlaceResult result = game.placeStone(request.getX(), request.getY());
        if (result != PlaceResult.OK) {
          sendMoveResult(responseObserver, false, "Invalid move: " + result);
          return;
        }

        broadcastBoard();

        if (game.isGameOver()) {
          String winner = game.getWinner();
          broadcastWinner(winner);
          endGame();
          sendMoveResult(responseObserver, true, "Move accepted; game over");
          return;
        }

        if (game.shouldSwitchPlayer()) {
          switchCurrentPlayer();
          game.switchPlayer();
        }

        notifyClients(c -> c.onNext(GameEvent.newBuilder().setCurrentTurn(currentPlayer).build()));
        sendMoveResult(responseObserver, true, "Move accepted");
      }
    }

    @Override
    public void disconnect(DisconnectRequest request, StreamObserver<MoveResult> responseObserver) {
      String player = request.getPlayer();
      synchronized (clients) {
        if (!clients.containsKey(player)) {
          sendMoveResult(responseObserver, false, "Not connected");
          return;
        }

        StreamObserver<GameEvent> so = clients.remove(player);
        rematchRequests.remove(player);
        safeSend(so, "Server: disconnecting");

        LOG.info("Player disconnected: " + player);

        if (gameStarted) handleDisconnectDuringGame();
        if (!gameStarted && clients.size() >= 2) startGame();

        sendMoveResult(responseObserver, true, "Disconnected");
      }
    }

    @Override
    public void requestRematch(
        RematchRequest request, StreamObserver<MoveResult> responseObserver) {
      String player = request.getPlayer();
      synchronized (clients) {
        if (!clients.containsKey(player)) {
          sendMoveResult(responseObserver, false, "You are not connected");
          return;
        }

        rematchRequests.put(player, true);

        if (rematchRequests.size() >= 2 && rematchRequests.values().stream().allMatch(b -> b)) {
          LOG.info("Starting rematch...");
          startGame();
        }
        sendMoveResult(responseObserver, true, "Rematch request received");
      }
    }

    private void handleDisconnectDuringGame() {
      if (clients.size() == 1) {
        String remaining = clients.keySet().iterator().next();
        StreamObserver<GameEvent> remainingObs = clients.get(remaining);
        safeSend(remainingObs, ServerConfig.INSTANCE.MSG_PLAYER_DISCONNECTED);
        safeSendWinner(remainingObs, "OPPONENT_DISCONNECTED");
        endGame();
      } else if (clients.size() < 2) {
        endGame();
      }
    }
  }

  private void startGame() {
    if (clients.size() < 2) return;

    game = new Connect6Game();
    gameStarted = true;
    rematchRequests.clear();

    playerOrder = clients.keySet().toArray(new String[0]);
    currentPlayer = playerOrder[0];

    clients
        .get(playerOrder[0])
        .onNext(GameEvent.newBuilder().setRole(PlayerType.BLACK.name()).build());
    clients
        .get(playerOrder[1])
        .onNext(GameEvent.newBuilder().setRole(PlayerType.WHITE.name()).build());

    notifyClients(c -> sendStatus(c, "Game started!"));
    sendCurrentTurn(currentPlayer);
    broadcastBoard();

    LOG.info("New game started between " + playerOrder[0] + " and " + playerOrder[1]);
  }

  private void endGame() {
    gameStarted = false;
    currentPlayer = null;
    game = null;
    rematchRequests.clear();
    playerOrder = new String[0];
  }

  private void switchCurrentPlayer() {
    if (playerOrder.length < 2) return;
    currentPlayer = currentPlayer.equals(playerOrder[0]) ? playerOrder[1] : playerOrder[0];
  }

  private void broadcastBoard() {
    if (game == null) return;
    char[][] boardCopy = game.getBoard();
    notifyClients(
        c -> c.onNext(GameEvent.newBuilder().setBoard(boardProtoFromChar(boardCopy)).build()));
  }

  private void broadcastWinner(String winner) {
    notifyClients(c -> safeSendWinner(c, winner));
  }

  private void notifyClients(Consumer<StreamObserver<GameEvent>> action) {
    List<StreamObserver<GameEvent>> copy;
    synchronized (clients) {
      copy = new ArrayList<>(clients.values());
    }
    for (StreamObserver<GameEvent> c : copy) {
      try {
        action.accept(c);
      } catch (Exception e) {
        LOG.log(Level.WARNING, "Failed to notify client", e);
      }
    }
  }

  private Board boardProtoFromChar(char[][] board) {
    Board.Builder b = Board.newBuilder();
    for (char[] rowArr : board) {
      Row.Builder row = Row.newBuilder();
      for (char cell : rowArr) row.addCells(String.valueOf(cell));
      b.addRows(row);
    }
    return b.build();
  }

  private void sendStatus(StreamObserver<GameEvent> obs, String msg) {
    safeSend(obs, msg);
  }

  private void sendCurrentTurn(String player) {
    StreamObserver<GameEvent> obs = clients.get(player);
    if (obs != null) obs.onNext(GameEvent.newBuilder().setCurrentTurn(player).build());
  }

  private void safeSend(StreamObserver<GameEvent> obs, String msg) {
    if (obs != null) {
      try {
        obs.onNext(GameEvent.newBuilder().setStatus(msg).build());
      } catch (Exception ignored) {
      }
    }
  }

  private void safeSendWinner(StreamObserver<GameEvent> obs, String winner) {
    if (obs != null) {
      try {
        obs.onNext(GameEvent.newBuilder().setWinner(winner).build());
      } catch (Exception ignored) {
      }
    }
  }

  private void sendMoveResult(StreamObserver<MoveResult> obs, boolean success, String msg) {
    if (obs != null) {
      obs.onNext(MoveResult.newBuilder().setSuccess(success).setMessage(msg).build());
      obs.onCompleted();
    }
  }
}
