// ref: https://gist.github.com/CarlEkerot/2693246
import java.io.IOException;
import java.lang.ClassNotFoundException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server extends Thread {
    private ServerSocket server;
    private int port;
    private int numOfClient = 0;

    public int getNumOfClient() {
        return numOfClient;
    }

    public void setNumOfClient(int numOfClient) {
        this.numOfClient = numOfClient;
    }

    public void run() {
       while (true) {
           try {
               Socket client = server.accept();
           } catch (IOException e) {
               e.printStackTrace();
           }
       }
    }

    public void main(String args[]) throws IOException, ClassNotFoundException {
        port = Integer.valueOf(args[0]);
        server = new ServerSocket(port);
        System.out.println("Shutting down socket server!!");
        server.close();
    }
}
