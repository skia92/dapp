package dapp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class Slave extends Server {
    private int masterPort = 7000;
    private Lock masterSocketLock = new ReentrantLock();
    private Socket master;
    public Slave(int port) {
        super(port);

    }

    public void connect() {
        masterSocketLock.lock();
        try {
            master = new Socket(InetAddress.getLocalHost(), this.masterPort,
                    InetAddress.getLocalHost(), this.clientThreadPort);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            masterSocketLock.unlock();
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
            ois.close();
            oos.close();
            master.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    public void init() {
        this.connect();
        if (master == null) {
            this.logger.log(Level.WARNING, "Slave failed to connect with Master");
            System.exit(-1);
        } else {
            transaction("init");
        }
    }

    @Override
    public void serverRun() {
        Socket client = null;
        while (true) {
            try {
                client = server.accept();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateCapacity() {
        while(true) {
            if (master.isClosed()) {
                this.logger.info("Reconnecting to Master...");
                this.connect();
            }
            transaction("updateCapacity:" + this.listClient.size());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void start() {
        init();
        (new Daemon(this, "serverRun")).start();
        // (new Daemon(this, "clientRun")).start();
        (new Daemon(this, "updateCapacity")).start();
    }

    public static void main(String[] args) {
        Slave s = new Slave(Integer.valueOf(args[0]));
        s.start();
    }
}
