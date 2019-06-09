package dapp;

import java.net.Socket;

import java.io.*;
import java.util.*;

public class Master extends Server {
    private List<String> listSlaveServerPort = new ArrayList<String>();
    private List<String> listSlaveClientPort = new ArrayList<String>();
    private Map<Integer, Integer> slaveCapacities = new HashMap<Integer, Integer>();

    public Master(int port) {
        super(port);
        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader("/Users/vincent/git/dapp_project/src/dapp/server_list.txt")
            );
            String line = reader.readLine();

            while (line != null) {
                listSlaveClientPort.add(line);
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.logger.info("Master Created (C Port: " + this.clientThreadPort
                + ", S Port: " + this.serverThreadPort + ")");
        this.logger.info("Available server ports are " + this.listSlaveClientPort);
    }

    private boolean IsServer(Socket client) {
        String slaveClientPort = Integer.toString(client.getPort());
        String slaveServerPort = Integer.toString(client.getPort() - 1000);

        // Loop: https://crunchify.com/how-to-iterate-through-java-list-4-way-to-iterate-through-loop/
        for (int i = 0; i < listSlaveClientPort.size(); i++) {
            String entry = listSlaveClientPort.get(i);
            if (entry.equals(slaveClientPort)) {
                if (!listSlaveServerPort.contains(entry)) {
                    // it will only add port number onetime
                    listSlaveServerPort.add(slaveServerPort);
                }
                return true;
            }
        }
        return false;
    }

    private String updateCapacity(Socket slave, int arg) {
        int port = slave.getPort();
        int count = slaveCapacities.containsKey(port) ? slaveCapacities.get(port) : 0;
        slaveCapacities.put(port, count);
        return "Slave[" + port + "] capacitiy["+ count + "] updated";
    }

    private String parse(String msg, Socket slave) {
        String[] commands = msg.split(":");
        String ret;

        switch(commands[0]) {
            case "updateCapacity":
                ret = updateCapacity(slave, Integer.valueOf(commands[1]));
                break;
            case "init":
                ret = "Slave attached to Master";
                break;
            default:
                ret = "Invalid Command";
        }
        return ret;
    }

    public void handleSlave(Socket slave) {
        String message, ret;
        ObjectInputStream ois;
        ObjectOutputStream oos;

        try {
            ois = new ObjectInputStream(slave.getInputStream());
            message = (String) ois.readObject();
            // parse message
            ret = parse(message, slave);
            oos = new ObjectOutputStream(slave.getOutputStream());
            oos.writeObject(ret);
            ois.close();
            oos.close();
            slave.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void handleClient(Socket client) {

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
            if (IsServer(client)) {
                this.logger.info("Slave[" + client.getPort() + "] connected");
                handleSlave(client);
            } else {
                this.logger.info("Client[" + client.getPort() + "] connected");
                handleClient(client);
            }
        }
    }

    @Override
    public void clientRun() {

    }

    @Override
    public void start() {
        (new Daemon(this, "serverRun")).start();
    }

    public static void main(String[] args) {
        Master m = new Master(7000);
        m.start();
    }
}
