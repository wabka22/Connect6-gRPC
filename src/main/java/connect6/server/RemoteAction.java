package connect6.server;

import connect6.rmi.RemoteClientInterface;
import java.rmi.RemoteException;

@FunctionalInterface
public interface RemoteAction {
  void run(RemoteClientInterface client) throws RemoteException;
}
