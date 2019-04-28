import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {

    ServerSocket server;
    String Id;
    String port;
    String ipAddress;
    List<Node> allServerNodes = new LinkedList<>();
    HashMap<String, SocketForServer> serverSocketConnectionHashMap = new HashMap<>();
    HashMap<String, SocketForServer> masterServerSocketConnectionHashMap = new HashMap<>();
    JMVAlgorithm votingAlgo = null;
    String fileObjectName = null;
    Integer dropConnectionCounter;
    Boolean autoConnectionEnabled = true;
    Integer connectionAckReq;

    public static void main(String[] args) {


        if (args.length != 1) {
            System.out.println("Usage: java Server <server-number>");
            System.exit(1);
        }

        System.out.println("Starting the Server");
        Server server = new Server();
        server.setServerList();// set server list
        server.serverSocket(Integer.valueOf(args[0]), server); // reserve a socket
        server.createVotingAlgorithmInstance();
        System.out.println("Started the Server");
    }

    public String getId() {
        return Id;
    }

    public List<Node> getAllServerNodes() {
        return allServerNodes;
    }

    public void createVotingAlgorithmInstance() {
        this.fileObjectName = "file_S"+(this.Id);
        this.votingAlgo = new JMVAlgorithm(this);
    }

    public void setupConnections(Server current) {
        try {
            connectionAckReq = allServerNodes.size() - (Integer.valueOf(this.Id) + 1);
            System.out.println("CONNECTING SERVER");
            Integer serverId;
            for (serverId = Integer.valueOf(this.Id) + 1; serverId < allServerNodes.size(); serverId++) {
                Socket serverConnection = new Socket(this.allServerNodes.get(serverId).getIpAddress(), Integer.valueOf(allServerNodes.get(serverId).getPort()));
                SocketForServer socketForServer = new SocketForServer(serverConnection, this.getId(), true, current);
                if (socketForServer.getRemote_id() == null) {
                    socketForServer.setRemote_id(Integer.toString(serverId));
                }
                synchronized (serverSocketConnectionHashMap) {
                    serverSocketConnectionHashMap.put(socketForServer.getRemote_id(), socketForServer);
                }
            }
        } catch (Exception e) {
            System.out.println("Error while connecting to server");
        }
    }

    public synchronized void autoSetup (){
        System.out.println("Inside auto setup process");
        if(autoConnectionEnabled) {
            this.setupConnections(this);
        }
        else {
            System.out.println("AUTO CONNECTION DISABLED");
        }
    }

    public synchronized void processConnectionAck(){
        System.out.println("Inside process connection ACK");
        this.connectionAckReq -=1;
        if(this.connectionAckReq == 0 && Integer.valueOf(this.Id) < this.allServerNodes.size() - 2 && autoConnectionEnabled){
            System.out.println("---------------- SEND PROGRESSIVE CONNECTION REQUEST -------------------");
            serverSocketConnectionHashMap.get(String.valueOf(Integer.valueOf(this.Id) + 1)).sendAutoSetup(String.valueOf(Integer.valueOf(this.Id) + 1));
        }
    }

    public synchronized void testCloseSocket() {
        serverSocketConnectionHashMap.get("0").closeSocketServer();
    }

    /*reading server file and populating the list*/
    public void setServerList() {
        try {
            BufferedReader br = new BufferedReader(new FileReader("config_server.txt"));
            try {
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();

                while (line != null) {
                    sb.append(line);
                    List<String> parsed_server = Arrays.asList(line.split(","));
                    Node n_server = new Node(parsed_server.get(0), parsed_server.get(1), parsed_server.get(2));
                    this.getAllServerNodes().add(n_server);
                    sb.append(System.lineSeparator());
                    line = br.readLine();
                }
                String everything = sb.toString();
                System.out.println(everything);
                System.out.println(this.getAllServerNodes().size());

            } finally {
                br.close();
            }
        } catch (Exception e) {
        }
    }

    public synchronized void processCloseConnectionAck(String severResponding) {
        this.dropConnectionCounter -= 1;
        System.out.println("Removing Sever Id " + severResponding + " from connection list");
        serverSocketConnectionHashMap.remove(severResponding);
        if (dropConnectionCounter == 0) {
            masterServerSocketConnectionHashMap.get("100").sendPhaseMoveAck();
        }
    }

    public synchronized void processCloseConnectionRequest(String serverRequesting) {
        System.out.println("Removing Sever Id " + serverRequesting + " from connection list");
        serverSocketConnectionHashMap.remove(serverRequesting);
    }

    public synchronized void processDropConnection(String dropConnectionWith) {
        System.out.println("INSIDE PROCESS DROP CONNECTION");
        Integer dropConnectionIndex;
        this.dropConnectionCounter = dropConnectionWith.length();
        for (dropConnectionIndex = 0; dropConnectionIndex < dropConnectionWith.length(); dropConnectionIndex += 1) {
            serverSocketConnectionHashMap.get(String.valueOf(dropConnectionWith.charAt(dropConnectionIndex))).closeSocketServer();
        }

    }

    public synchronized void sendPing(){
        serverSocketConnectionHashMap.keySet().forEach(key -> {
            serverSocketConnectionHashMap.get(key).sendPing();
        });
    }

    public synchronized void processRejoinConnection(String rejoinConnectionWith, Server current) {
        System.out.print("INSIDE REJOIN CONNECTION");
        try {
            System.out.println("CONNECTING SERVER AS PART OF REJOIN CONNECTION");
            Integer serverId;
            for (serverId = Integer.valueOf(this.Id) + 1; serverId < allServerNodes.size(); serverId++) {
                if (rejoinConnectionWith.contains(serverId.toString())) {
                    Socket serverConnection = new Socket(this.allServerNodes.get(serverId).getIpAddress(), Integer.valueOf(allServerNodes.get(serverId).getPort()));
                    SocketForServer socketForServer = new SocketForServer(serverConnection, this.getId(), true, current);
                    if (socketForServer.getRemote_id() == null) {
                        socketForServer.setRemote_id(Integer.toString(serverId));
                    }
                    serverSocketConnectionHashMap.put(socketForServer.getRemote_id(), socketForServer);
                }
                else {
                    System.out.println(serverId.toString() + " is not part of reconnection request");
                }
            }
        } catch (Exception e) {
            System.out.println("Error while connecting to server");
        }
        masterServerSocketConnectionHashMap.get("100").sendPhaseMoveAck();
    }

    /*Open a socket to list to connection request*/
    public void serverSocket(Integer serverId, Server currentServer) {
        try {
            server = new ServerSocket(Integer.valueOf(this.allServerNodes.get(serverId).port));
            Id = Integer.toString(serverId);
            port = this.allServerNodes.get(serverId).port;
            ipAddress = this.allServerNodes.get(serverId).ipAddress;
            System.out.println("Server node running on port " + Integer.valueOf(this.allServerNodes.get(serverId).port) + "," + " use ctrl-C to end");
            InetAddress myServerIp = InetAddress.getLocalHost();
            String ip = myServerIp.getHostAddress();
            String hostname = myServerIp.getHostName();
            System.out.println("Your current Server IP address : " + ip);
            System.out.println("Your current Server Hostname : " + hostname);
        } catch (IOException e) {
            System.out.println("Error creating socket");
            System.exit(-1);
        }

        Server.CommandParser cmdpsr = new Server.CommandParser(currentServer);
        cmdpsr.start();

        Thread current_node = new Thread() {
            public void run() {
                while (true) {
                    try {
                        Socket s = server.accept();
                        SocketForServer socketForServer = new SocketForServer(s, Id, false, currentServer);
                        if(Integer.valueOf(socketForServer.getRemote_id()) <= 7) {
                            serverSocketConnectionHashMap.put(socketForServer.getRemote_id(), socketForServer);
                        } else {
                            masterServerSocketConnectionHashMap.put(socketForServer.getRemote_id(), socketForServer);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        current_node.setDaemon(true);
        current_node.start();
    }

    public class CommandParser extends Thread {

        Server currentServer;
        /*Command parser for server terminal */
        Pattern STATUS = Pattern.compile("^STATUS$");
        Pattern SETUP = Pattern.compile("^SETUP$");
        Pattern WRITE = Pattern.compile("^W$");
        Pattern WRITE_STRING = Pattern.compile("^W (.*)$");
        Pattern LIST = Pattern.compile("^LIST$");
        Pattern CLOSE_SOCKET = Pattern.compile("^CLOSE_SOCKET$");
        Pattern PING = Pattern.compile("^PING$");

        public CommandParser(Server currentServer) {
            this.currentServer = currentServer;
        }

        int rx_cmd(Scanner cmd) {
            String cmd_in = null;
            if (cmd.hasNext())
                cmd_in = cmd.nextLine();
            Matcher m_STATUS = STATUS.matcher(cmd_in);
            Matcher m_LIST = LIST.matcher(cmd_in);
            Matcher m_SETUP = SETUP.matcher(cmd_in);
            Matcher m_WRITE = WRITE.matcher(cmd_in);
            Matcher m_WRITE_STRING = WRITE_STRING.matcher(cmd_in);
            Matcher m_CLOSE_SOCKET = CLOSE_SOCKET.matcher(cmd_in);
            Matcher m_PING = PING.matcher(cmd_in);

            if (m_STATUS.find()) {
                System.out.println("SERVER SOCKET STATUS:");
                try {
                    System.out.println("STATUS UP");
                    System.out.println("SERVER ID: " + Id);
                    System.out.println("SERVER IP ADDRESS: " + ipAddress);
                    System.out.println("SERVER PORT: " + port);
                    System.out.println("CONNECTION WITH SERVER ID: " + serverSocketConnectionHashMap.keySet());
                } catch (Exception e) {
                    System.out.println("SOMETHING WENT WRONG IN TERMINAL COMMAND PROCESSOR");
                }
            } else if (m_SETUP.find()) {
                setupConnections(currentServer);
            } else if (m_WRITE_STRING.find()) {
                currentServer.votingAlgo.requestUpdate(m_WRITE_STRING.group(1));
            } else if (m_WRITE.find()) {
                currentServer.votingAlgo.requestUpdate("NULL");
            } else if (m_LIST.find()) {
                synchronized (serverSocketConnectionHashMap) {
                    System.out.println("\n=== Connections to servers ===");
                    serverSocketConnectionHashMap.keySet().forEach(key -> {
                        System.out.println("key:" + key + " => ID " + serverSocketConnectionHashMap.get(key).remote_id);
                    });
                    System.out.println("=== size =" + serverSocketConnectionHashMap.size());
                }
                synchronized (masterServerSocketConnectionHashMap) {
                    System.out.println("\n=== Connections to masters ===");
                    masterServerSocketConnectionHashMap.keySet().forEach(key -> {
                        System.out.println("key:" + key + " => ID " + masterServerSocketConnectionHashMap.get(key).remote_id);
                    });
                    System.out.println("=== size =" + masterServerSocketConnectionHashMap.size());
                }
                synchronized (votingAlgo.controlWord) {
                    System.out.println("Site STATS");
                    System.out.println("LVN = " + votingAlgo.controlWord.LVN);
                    System.out.println("PVN = " + votingAlgo.controlWord.PVN);
                    System.out.println("RU = " + votingAlgo.controlWord.RU);
                    System.out.println("DS = " + votingAlgo.controlWord.DS);
                    System.out.println("All UPDATES till now :"+votingAlgo.controlWord.Updates);
                    System.out.println("votes received :"+ votingAlgo.controlWord.voteInfo.size());
                    votingAlgo.controlWord.voteInfo.keySet().forEach(key -> {
                        System.out.println("S"+key+" LVN="+votingAlgo.controlWord.voteInfo.get(key).getLVN()+" PVN="+votingAlgo.controlWord.voteInfo.get(key).getPVN()+" RU="+votingAlgo.controlWord.voteInfo.get(key).getRU()+" DS="+votingAlgo.controlWord.voteInfo.get(key).getDS());
                    });
                }
            } else if (m_CLOSE_SOCKET.find()) {
                testCloseSocket();
            }
            else if (m_PING.find()){
                sendPing();
            }
            else {
                System.out.println("Unknown command : enter proper command");
            }

            return 1;
        }

        public void run() {
            System.out.println("Enter commands to set-up MESH Connection : TRIGGER");
            Scanner input = new Scanner(System.in);
            while (rx_cmd(input) != 0) {
            }
        }
    }
}
