package connect6.server;

import connect6.game.Connect6Game;
import connect6.game.PlaceResult;
import connect6.game.PlayerType;
import connect6.rmi.RemoteClientInterface;
import connect6.rmi.RemoteServerInterface;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameServer implements RemoteServerInterface {

  private static final Logger LOG = Logger.getLogger(GameServer.class.getName());

  private Connect6Game game;
  private final Map<String, RemoteClientInterface> clients = new LinkedHashMap<>();
  private boolean gameStarted = false;
  private String currentPlayer;
  private final Map<String, Boolean> rematchRequests = new LinkedHashMap<>();
  private static Registry registry;
  private String[] playerOrder;

  public static void main(String[] args) {
    System.setProperty("java.security.policy", "server.policy");
    try {
      GameServer server = new GameServer();
      RemoteServerInterface stub =
          (RemoteServerInterface) UnicastRemoteObject.exportObject(server, 0);
      registry = LocateRegistry.createRegistry(ServerConfig.INSTANCE.RMI_PORT);
      registry.rebind(ServerConfig.INSTANCE.GAME_SERVER_NAME, stub);
      LOG.info("Server started on port " + ServerConfig.INSTANCE.RMI_PORT);
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Server error", e);
    }
  }

  private void notifyClients(RemoteAction action) {
    for (RemoteClientInterface client : clients.values()) {
      try {
        action.run(client);
      } catch (RemoteException e) {
        LOG.log(Level.WARNING, "Failed to notify client", e);
      }
    }
  }

  private boolean validateClient(RemoteClientInterface client, String playerName)
      throws RemoteException {
    if (clients.containsKey(playerName)) {
      client.showError("Name already in use. Choose another.");
      return false;
    }
    return true;
  }

  @Override
  public synchronized void registerClient(RemoteClientInterface client, String playerName)
      throws RemoteException {
    if (!validateClient(client, playerName)) return;

    clients.put(playerName, client);
    LOG.info("Player connected: " + playerName);

    if (clients.size() < 2) {
      client.showError(ServerConfig.INSTANCE.MSG_WAITING_PLAYER);
      return;
    }

    if (!gameStarted) startGame();
  }

  private void startGame() throws RemoteException {
    if (clients.size() < 2) return;

    game = new Connect6Game();
    gameStarted = true;
    rematchRequests.clear();

    playerOrder = clients.keySet().toArray(new String[0]);
    currentPlayer = playerOrder[0];

    clients.get(playerOrder[0]).setPlayerRole(PlayerType.BLACK.name());
    clients.get(playerOrder[1]).setPlayerRole(PlayerType.WHITE.name());

    notifyClients(RemoteClientInterface::gameStarted);
    clients.get(currentPlayer).setCurrentTurn(currentPlayer);
    broadcastBoard();

    LOG.info("New game started between " + playerOrder[0] + " and " + playerOrder[1]);
  }

  @Override
  public synchronized void makeMove(String playerName, int x, int y) throws RemoteException {
    if (!gameStarted || !playerName.equals(currentPlayer)) return;

    PlaceResult result = game.placeStone(x, y);
    if (result != PlaceResult.OK) {
      clients.get(playerName).showError("Invalid move: " + result);
      return;
    }

    broadcastBoard();

    if (game.isGameOver()) {
      notifyClients(c -> c.gameOver(game.getWinner()));
      endGame();
      return;
    }

    if (game.shouldSwitchPlayer()) {
      switchCurrentPlayer();
      game.switchPlayer();
    }

    notifyClients(c -> c.setCurrentTurn(currentPlayer));
  }

  @Override
  public synchronized void disconnect(String playerName) throws RemoteException {
    if (!clients.containsKey(playerName)) return;

    clients.remove(playerName);
    rematchRequests.remove(playerName);
    LOG.info("Player disconnected: " + playerName);

    if (gameStarted) {
      if (clients.size() == 1) {
        String remaining = clients.keySet().iterator().next();
        RemoteClientInterface winner = clients.get(remaining);
        if (winner != null) {
          winner.showError(ServerConfig.INSTANCE.MSG_PLAYER_DISCONNECTED);
          winner.gameOver("OPPONENT_DISCONNECTED");
        }
        endGame();
      } else if (clients.size() < 2) {
        endGame();
      }
    }

    if (!gameStarted && clients.size() >= 2) {
      startGame();
    }
  }

  private void endGame() {
    gameStarted = false;
    currentPlayer = null;
    game = null;
    rematchRequests.clear();
    playerOrder = new String[0];
  }

  @Override
  public synchronized void requestRematch(String playerName) throws RemoteException {
    if (!clients.containsKey(playerName)) return;

    rematchRequests.put(playerName, true);
    if (rematchRequests.size() >= 2 && rematchRequests.values().stream().allMatch(b -> b)) {
      LOG.info("Starting rematch...");
      startGame();
    }
  }

  private void broadcastBoard() {
    if (game == null) return;
    char[][] boardCopy = game.getBoard();
    notifyClients(c -> c.updateBoard(boardCopy));
  }

  private void switchCurrentPlayer() {
    if (playerOrder == null || playerOrder.length < 2) return;
    currentPlayer = currentPlayer.equals(playerOrder[0]) ? playerOrder[1] : playerOrder[0];
  }
}
