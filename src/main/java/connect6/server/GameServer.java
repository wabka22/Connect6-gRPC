package connect6.server;

import connect6.client.ClientConfig;
import connect6.server.ServerConfig;
import connect6.game.Connect6Game;
import connect6.game.PlaceResult;
import connect6.game.PlayerType;
import connect6.grpc.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;


public class GameServer {
    private static final Logger LOG = Logger.getLogger(GameServer.class.getName());

    private final Map<String, StreamObserver<GameEvent>> clients = new LinkedHashMap<>();
    private final Map<String, Boolean> rematchRequests = new LinkedHashMap<>();
    private Connect6Game game;
    private boolean gameStarted = false;
    private String[] playerOrder = new String[0];
    private String currentPlayer;

    private Server server;

    public static void main(String[] args) throws IOException, InterruptedException {
        GameServer gs = new GameServer();
        gs.start();
        gs.blockUntilShutdown();
    }

    private void start() throws IOException {
        int port = ServerConfig.INSTANCE.RMI_PORT;
        server = ServerBuilder.forPort(port)
                .addService(new GameService())
                .build()
                .start();
        LOG.info("gRPC server started, listening on " + port);
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    class GameService extends Connect6GameGrpc.Connect6GameImplBase {

        @Override
        public void register(PlayerInfo request, StreamObserver<GameEvent> responseObserver) {
            String player = request.getName();
            synchronized (clients) {
                if (clients.containsKey(player)) {
                    // send error status and complete
                    GameEvent err = GameEvent.newBuilder().setStatus("Name already in use").build();
                    responseObserver.onNext(err);
                    responseObserver.onCompleted();
                    return;
                }

                clients.put(player, responseObserver);
                LOG.info("Player connected: " + player);

                // send basic connected status
                GameEvent status = GameEvent.newBuilder()
                        .setStatus("Connected as: " + player)
                        .build();
                responseObserver.onNext(status);

                if (clients.size() < 2) {
                    // waiting for second player
                    responseObserver.onNext(GameEvent.newBuilder().setStatus(ServerConfig.INSTANCE.MSG_WAITING_PLAYER).build());
                    return;
                }

                // if two clients and no game started -> start
                if (!gameStarted) {
                    try {
                        startGame();
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Failed to start game", e);
                    }
                } else {
                    // if game already started and more than 2 clients, we currently support two players only.
                    responseObserver.onNext(GameEvent.newBuilder().setStatus("Server supports two players only for now").build());
                }
            }
        }

        @Override
        public void makeMove(Move request, StreamObserver<MoveResult> responseObserver) {
            String player = request.getPlayer();
            synchronized (GameServer.this) {
                if (!gameStarted || !player.equals(currentPlayer)) {
                    responseObserver.onNext(MoveResult.newBuilder().setSuccess(false).setMessage("Not your turn or game not started").build());
                    responseObserver.onCompleted();
                    return;
                }

                PlaceResult r = game.placeStone(request.getX(), request.getY());
                if (r != PlaceResult.OK) {
                    responseObserver.onNext(MoveResult.newBuilder().setSuccess(false).setMessage("Invalid move: " + r).build());
                    responseObserver.onCompleted();
                    return;
                }

                // broadcast board
                broadcastBoard();

                // check game over
                if (game.isGameOver()) {
                    String winner = game.getWinner();
                    broadcastWinner(winner);
                    endGame();
                    responseObserver.onNext(MoveResult.newBuilder().setSuccess(true).setMessage("Move accepted; game over").build());
                    responseObserver.onCompleted();
                    return;
                }

                // maybe switch player
                if (game.shouldSwitchPlayer()) {
                    switchCurrentPlayer();
                    game.switchPlayer();
                }

                // notify current turn
                notifyClients(c -> c.onNext(GameEvent.newBuilder().setCurrentTurn(currentPlayer).build()));

                responseObserver.onNext(MoveResult.newBuilder().setSuccess(true).setMessage("Move accepted").build());
                responseObserver.onCompleted();
            }
        }

        @Override
        public void requestRematch(RematchRequest request, StreamObserver<MoveResult> responseObserver) {
            String player = request.getPlayer();
            synchronized (clients) {
                if (!clients.containsKey(player)) {
                    responseObserver.onNext(MoveResult.newBuilder().setSuccess(false).setMessage("You are not connected").build());
                    responseObserver.onCompleted();
                    return;
                }
                rematchRequests.put(player, true);
                if (rematchRequests.size() >= 2 && rematchRequests.values().stream().allMatch(b -> b)) {
                    LOG.info("Starting rematch...");
                    try {
                        startGame();
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Rematch start failed", e);
                        responseObserver.onNext(MoveResult.newBuilder().setSuccess(false).setMessage("Rematch failed").build());
                        responseObserver.onCompleted();
                        return;
                    }
                }
                responseObserver.onNext(MoveResult.newBuilder().setSuccess(true).setMessage("Rematch request received").build());
                responseObserver.onCompleted();
            }
        }

        @Override
        public void disconnect(DisconnectRequest request, StreamObserver<MoveResult> responseObserver) {
            String player = request.getPlayer();
            synchronized (clients) {
                if (!clients.containsKey(player)) {
                    responseObserver.onNext(MoveResult.newBuilder().setSuccess(false).setMessage("Not connected").build());
                    responseObserver.onCompleted();
                    return;
                }
                // close observer to that client
                StreamObserver<GameEvent> so = clients.remove(player);
                rematchRequests.remove(player);
                if (so != null) {
                    try {
                        so.onNext(GameEvent.newBuilder().setStatus("Server: disconnecting").build());
                        so.onCompleted();
                    } catch (Exception ignored) {
                    }
                }

                LOG.info("Player disconnected: " + player);

                if (gameStarted) {
                    if (clients.size() == 1) {
                        // remaining player wins by opponent disconnect
                        String remaining = clients.keySet().iterator().next();
                        StreamObserver<GameEvent> remainingObs = clients.get(remaining);
                        if (remainingObs != null) {
                            remainingObs.onNext(GameEvent.newBuilder().setStatus(ServerConfig.INSTANCE.MSG_PLAYER_DISCONNECTED).build());
                            remainingObs.onNext(GameEvent.newBuilder().setWinner("OPPONENT_DISCONNECTED").build());
                        }
                        endGame();
                    } else if (clients.size() < 2) {
                        endGame();
                    }
                }

                // if not started and enough players -> start
                if (!gameStarted && clients.size() >= 2) {
                    try {
                        startGame();
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Start after disconnect failed", e);
                    }
                }

                responseObserver.onNext(MoveResult.newBuilder().setSuccess(true).setMessage("Disconnected").build());
                responseObserver.onCompleted();
            }
        }
    }

    private void startGame() {
        synchronized (clients) {
            if (clients.size() < 2) return;
            game = new Connect6Game();
            gameStarted = true;
            rematchRequests.clear();

            playerOrder = clients.keySet().toArray(new String[0]);
            currentPlayer = playerOrder[0];

            // send roles
            StreamObserver<GameEvent> first = clients.get(playerOrder[0]);
            StreamObserver<GameEvent> second = clients.get(playerOrder[1]);

            if (first != null) first.onNext(GameEvent.newBuilder().setRole(PlayerType.BLACK.name()).build());
            if (second != null) second.onNext(GameEvent.newBuilder().setRole(PlayerType.WHITE.name()).build());

            // notify started
            notifyClients(c -> c.onNext(GameEvent.newBuilder().setStatus("Game started!").build()));

            // set initial turn
            StreamObserver<GameEvent> cur = clients.get(currentPlayer);
            if (cur != null) cur.onNext(GameEvent.newBuilder().setCurrentTurn(currentPlayer).build());

            // broadcast initial empty board
            broadcastBoard();

            LOG.info("New game started between " + playerOrder[0] + " and " + playerOrder[1]);
        }
    }

    private void endGame() {
        gameStarted = false;
        currentPlayer = null;
        game = null;
        rematchRequests.clear();
        playerOrder = new String[0];
    }

    private void switchCurrentPlayer() {
        if (playerOrder == null || playerOrder.length < 2) return;
        currentPlayer = currentPlayer.equals(playerOrder[0]) ? playerOrder[1] : playerOrder[0];
    }

    private void broadcastBoard() {
        if (game == null) return;
        char[][] boardCopy = game.getBoard();
        GameEvent boardEvent = GameEvent.newBuilder().setBoard(boardProtoFromChar(boardCopy)).build();
        notifyClients(c -> c.onNext(boardEvent));
    }

    private void broadcastWinner(String winner) {
        notifyClients(c -> {
            c.onNext(GameEvent.newBuilder().setWinner(winner).build());
        });
    }

    private void notifyClients(java.util.function.Consumer<StreamObserver<GameEvent>> action) {
        // iterate over copy to be safe from concurrent modification
        List<StreamObserver<GameEvent>> copy;
        synchronized (clients) {
            copy = new ArrayList<>(clients.values());
        }
        for (StreamObserver<GameEvent> c : copy) {
            try {
                action.accept(c);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed notify client", e);
            }
        }
    }

    private Board boardProtoFromChar(char[][] board) {
        Board.Builder b = Board.newBuilder();
        for (int r = 0; r < board.length; r++) {
            Row.Builder row = Row.newBuilder();
            for (int c = 0; c < board[r].length; c++) {
                row.addCells(String.valueOf(board[r][c]));
            }
            b.addRows(row);
        }
        return b.build();
    }
}
