// ref: https://gist.github.com/CarlEkerot/2693246
import java.io.IOException;
import java.lang.ClassNotFoundException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Server extends Thread {
    private ServerSocket server;
    private int port;
    private int numOfClient = 0;
    private final int LIMIT = 4;

    public Server(int port) {
        this.port = port;
        try {
            server = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getNumOfClient() {
        return numOfClient;
    }

    public void setNumOfClient(int numOfClient) {
        this.numOfClient = numOfClient;
    }

    private boolean IsFull() {
        if (this.numOfClient > this.LIMIT) {
            return true;
        } else {
            return false;
        }
    }

    public void sendMsg(Socket client, String[] msgArr) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(client.getOutputStream());
        oos.writeObject(msgArr);
    }

    public void run() {
       while (true) {
           try {
               Socket client = server.accept();
               if (IsFull()) {
                   String[] msgArr = new String[]{"full", "7001"};
                   sendMsg(client, msgArr);
               }
               setNumOfClient(getNumOfClient() + 1);
               // transfer();

           } catch (IOException e) {
               e.printStackTrace();
           }
       }
    }

    public void main(String[] args) throws IOException, ClassNotFoundException {
        port = Integer.valueOf(args[0]);
        Server s = new Server(port);
        s.start();

        System.out.println("Shutting down socket server!!");
        server.close();
    }
}
