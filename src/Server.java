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
    List<SocketForServer> serverSocketConnectionList = new LinkedList<>();
    HashMap<String, SocketForServer> serverSocketConnectionHashMap = new HashMap<>();
    JMVAlgorithm votingAlgo = null;

    public String getId() {
        return Id;
    }

    public List<Node> getAllServerNodes() {
        return allServerNodes;
    }

    public void createVotingAlgorithmInstance() {
        this.votingAlgo = new JMVAlgorithm(this);
    }

    public class CommandParser extends Thread {

        Server currentServer;

        public CommandParser(Server currentServer) {
            this.currentServer = currentServer;
        }

        /*Command parser for server terminal */
        Pattern STATUS = Pattern.compile("^STATUS$");
        Pattern SETUP = Pattern.compile("^SETUP$");
        Pattern WRITE = Pattern.compile("^WR$");
        Pattern LIST = Pattern.compile("^LIST$");
        Pattern CLOSE_SOKCET = Pattern.compile("^CLOSE_SOKCET$");

        int rx_cmd(Scanner cmd) {
            String cmd_in = null;
            if (cmd.hasNext())
                cmd_in = cmd.nextLine();
            Matcher m_STATUS = STATUS.matcher(cmd_in);
            Matcher m_LIST = LIST.matcher(cmd_in);
            Matcher m_SETUP = SETUP.matcher(cmd_in);
            Matcher m_WRITE = WRITE.matcher(cmd_in);
            Matcher m_CLOSE_SOCKET = CLOSE_SOKCET.matcher(cmd_in);


            if (m_STATUS.find()) {
                System.out.println("SERVER SOCKET STATUS:");
                try {
                    System.out.println("STATUS UP");
                    System.out.println("SERVER ID: " + Id);
                    System.out.println("SERVER IP ADDRESS: " + ipAddress);
                    System.out.println("SERVER PORT: " + port);
                } catch (Exception e) {
                    System.out.println("SOMETHING WENT WRONG IN TERMINAL COMMAND PROCESSOR");
                }
            } else if (m_SETUP.find()) {
                setupConnections(currentServer);
            } else if (m_WRITE.find()) {
                currentServer.votingAlgo.requestUpdate();
            } else if (m_LIST.find()) {
                synchronized (serverSocketConnectionHashMap) {
                    System.out.println("\n=== Connections to servers ===");
                    serverSocketConnectionHashMap.keySet().forEach(key -> {
                        System.out.println("key:" + key + " => ID " + serverSocketConnectionHashMap.get(key).remote_id);
                    });
                    System.out.println("=== size =" + serverSocketConnectionHashMap.size());
                }
            } else if (m_CLOSE_SOCKET.find()) {
                testCloseSocket();
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

    public void setupConnections(Server current) {
        try {
            System.out.println("CONNECTING SERVER");
            Integer serverId;
            for (serverId = Integer.valueOf(this.Id) + 1; serverId < allServerNodes.size(); serverId++) {
                Socket serverConnection = new Socket(this.allServerNodes.get(serverId).getIpAddress(), Integer.valueOf(allServerNodes.get(serverId).getPort()));
                SocketForServer socketForServer = new SocketForServer(serverConnection, this.getId(), true, current);
                if (socketForServer.getRemote_id() == null) {
                    socketForServer.setRemote_id(Integer.toString(serverId));
                }
                serverSocketConnectionList.add(socketForServer);
                serverSocketConnectionHashMap.put(socketForServer.getRemote_id(), socketForServer);
            }
        } catch (Exception e) {

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


    // check node lock and process vote request
    public synchronized void processInfoReply(String requestingClientId, int LVN, int PVN, int RU, int DS) {
        int current = -1;
        int target = 0;
        boolean distinguished = false;
        DSmessage obj = new DSmessage(LVN, PVN, RU, DS);
        System.out.println("processing INFO_REPLY from S" + requestingClientId);
        System.out.println("LVN = " + LVN);
        System.out.println("PVN = " + PVN);
        System.out.println("RU = " + RU);
        System.out.println("DS = " + DS);
        synchronized (votingAlgo.controlWord) {
            ++votingAlgo.controlWord.received_msg_count;
            votingAlgo.controlWord.voteInfo.put(Integer.valueOf(requestingClientId), obj);
            current = votingAlgo.controlWord.received_msg_count;
            target = votingAlgo.controlWord.target_msg_count;
        }
        if (target == current) {
            System.out.println("received all INFO_REPLY messages for current partition");
            // check and proceed with next phase of voting algorithm
            // check if distinguished partition

            //distinguished == isDistinguished();

            if (distinguished) {
                // do more steps
            } else {
                //release lock and send abort to all in current partition
                System.out.println("Not a distinguished partition: send ABORT to subordinates");
                votingAlgo.releaseAbort();
            }
        }
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
                        serverSocketConnectionList.add(socketForServer);
                        serverSocketConnectionHashMap.put(socketForServer.getRemote_id(), socketForServer);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        current_node.setDaemon(true);
        current_node.start();
    }


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
}
