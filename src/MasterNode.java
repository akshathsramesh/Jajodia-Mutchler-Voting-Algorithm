import javafx.scene.shape.PathElement;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MasterNode {

    String Id;
    String ipAddress;
    String port;
    Node masterNode;
    List<Node> allServerNodes = new LinkedList<>();
    List<SocketForMasterNode> socketConnectionListServer = new LinkedList<>();
    ServerSocket server;
    HashMap<String, SocketForMasterNode> socketConnectionHashMapServer = new HashMap<>();


    public MasterNode(String id) {
        this.Id = id;
    }

    public String getId() {
        return this.Id;
    }


    public List<Node> getAllServerNodes() {
        return allServerNodes;
    }


    /* Command Parser to look for input fom terminal once the client is running*/
    public class CommandParser extends Thread {

        MasterNode current;

        public CommandParser(MasterNode current) {
            this.current = current;
        }

        /*Pattern matching, command parsing from terminal*/
        Pattern STATUS = Pattern.compile("^STATUS$");
        Pattern TEST_CONNECTION = Pattern.compile("^TEST_CONNECTION$");

        int rx_cmd(Scanner cmd) {
            String cmd_in = null;
            if (cmd.hasNext())
                cmd_in = cmd.nextLine();
            Matcher m_STATUS = STATUS.matcher(cmd_in);
            Matcher m_TEST_CONNECTION = TEST_CONNECTION.matcher(cmd_in);


            if (m_STATUS.find()) {
                System.out.println("MASTER SOCKET STATUS:");
                try {
                    System.out.println("STATUS:  UP");
                    System.out.println("MASTER ID: " + Id);
                    System.out.println("MASTER IP ADDRESS: " + ipAddress);
                    System.out.println("MASTER PORT: " + port);
                } catch (Exception e) {
                    System.out.println("SOMETHING WENT WRONG IN TERMINAL COMMAND PROCESSOR");
                }
            }
            else if(m_TEST_CONNECTION.find()){
                Integer severId;
                for (severId =0 ; severId < socketConnectionListServer.size(); severId+=1){
                    socketConnectionListServer.get(severId).testConnection();
                }
            }

            return 1;
        }

        public void run() {
            Scanner input = new Scanner(System.in);
            while (rx_cmd(input) != 0) {
            }
        }
    }


    /*Helps establish the socket connection to all the servers available*/
    public void setupServerConnection(MasterNode current) {
        try {
            System.out.println("CONNECTING SERVERS WITH MASTER");
            Integer serverId;
            for (serverId = 0; serverId < allServerNodes.size(); serverId++) {
                Socket serverConnection = new Socket(this.allServerNodes.get(serverId).getIpAddress(), Integer.valueOf(this.allServerNodes.get(serverId).getPort()));
                SocketForMasterNode socketConnectionServer = new SocketForMasterNode(serverConnection, this.getId(), current);
                if (socketConnectionServer.getRemote_id() == null) {
                    socketConnectionServer.setRemote_id(Integer.toString(serverId));
                }
                socketConnectionListServer.add(socketConnectionServer);
                socketConnectionHashMapServer.put(socketConnectionServer.getRemote_id(), socketConnectionServer);
            }
        } catch (Exception e) {
            System.out.println("Setup Server Connection Failure");
        }

    }

    /*Used to create client listen socket and use the listener to add requesting socket connection*/
    public void masterSocket(Integer masterId, MasterNode current) {
        try {
            current.setMasterNode();
            server = new ServerSocket(Integer.valueOf(masterNode.getPort()));
            Id = Integer.toString(masterId);
            ipAddress = masterNode.getIpAddress();
            port = masterNode.getPort();
            System.out.println("MasterNode node running on port ****" + masterNode.getPort() + "," + "*** use ctrl-C to end");
            InetAddress myip = InetAddress.getLocalHost();
            String ip = myip.getHostAddress();
            String hostname = myip.getHostName();
            System.out.println("Your current IP address : " + ip);
            System.out.println("Your current Hostname : " + hostname);
        } catch (IOException e) {
            System.out.println("Error creating socket");
            System.exit(-1);
        }

        CommandParser cmdpsr = new CommandParser(current);
        cmdpsr.start();
    }


    public void setMasterNode(){
        try {
            BufferedReader br = new BufferedReader(new FileReader("config_master.txt"));
            System.out.println("reading file.......");
            try {
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();

                while (line != null) {
                    sb.append(line);
                    List<String> parsed_server = Arrays.asList(line.split(","));
                    this.masterNode = new Node(parsed_server.get(0), parsed_server.get(1), parsed_server.get(2));
                    sb.append(System.lineSeparator());
                    line = br.readLine();
                }
                String everything = sb.toString();
                System.out.println("Added master node information : "+ everything);

            } finally {
                br.close();
            }
        } catch (Exception e) {
            System.out.println("Error while setting up the master node information");
        }
    }

    /*Consume the server config file and save the information*/
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
                System.out.println("____________________________");
                System.out.println(everything);
                System.out.println("____________________________");
                System.out.println("******** NUMBER OF SERVERS: " + this.getAllServerNodes().size());
                System.out.println("____________________________");

            } finally {
                br.close();
            }
        } catch (Exception e) {
        }

    }


    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Usage: java MasterNode <Master-ID>");
            System.exit(1);
        }


        System.out.println("Starting the MasterNode");

        MasterNode M1 = new MasterNode(args[0]); // Create MasterNode instance//Reads from client file and adds it to list of clients
        M1.setServerList(); // Reads from config_server file and adds it to list of server
        M1.setupServerConnection(M1); // Used the method to establish TCP connection to server
        M1.masterSocket(Integer.valueOf(args[0]), M1); // Reserve socket with port number
        System.out.println("Started MasterNode with ID: " + M1.getId());
    }
}
