package dapp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.*;

public class Client {
    private Socket socket;
    private String serverHost;
    private int serverPort;
    private String[] args;
    private Logger logger;

    ObjectInputStream ois;
    ObjectOutputStream oos;

    public Client(String serverHost, int serverPort, String[] args) {
        this.args = args;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.logger = Logger.getLogger(this.getClass().getName());
    }

    public void connect() {
        try {
            this.socket = new Socket(this.serverHost, this.serverPort);
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String[] parse(String msg) {
        return msg.split(":");
    }

    private void reconnect(String host, String port) {
        try {
            this.socket = new Socket(host, Integer.valueOf(port));
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void checkReconn() {
        String res;
        String[] cmds;

        try {
            oos.writeObject("checkReconnect");
            // read response from master node and if it is reconnect then
            // invoke reconnect, close the socket
            res = (String) ois.readObject();
            cmds = parse(res);
            if (cmds[0].equalsIgnoreCase("reconnect")) {
                logger.info("reconnect");
                oos.writeObject("exit");
                oos.close();
                ois.close();
                socket.close();
                reconnect(cmds[1], cmds[2]);
            }
            logger.info(cmds[0]);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void help() {
        logger.info("help()");
    }

    private void cmdExit() {
        String[] cmds;
        String res;

        try {
            oos.writeObject("exit");

            // read response from master node and if it is reconnect then
            // invoke reconnect, close the socket
            res = (String) ois.readObject();
            cmds = parse(res);
            logger.info(cmds[0]);
            oos.close();
            ois.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void cmdList() {
        String[] cmds;
        String res;

        try {
            oos.writeObject("list");

            // read response from master node and if it is reconnect then
            // invoke reconnect, close the socket
            res = (String) ois.readObject();
            cmds = parse(res);
            logger.info(cmds[0]);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void cmdDownload(String filename) {

    }

    private void cmdUpload(String filename) {
    }

    public void command() {
        switch (args[0]) {
            case "list":
                cmdList();
                break;
            case "upload":
                cmdUpload(args[1]);
                break;
            case "download":
                cmdDownload(args[1]);
                break;
            default:
                help();
        }
        cmdExit();
        try {
            ois.close();
            oos.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Client c = new Client("localhost", 7000, args);
        c.connect();

        // if the server is not full then it would not reconnect
        c.checkReconn();
        c.command();
    }
}
