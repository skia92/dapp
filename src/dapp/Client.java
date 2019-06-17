package dapp;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.logging.*;
import java.util.stream.Stream;

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

    private void connect() {
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

    private void reconnect(String slaveHost, String slavePort) {
        try {
            this.socket = new Socket(slaveHost, Integer.valueOf(slavePort));
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkReconn() {
        String res;
        String[] cmds;

        try {
            oos.writeObject("checkReconnect");
            // read response from master node and if it is reconnect then
            // invoke reconnect, close the socket
            res = (String) ois.readObject();
            cmds = parse(res);
            if (cmds[0].equalsIgnoreCase("reconnect")) {
                logger.info("Client[" + socket.getLocalPort() + "] "
                        + "reconnect to Slave " + cmds[1] + ":" + cmds[2]);
                oos.close();
                ois.close();
                socket.close();
                reconnect(cmds[1], cmds[2]);
            }
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
            socket.close();
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

    private void cmdDownload(String[] cmds) {
        byte[] buffer = new byte[4096];
        int read, totalRead = 0;
        String ret="", filename = cmds[1];

        try {
            oos.writeObject(cmds);

            DataInputStream dis = new DataInputStream(socket.getInputStream());
            FileOutputStream fos = new FileOutputStream("downloads/" + filename);


            //  dis.read() also receive Server's writeObject message..
            while((read = dis.read(buffer,0 , buffer.length)) > 0 ) {
                totalRead += read;
                logger.info("read " + totalRead + "bytes.");
                fos.write(buffer, 0, read);

                if(read < buffer.length)
                    break;
            }


            fos.close();

            //ret = (String) ois.readObject();
            //  dis.close();

            ret = "Succeeded file download. Filename: " + filename;
            logger.info(cmds[0]);
            logger.info(ret);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void cmdUpload(String[] cmds) {
        byte[] buffer = new byte[4096];
        int read, totalRead = 0;
        String filename = cmds[1];
        String msg = cmds[0] + ":" + cmds[1];
        String res;

        try {
            oos.writeObject(msg);

            DataOutputStream dos = new DataOutputStream(this.socket.getOutputStream());
            FileInputStream fis = new FileInputStream(filename);

            // upload file from client to server
            while ((read=fis.read(buffer)) > 0) {
                totalRead += read;
                logger.info("Send " + totalRead + "bytes");
                dos.write(buffer, 0, read);
            }

            // Why does it have to be commented out?
            fis.close();
            // dos.close();

            res = (String) ois.readObject();
            logger.info(res);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void command() {
        switch (args[0]) {
            case "list":
                cmdList();
                break;
            case "upload":
                cmdUpload(args);
                break;
            case "download":
                cmdDownload(args);
                break;
            default:
                help();
        }
        cmdExit();
    }

    public static void main(String[] args) {
        Client c = new Client("localhost", 7000, args);
        c.connect();

        // if the server is not full then it would not reconnect
        c.checkReconn();
        c.command();
        return;
    }
}
