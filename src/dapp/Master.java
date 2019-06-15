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
                    new FileReader("/home/jonghyeon/git/dapp/src/dapp/server_list.txt")
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
        byte[] buffer = new byte[4096];
        String ret="";
        int read;

        try {

            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            FileInputStream fis = new FileInputStream("dapp/storage/" + filename);

            // upload file from client to server
            while ((read=fis.read(buffer)) > 0) {
                logger.info(""+read);
                dos.write(buffer, 0, read);
            }

            // fis.close();
            // dos.close();

            // read response from master node and if it is reconnect then
            // invoke reconnect, close the socket
            ret = "Succeeded file download. Filename: " + filename;
            logger.info(ret);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    private String upload(Socket socket, String filename) {
        byte[] buffer = new byte[4096];
        int read, totalRead = 0;
        String[] path;
        String ret="";

        path = filename.split("/");
        filename = path[path.length -1];

        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            FileOutputStream fos = new FileOutputStream( "dapp/storage/" + filename);

            while((read = dis.read(buffer,0 ,buffer.length)) > 0 ) {
                totalRead += read;
                logger.info("read " + totalRead + "bytes.");
                fos.write(buffer, 0, read);

                if(read < buffer.length)
                    break;
            }

            // fos.close();
            // dis.close();

            ret = "Succeeded file upload. Filename: " + filename;
            logger.info(ret);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ret;
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
        String[] message = new String[3];

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
                message[0] = "reconnect:127.0.0.1:" + port;
                oos = new ObjectOutputStream(socket.getOutputStream());
                oos.writeObject(message);

                // This client is not useful anymore so we need to disconnect
                oos.close();
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

    public void handleClient(Socket client) {
        String  ret;
        Object message;

        // handle request from client
        try {
            message =  ois.readObject();

            if (message instanceof String) {
                ret = handleCommand(parse((String)message), client);
            } else {
                ret = handleCommand((String[]) message, client);
            }

            // download and upload should do different method
            oos.writeObject(ret);

            if (ret.equalsIgnoreCase("keep-alive")) {
                listClient.add(client);
            } else if (ret.equalsIgnoreCase("exit")) {
                oos.close();
                ois.close();
                client.close();
                listClient.remove(client);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        }

    }

    @Override
    public void serverRun() {
        Socket socket = null;
        int numOfClient = 0;
        while (true) {
            numOfClient = listClient.size();
            if (numOfClient == 0) {
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
            // I guess we should check every time
            if (isServer(socket)) {
                this.logger.info("Slave[" + socket.getPort() + "] connected");
                handleSlave(socket);

            } else {
                if (numOfClient <= 0)
                    this.logger.info("Client[" + socket.getPort() + "] connected");
                logger.info("numOfClient: " + numOfClient);
                handleClient(socket);
            }
        }
    }

    @Override
    public void clientRun() {
//        while(true) {
//            for (int i = 0; i < listClient.size(); i++) {
//                logger.info("clientRun...");
//                handleClient(listClient.get(i));
//            }
//        }
    }

    @Override
    public void start() {
        (new Daemon(this, "serverRun")).start();
//        (new Daemon(this, "clientRun")).start();
    }

    public static void main(String[] args) {
        Master m = new Master(7000);
        m.start();
    }
}
