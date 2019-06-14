package dapp;

import java.net.Socket;
import java.io.*;
import java.util.*;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

public class Master extends Server {
    private List<String> listSlaveServerPort = new ArrayList<String>();
    private List<String> listSlaveClientPort = new ArrayList<String>();
    private Map<String, Integer> slaveCapacities = new HashMap<String, Integer>();

    public Master(int port) {
        super(port);
        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader("/src/dapp/server_list.txt")
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

    private String[] parse(String msg) {
        String[] commands = msg.split(":");
        return commands;

    }

    private boolean isServer(Socket client) {
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
        String port = String.valueOf(slave.getPort());
        int count = slaveCapacities.containsKey(port) ? slaveCapacities.get(port) : 0;
        slaveCapacities.put(port, count);
        return "Slave[" + port + "] capacitiy["+ count + "] updated";
    }

    private String list(Socket socket) {
        return "List: blah blah";
    }

    private String download(Socket socket, String filename) {
        return "Try to send: default.txt";
    }

    private String upload(Socket socket, String filename) {
        return "Uploaded: default.txt";
    }

    private String findNode() {
        String ret = "";
        // https://www.javacodegeeks.com/2017/09/java-8-sorting-hashmap-values-ascending-descending-order.html
        Map<String, Integer> sorted = slaveCapacities
                .entrySet()
                .stream()
                .sorted(comparingByValue())
                .collect(
                        toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new)
                );

        for (Map.Entry<String, Integer> entry : slaveCapacities.entrySet()) {
            ret = entry.getKey();
            break;
        }
        return ret;
    }

    private String checkReconnect(Socket socket) {
        String message;
        ObjectOutputStream oos;

        if (isFull()) {
            logger.info("isFull");
            // find an idle slave node
            String port = findNode();

            // if port is null, it is an error
            if (port.equals("")) {
                logger.warning("addr is null");
                System.exit(-1);
            }
            try {
                // Send a slave server port to be reconnected
                message = "reconnect:127.0.0.1:" + port;
                oos = new ObjectOutputStream(socket.getOutputStream());
                oos.writeObject(message);

                // This client is not useful anymore so we need to disconnect
                socket.close();
                return "reconnect";
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "keep-alive";
    }

    private String handleCommand (String[] cmds, Socket socket) {
        String ret;
        switch(cmds[0]) {
            case "updateCapacity":
                ret = updateCapacity(socket, Integer.valueOf(cmds[1]));
                break;
            case "init":
                ret = "Slave attached to Master";
                break;
            case "list":
                ret = list(socket);
                break;
            case "download":
                ret = download(socket, cmds[1]);
                break;
            case "upload":
                ret = upload(socket, cmds[1]);
                break;
            case "checkReconnect":
                ret = checkReconnect(socket);
                break;
            case "exit":
                ret = "exit";
                break;
            default:
                ret = "Invalid Command";
        }
        return ret;
    }

    public void handleSlave(Socket slave) {
        String message, ret;
        String[] commands;
        ObjectInputStream ois;
        ObjectOutputStream oos;

        try {
            ois = new ObjectInputStream(slave.getInputStream());
            message = (String) ois.readObject();
            // parse message
            commands = parse(message);
            ret = handleCommand(commands, slave);
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
        String message, ret;
        ObjectInputStream ois;
        ObjectOutputStream oos;

        // handle request from client
        try {
            ois = new ObjectInputStream(client.getInputStream());
            message = (String) ois.readObject();
            ret = handleCommand(parse(message), client);

            // download and upload should do different method
            oos = new ObjectOutputStream(client.getOutputStream());
            oos.writeObject(ret);

            if (!ret.equalsIgnoreCase("keep-alive")) {
                client.close();
            } else if (ret.equalsIgnoreCase("exit")) {
                client.close();
                listClient.remove(client);
            }
            listClient.add(client);
            logger.info("add..." + listClient);

            // clientRun should run first to obtain a socket lock
            (new Daemon(this, "clientRun")).start();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void serverRun() {
        Socket client = null;
        while (true) {
            serverLock.lock();
            try {
                logger.info("Ready to accept...");
                client = server.accept();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (isServer(client)) {
                this.logger.info("Slave[" + client.getPort() + "] connected");
                handleSlave(client);
            } else {
                this.logger.info("Client[" + client.getPort() + "] connected");
                handleClient(client);
            }
            serverLock.unlock();
        }
    }

    @Override
    public void clientRun() {
        while(true) {
            for (int i = 0; i < listClient.size(); i++) {
                serverLock.lock();
                logger.info("clientRun...");
                handleClient(listClient.get(i));
                serverLock.unlock();
            }
        }
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
