package dapp;

import java.net.ServerSocket;
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
                    new FileReader("dapp/server_list.txt")
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

    protected String[] parse(String msg) {
        String[] commands = msg.split(":");
        return commands;

    }

    protected void incNClient(Socket client) {
        listClientLock.lock();
        listClient.add(client);
        numOfClient.getAndIncrement();
        listClientLock.unlock();
    }

    protected void decNClient(Socket client) {
        listClientLock.lock();
        listClient.remove(client);
        numOfClient.getAndDecrement();
        listClientLock.unlock();
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

    protected String list(Socket socket) {
        return "List: blah blah";
    }

    protected String download(Socket socket, String filename) {
        return "Try to send: default.txt";
    }

    protected String upload(Socket socket, String filename) {
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

    private boolean isFull() {
        if (numOfClient.get() == this.LIMIT) {
            return true;
        }
        return false;
    }

    private String checkReconnect(Socket socket) {
        String message;

        if (isFull()) {
            // find an idle slave node
            String port = String.valueOf(Integer.valueOf(findNode()) - 1000);

            // if port is null, it is an error
            if (port.equals("")) {
                logger.warning("addr is null");
                System.exit(-1);
            }
            return "reconnect:127.0.0.1:" + port;
        }
        return "keep-alive";
    }

    protected String handleCommand (String[] cmds, Socket socket) {
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

    public void handleSlave(Socket slave, ObjectInputStream ois, ObjectOutputStream oos) {
        String message, ret;
        String[] commands;

        try {
            message = (String) ois.readObject();
            // parse message
            commands = parse(message);
            ret = handleCommand(commands, slave);
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

    protected boolean handleClient(Socket client, ObjectInputStream ois, ObjectOutputStream oos) {
        String message, ret;

        // handle request from client
        try {
            message = (String) ois.readObject();
            ret = handleCommand(parse(message), client);

            // download and upload should do different method
            oos.writeObject(ret);

            if (ret.equalsIgnoreCase("keep-alive")) {
                incNClient(client);
            } else if (ret.matches("recoonect:127.0.0.1:[0,9]{4}")) {
                oos.close();
                ois.close();
                client.close();
                return false;
            } else if (ret.equalsIgnoreCase("exit")) {
                oos.close();
                ois.close();
                client.close();
                decNClient(client);
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public void serverRun() {
        Socket socket = null;
        ObjectInputStream ois = null;
        ObjectOutputStream oos = null;
        boolean exist = false;
        while (true) {
            if (!exist) {
                // if there is a connected client
                try {
                    logger.info("Ready to accept...");
                    socket = server.accept();
                    ois = new ObjectInputStream(socket.getInputStream());
                    oos = new ObjectOutputStream(socket.getOutputStream());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (!exist) {
                this.logger.info("Client[" + socket.getPort() + "] connected");
                exist = true;
            }
            exist = handleClient(socket, ois, oos);
        }
    }

    public void serverRunForSlave() {
        ServerSocket heartbeat = null;
        Socket socket = null;
        ObjectInputStream ois = null;
        ObjectOutputStream oos = null;
        try {
            heartbeat = new ServerSocket(this.serverThreadPort - 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (true) {
            // if there is a connected client
            try {
                logger.info("Slave Heartbeat...");
                socket = heartbeat.accept();
                ois = new ObjectInputStream(socket.getInputStream());
                oos = new ObjectOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (isServer(socket)) {
                this.logger.info("Slave[" + socket.getPort() + "] connected");
                handleSlave(socket, ois, oos);
            }
        }
    }

    @Override
    public void start() {
        int i = 0;
        for (; i < LIMIT; i++) {
            (new Daemon(this, "serverRun")).start();
        }
        (new Daemon(this, "serverRunForSlave")).start();
    }

    public static void main(String[] args) {
        Master m = new Master(7000);
        m.start();
    }
}
