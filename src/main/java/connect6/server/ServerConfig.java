package connect6.server;

public enum ServerConfig {
    INSTANCE;

    public final int RMI_PORT = 50051;
    public final String GAME_SERVER_NAME = "Connect6Game";
    public final String MSG_WAITING_PLAYER = "Waiting for another player...";
    public final String MSG_PLAYER_DISCONNECTED = "Opponent disconnected";
}
