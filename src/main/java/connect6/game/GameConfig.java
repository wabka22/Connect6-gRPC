package connect6.game;

public enum GameConfig {
  CFG;

  public final int BOARD_SIZE = 19;
  public final int WIN_COUNT = 6;

  public final char EMPTY_CELL = '.';
  public final char PLAYER1_STONE = 'B';
  public final char PLAYER2_STONE = 'W';

  public final int FIRST_TURN_STONES = 1;
  public final int NORMAL_TURN_STONES = 2;

  public final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
}
