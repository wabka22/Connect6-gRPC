package connect6.game;

public class Connect6Game {
  private final char[][] board;
  private PlayerType currentPlayer;
  private boolean gameOver;
  private String winner;
  private int stonesPlacedThisTurn;
  private boolean isFirstTurn;

  public Connect6Game() {
    board = new char[GameConfig.CFG.BOARD_SIZE][GameConfig.CFG.BOARD_SIZE];
    resetGame();
  }

  private void initializeBoard() {
    for (int r = 0; r < GameConfig.CFG.BOARD_SIZE; r++) {
      for (int c = 0; c < GameConfig.CFG.BOARD_SIZE; c++) {
        board[r][c] = GameConfig.CFG.EMPTY_CELL;
      }
    }
  }

  public synchronized PlaceResult placeStone(int x, int y) {
    if (gameOver) return PlaceResult.GAME_OVER;
    if (!isValidPosition(x, y)) return PlaceResult.INVALID_POSITION;
    if (board[y][x] != GameConfig.CFG.EMPTY_CELL) return PlaceResult.CELL_OCCUPIED;

    board[y][x] =
        (currentPlayer == PlayerType.BLACK)
            ? GameConfig.CFG.PLAYER1_STONE
            : GameConfig.CFG.PLAYER2_STONE;

    stonesPlacedThisTurn++;

    if (checkWin(x, y)) {
      gameOver = true;
      winner = currentPlayer.name();
    }

    return PlaceResult.OK;
  }

  public synchronized boolean shouldSwitchPlayer() {
    return stonesPlacedThisTurn
        >= (isFirstTurn ? GameConfig.CFG.FIRST_TURN_STONES : GameConfig.CFG.NORMAL_TURN_STONES);
  }

  public synchronized void switchPlayer() {
    if (gameOver) return;
    currentPlayer = (currentPlayer == PlayerType.BLACK) ? PlayerType.WHITE : PlayerType.BLACK;
    stonesPlacedThisTurn = 0;
    isFirstTurn = false;
  }

  private boolean checkWin(int x, int y) {
    char stone = board[y][x];

    for (int[] d : GameConfig.CFG.DIRECTIONS) {
      int count = 1;
      count += countInDirection(x, y, d[0], d[1], stone);
      count += countInDirection(x, y, -d[0], -d[1], stone);
      count--;
      if (count >= GameConfig.CFG.WIN_COUNT) return true;
    }
    return false;
  }

  private int countInDirection(int x, int y, int dx, int dy, char stone) {
    int count = 0;
    int nx = x + dx;
    int ny = y + dy;
    while (isValidPosition(nx, ny) && board[ny][nx] == stone) {
      count++;
      nx += dx;
      ny += dy;
    }
    return count;
  }

  private static boolean isValidPosition(int x, int y) {
    return x >= 0 && x < GameConfig.CFG.BOARD_SIZE && y >= 0 && y < GameConfig.CFG.BOARD_SIZE;
  }

  public synchronized char[][] getBoard() {
    int n = GameConfig.CFG.BOARD_SIZE;
    char[][] copy = new char[n][n];
    for (int i = 0; i < n; i++) {
      System.arraycopy(board[i], 0, copy[i], 0, n);
    }
    return copy;
  }

  public synchronized boolean isGameOver() {
    return gameOver;
  }

  public synchronized String getWinner() {
    return winner;
  }

  public synchronized void resetGame() {
    initializeBoard();
    currentPlayer = PlayerType.BLACK;
    gameOver = false;
    winner = null;
    stonesPlacedThisTurn = 0;
    isFirstTurn = true;
  }
}
