package connect6.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteClientInterface extends Remote {
  void updateBoard(char[][] board) throws RemoteException;

  void setPlayerRole(String role) throws RemoteException;

  void setCurrentTurn(String player) throws RemoteException;

  void gameStarted() throws RemoteException;

  void gameOver(String winner) throws RemoteException;

  void showError(String message) throws RemoteException;
}
