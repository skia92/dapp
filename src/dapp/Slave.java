package dapp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class Slave extends Master {
    private int masterPort = 7000;
//    private Lock masterSocketLock = new ReentrantLock();
    private Socket master;
    public Slave(int port) {
        super(port);

    }

    private void connect() {
        try {
            master = new Socket(InetAddress.getLocalHost(), this.masterPort,
                    InetAddress.getLocalHost(), this.clientThreadPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void transaction(String command) {
        ObjectOutputStream oos;
        ObjectInputStream ois;
        String message;

        try {
            oos = new ObjectOutputStream(master.getOutputStream());
            oos.writeObject(command);
            ois = new ObjectInputStream(master.getInputStream());
            message = (String) ois.readObject();
            this.logger.info("Server - " + message);
            oos.close();
            ois.close();
            master.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String handleCommand (String[] cmds, Socket socket) {
        String ret;
        switch(cmds[0]) {
            case "updateCapcity":
                updateCapacity();
            case "list":
                ret = list(socket);
                break;
            case "download":
                ret = download(socket, cmds[1]);
                break;
            case "upload":
                ret = upload(socket, cmds[1]);
                break;
            case "exit":
                ret = "exit";
                break;
            default:
                ret = "Invalid Command";
        }
        return ret;
    }

    @Override
    protected void handleClient(Socket client) {
        String message, ret;

        while (numOfClient > 0) {
            // handle request from client
            try {
                message = (String) ois.readObject();
                ret = handleCommand(parse(message), client);

                // download and upload should do different method
                oos.writeObject(ret);
                if (ret.equalsIgnoreCase("exit")) {
                    oos.close();
                    ois.close();
                    client.close();
                    listClient.remove(client);
                    numOfClient--;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void serverRun() {
        Socket client = null;
        while (true) {
            try {
                logger.info("Ready to accept...");
                client = server.accept();
                oos = new ObjectOutputStream(client.getOutputStream());
                ois = new ObjectInputStream(client.getInputStream());
                listClient.add(client);
                numOfClient++;
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.logger.info("Client[" + client.getPort() + "] connected");
            handleClient(client);
        }
    }

    public void updateCapacity() {
        while(true) {
            connect();
            transaction("updateCapacity:" + this.listClient.size());
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void start() {
        (new Daemon(this, "serverRun")).start();
        // (new Daemon(this, "clientRun")).start();
        (new Daemon(this, "updateCapacity")).start();
    }

    public static void main(String[] args) {
        Slave s = new Slave(Integer.valueOf(args[0]));
        s.start();
    }
}
