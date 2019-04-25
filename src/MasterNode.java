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
    List<List<AppCom>> commands = new LinkedList<>();
    Integer requiredPhaseMoveAck = 0;

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
        Pattern PHASE_ONE = Pattern.compile("^PHASE_ONE$");
        Pattern PHASE_TWO = Pattern.compile("^PHASE_TWO$");
        Pattern PHASE_THREE = Pattern.compile("^PHASE_THREE$");

        int rx_cmd(Scanner cmd) {
            String cmd_in = null;
            if (cmd.hasNext())
                cmd_in = cmd.nextLine();
            Matcher m_STATUS = STATUS.matcher(cmd_in);
            Matcher m_TEST_CONNECTION = TEST_CONNECTION.matcher(cmd_in);
            Matcher m_PHASE_ONE = PHASE_ONE.matcher(cmd_in);
            Matcher m_PHASE_TWO = PHASE_TWO.matcher(cmd_in);
            Matcher m_PHASE_THREE = PHASE_THREE.matcher(cmd_in);

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

            else if(m_PHASE_ONE.find()){
                current.moveToOne();
            }

            else if(m_PHASE_TWO.find()){
                current.moveToTwo();

            }
            else if(m_PHASE_THREE.find()){
                current.moveToThree();
            }

            return 1;
        }

        public void run() {
            Scanner input = new Scanner(System.in);
            while (rx_cmd(input) != 0) {
            }
        }
    }


    public void moveToOne(){
        System.out.println(" Moving severs from phase 0 to 1");
        List<AppCom> phaseOneCommands = this.commands.get(0);
        System.out.println(phaseOneCommands.toString());
        this.requiredPhaseMoveAck = 2;
        this.socketConnectionHashMapServer.get("0").dropConnection("0", "12");
        this.socketConnectionHashMapServer.get("1").dropConnection("1","2");

    }

    public void moveToTwo(){
        System.out.println(" Moving servers from phase 1 to 2");
        List<AppCom> phaseTwoCommands = this.commands.get(1);
        System.out.println(phaseTwoCommands.toString());
    }

    public void moveToThree(){
        System.out.println(" Moving servers from phase 2 to 3");
        List<AppCom> phaseThreeCommands = this.commands.get(2);
        System.out.println(phaseThreeCommands.toString());
    }

    public synchronized void processPhaseMoveAck() {
        this.requiredPhaseMoveAck -=1;
        if(this.requiredPhaseMoveAck == 0 ){
            System.out.println("PHASE MOVE COMPLETE");
        }
        else{
            System.out.println("WAITING ON PHASE MOVE COMPLETION BY " + this.requiredPhaseMoveAck + " Number of Severs");
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

    public void setUpCommands(){
        System.out.println("*******SETTING PHASE MOVE COMMANDS *******");
        List<AppCom> phaseOne = new LinkedList<>();
        phaseOne.add(new AppCom("0","4567"));
        phaseOne.add(new AppCom("1","4567"));
        phaseOne.add(new AppCom("2","4567"));
        phaseOne.add(new AppCom("3","4567"));
        this.commands.add(phaseOne);
        List<AppCom> phaseTwo = new LinkedList<>();
        phaseTwo.add(new AppCom("0","123"));
        phaseTwo.add(new AppCom("7","456"));
        this.commands.add(phaseTwo);
        List<AppCom> phaseThree = new LinkedList<>();
        phaseThree.add(new AppCom("1","456"));
        phaseThree.add(new AppCom("2","456"));
        phaseThree.add(new AppCom("3","456"));
        this.commands.add(phaseThree);
        System.out.println("******* PHASE MOVE COMMAND SETUP COMPLETED *******");
    }

    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Usage: java MasterNode <Master-ID>");
            System.exit(1);
        }


        System.out.println("Starting the MasterNode");
        MasterNode M1 = new MasterNode(args[0]); // Create MasterNode instance//Reads from client file and adds it to list of operatingClients
        M1.setServerList();// Reads from config_server file and adds it to list of server
        try {
            TimeUnit.SECONDS.sleep(5);
        }
        catch (Exception e){
            System.out.println("Crashed while wait to read file");
        }
        M1.setupServerConnection(M1); // Used the method to establish TCP connection to server
        M1.masterSocket(Integer.valueOf(args[0]), M1);// Reserve socket with port number
        M1.setUpCommands();
        System.out.println("Started MasterNode with ID: " + M1.getId());
    }
}
