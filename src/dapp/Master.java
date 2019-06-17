package dapp;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;



public class Master extends Server {
    private List<String> listSlaveServerPort = new ArrayList<String>();
    protected List<String> listSlaveClientPort = new ArrayList<String>();
    protected List<Replicate> listReplicate = new ArrayList<Replicate>();
    private Map<String, Integer> slaveCapacities = new HashMap<String, Integer>();

    protected static int masterSPort = 7000;
    protected static int masterCPort = 8000;

    protected ReentrantLock waitFileQLock = new ReentrantLock();
    protected AtomicInteger numOfQFile = new AtomicInteger(0);
    protected List<String> waitFileQueue = new ArrayList<String>();

    public Master(int port) {
        super(port);
        Replicate rPort = new Replicate(masterCPort + 2000, masterSPort + 2000);
        listSlaveClientPort.add(String.valueOf(masterCPort)); // 8000
        listReplicate.add(rPort);

        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader("dapp/server_list.txt")
                    );
            String line = reader.readLine();

            while (line != null) {
                Replicate rSlavePort = new Replicate(Integer.valueOf(line) + 2000,
                        Integer.valueOf(line) + 1000);
                listSlaveClientPort.add(String.valueOf(line)); // 8000
                listReplicate.add(rSlavePort);
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

    public void enqFile(String filename) {
        waitFileQLock.lock();
        waitFileQueue.add(filename);
        numOfQFile.getAndIncrement();
        waitFileQLock.unlock();
    }

    public void deqFile(String filename) {
        waitFileQLock.lock();
        waitFileQueue.remove(filename);
        numOfQFile.getAndDecrement();
        waitFileQLock.unlock();
    }

    public void incNClient(Socket client) {
        listClientLock.lock();
        listClient.add(client);
        numOfClient.getAndIncrement();
        listClientLock.unlock();
    }

    public void decNClient(Socket client) {
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
        String ret = "\n";
        // https://stackoverflow.com/questions/31456898/convert-a-for-loop-to-concat-string-into-a-lambda-expression
        try (Stream<Path> walk = Files.walk(Paths.get("storage"))) {
            List<String> result = walk.filter(Files::isRegularFile)
                    .map(x -> x.toString()).collect(Collectors.toList());
            ret = result.stream()
                    .map(filename -> filename.toString())
                    .collect(Collectors.joining("\n"));

        } catch (IOException e) {
            e.printStackTrace();
        }

        return "\n" + ret;
    }

    protected String download(Socket socket, String filename) {
        byte[] buffer = new byte[4096];
        String ret="";
        int read;

        try {

            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            FileInputStream fis = new FileInputStream("storage/" + filename);

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

    protected String upload(Socket socket, String filename) {
        byte[] buffer = new byte[4096];
        int read, totalRead = 0;
        String[] path;
        String ret="";

        path = filename.split("/");
        filename = path[path.length -1];

        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            FileOutputStream fos = new FileOutputStream( "storage/" + filename);

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

            int replicate = 0;
            for (int i = 0; i < listReplicate.size(); i++) {
                if (socket.getPort() == listReplicate.get(i).cPort)
                    replicate = 1;
            }
            if (replicate == 0)
                enqFile(filename);
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

    private boolean isFull() {
        if (numOfClient.get() == this.LIMIT) {
            return true;
        }
        return false;
    }

    private String checkReconnect(Socket socket) {
        String[] message = new String[3];

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
        String ret;
        Object message;

        // handle request from client
        try {
            message =  ois.readObject();

            if (message instanceof String) {
                ret = handleCommand(parse((String)message), client);
            } else {
                ret = handleCommand((String[]) message, client);
            }

            oos.writeObject(ret);
            if (ret.equalsIgnoreCase("keep-alive")) {
                incNClient(client);
            } else if (ret.matches("reconnect:127.0.0.1:[0-9]{4,5}")) {
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
            System.exit(-1);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
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
            // Well it should be another port but master's clientThreadPort are only used in this context
            // Port: 8000
            heartbeat = new ServerSocket(this.clientThreadPort);
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

    private void replicateUpload(Socket s, String filename) {
        byte[] buffer = new byte[4096];
        int read;
        String msg, res;

        msg = "upload:" + filename;
        try {
            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
            oos.writeObject(msg);

            DataOutputStream dos = new DataOutputStream(s.getOutputStream());
            FileInputStream fis = new FileInputStream(filename);

            // upload file from client to server
            while ((read=fis.read(buffer)) > 0) {
                logger.info("Replicate to [" + s.getPort() + "] Send" + read + "bytes");
                dos.write(buffer, 0, read);
            }

            // fis.close();
            // dos.close();
            res = (String) ois.readObject();
            logger.info(res);
            ois.close();
            oos.close();
            s.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void replicateSendNode() {
        while (true) {
            if (numOfQFile.get() > 0) {
                // FIXME need another lock actually but for now let just do it...
                logger.info("Replicating...");
                String filename = waitFileQueue.get(0);
                for(int i = 0; i < listReplicate.size(); i++) {
                    // Skip sender's replicate server port
                    if (listReplicate.get(i).sPort == this.serverThreadPort + 2000)
                        continue;
                    try {
                        Socket s = new Socket(InetAddress.getLocalHost(), listReplicate.get(i).sPort,
                                InetAddress.getLocalHost(), this.clientThreadPort + 2000);
                        replicateUpload(s, filename);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                deqFile(filename);
                logger.info("??? " + waitFileQueue + "[]" + numOfQFile.get());
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void replicateRecvNode() {
        ServerSocket repServer = null;
        Socket repClient;
        ObjectInputStream ois;
        ObjectOutputStream oos;
        try {
            repServer = new ServerSocket(this.serverThreadPort + 2000);
            logger.info("Ready to accept replication...[" + (this.serverThreadPort + 2000) + "]" );
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (true) {
            // if there is a connected client
            try {
                repClient = repServer.accept();
                ois = new ObjectInputStream(repClient.getInputStream());
                oos = new ObjectOutputStream(repClient.getOutputStream());

                // FIXME We should check slave IP / Port
                handleClient(repClient, ois, oos);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class Replicate {
        int cPort;
        int sPort;

        public Replicate(int cPort, int sPort) {
            this.cPort = cPort;
            this.sPort = sPort;
        }

        private String getCPort() {
            return String.valueOf(cPort);
        }

        private String getSPort() {
            return String.valueOf(sPort);
        }
    }

    @Override
    public void start() {
        int i = 0;
        for (; i < LIMIT + 1; i++) {
            (new Daemon(this, "serverRun")).start();
        }
        (new Daemon(this, "serverRunForSlave")).start();
        (new Daemon(this, "replicateSendNode")).start();
        (new Daemon(this, "replicateRecvNode")).start();
    }

    public static void main(String[] args) {
        Master m = new Master(7000);
        m.start();
    }
}
