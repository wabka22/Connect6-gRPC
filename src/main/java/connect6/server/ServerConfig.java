package connect6.server;

public enum ServerConfig {
  INSTANCE;

  public final int RMI_PORT = 1099;
  public final String GAME_SERVER_NAME = "Connect6Game";
  public final int MAX_PLAYERS = 2;

  public final String MSG_SERVER_FULL =
      "Server is full. Maximum " + MAX_PLAYERS + " players allowed.";
  public final String MSG_WAITING_PLAYER = "Waiting for another player...";
  public final String MSG_PLAYER_DISCONNECTED =
      "Opponent disconnected. Waiting for another player...";
}
