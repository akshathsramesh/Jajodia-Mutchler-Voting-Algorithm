import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;

public class SocketForServer {
    Socket otherClient;
    String my_id;
    String remote_id;
    BufferedReader in;
    PrintWriter out;
    Server my_master;

    public SocketForServer(Socket otherClient, String myId, Boolean isInitiator, Server my_master) {
        this.otherClient = otherClient;
        this.my_id = myId;
        this.my_master = my_master;
        try {
            in = new BufferedReader(new InputStreamReader(this.otherClient.getInputStream()));
            out = new PrintWriter(this.otherClient.getOutputStream(), true);
        } catch (Exception e) {

        }

        try {
            if (!isInitiator) {
                out.println("SEND_YOUR_ID");
                System.out.println("SEND_YOUR_ID request sent");
                remote_id = in.readLine();
                System.out.println("SEND_YOUR_ID request: response received with ID: " + remote_id);
                out.println("CONNECTION_ACK");
            }
        } catch (Exception e) {

        }
        Thread read = new Thread() {
            public void run() {
                while (rx_cmd(in, out) != 0) {
                }
            }
        };

        read.setDaemon(true);    // terminate when main ends
        read.start();
    }

    public String getRemote_id() {
        return remote_id;
    }

    public void setRemote_id(String remote_id) {
        this.remote_id = remote_id;
    }

    public int rx_cmd(BufferedReader cmd, PrintWriter out) {
        try {
            String cmd_in = cmd.readLine();
            if (cmd_in.equals("SEND_YOUR_ID")) {
                System.out.println("Received SEND_YOUR_ID - Replying with my ID: " + this.my_id);
                out.println(this.my_id);
            // send vote information to requestor
            } else if (cmd_in.equals("VOTE_REQUEST")) {
                System.out.println("Received VOTE_REQUEST from S" + this.remote_id);
                boolean lock = false;
                synchronized (my_master.votingAlgo.controlWord) {
                    lock = my_master.votingAlgo.controlWord.locked;
                }
                if (lock) {
                    System.out.println("SITE ALREADY LOCKED");
                    System.out.println("DISCARDING REQUEST");
                } else {
                    int LVN = 0;
                    int PVN = 0;
                    int RU = 0;
                    int DS = 0;
                    synchronized (my_master.votingAlgo.controlWord) {
                        my_master.votingAlgo.controlWord.locked = true;
                        System.out.println("SITE LOCKED for request to update");
                        LVN = my_master.votingAlgo.controlWord.LVN;
                        PVN = my_master.votingAlgo.controlWord.PVN;
                        RU = my_master.votingAlgo.controlWord.RU;
                        DS = my_master.votingAlgo.controlWord.DS;
                        sendInfoReply(LVN, PVN, RU, DS);
                    }
                }
            // process the reply for vote_request
            } else if (cmd_in.equals("INFO_REPLY")) {
                System.out.println("Received INFO_REPLY from S" + this.remote_id);
                int LVN = Integer.valueOf(in.readLine());
                int PVN = Integer.valueOf(in.readLine());
                int RU = Integer.valueOf(in.readLine());
                int DS = Integer.valueOf(in.readLine());
                my_master.votingAlgo.processInfoReply(this.remote_id, LVN, PVN, RU, DS);
            // handle abort condition
            } else if (cmd_in.equals("ABORT")) {
                System.out.println("Received ABORT from S" + this.remote_id);
                synchronized (my_master.votingAlgo.controlWord) {
                    my_master.votingAlgo.controlWord.locked = false;
                    System.out.println("SITE UNLOCKED due to ABORT");
                }
                my_master.votingAlgo.printSiteStats();
            // send missing updates
            } else if (cmd_in.equals("GET_MISSING_UPDATES")) {
                System.out.println("Received GET_MISSING_UPDATES from S" + this.remote_id);
                int rPVN = Integer.valueOf(in.readLine());
                sendMissingUpdates(rPVN);
            // process missing updates
            } else if (cmd_in.equals("MISSING_UPDATES")) {
                System.out.println("Received MISSING_UPDATES from S" + this.remote_id);
                processMissingUpdates();
                my_master.votingAlgo.printSiteStats();
            // process commit message
            } else if (cmd_in.equals("COMMIT")) {
                System.out.println("Received COMMIT from S" + this.remote_id);
                int LVN = Integer.valueOf(in.readLine());
                int RU = Integer.valueOf(in.readLine());
                int DS = Integer.valueOf(in.readLine());
                String update = in.readLine();
                my_master.votingAlgo.processCommit(this.remote_id, LVN, RU, DS, update);
            } else if (cmd_in.equals("CLOSE_SOCKET")) {
                String serverRequesting = cmd.readLine();
                System.out.println("Received close socket from SERVER_ID " + serverRequesting);
                my_master.processCloseConnectionRequest(serverRequesting);
                out.println("CLOSE_SOCKET_ACK");
                out.println(this.my_id);
                otherClient.close();
                return 0;
            } else if (cmd_in.equals("CLOSE_SOCKET_ACK")) {
                String serverResponding = cmd.readLine();
                System.out.println("Received close socket ACK from SERVER_ID" + serverResponding);
                otherClient.close();
                my_master.processCloseConnectionAck(serverResponding);
                return 0;
            }

            else if(cmd_in.equals("TEST_MASTER_SERVER_CONNECTION")){
                System.out.println("Received test packet for master and sever connection");
            }

            else if (cmd_in.equals("DROP_CONNECTION")){
                String dropConnectionWith = cmd.readLine();
                System.out.println( " DROP request received by " + this.my_id +" to drop connection with " + dropConnectionWith);
                my_master.processDropConnection(dropConnectionWith);
            }

            else if( cmd_in.equals("REJOIN_CONNECTION")){
                String rejoinConnectionWith = cmd.readLine();
                System.out.println("REJOIN request received by " + this.my_id + "to REJOIN connection with " + rejoinConnectionWith);
                my_master.processRejoinConnection(rejoinConnectionWith, my_master);
            }

            else if (cmd_in.equals("AUTO_SETUP")){
                System.out.println("AUTO SETUP RECEIVED AT THE SERVER - TRIGGERING SET UP CONNECTION");
                my_master.autoSetup();
            }

            else if(cmd_in.equals("CONNECTION_ACK")){
                System.out.println("CONNECTION ack received by " + this.my_id);
                my_master.processConnectionAck();
            }
            else if(cmd_in.equals("PING")){
                out.println("PONG");
                out.println(this.my_id);
            }
            else if( cmd_in.equals("PONG")){
                String pongServerId = cmd.readLine();
                System.out.println("RECEIVED PONG FROM " + pongServerId);
            }


        } catch (Exception e) {
            System.out.println("Socket RX_cmd exception: Buffer close couldn't be handled graciously");
            System.out.println("*************** Closing Socket Connection *****************");
            return 0;
        }
        return 1;
    }

    public synchronized void sendVoteRequest() {
        System.out.println("send VOTE_REQUEST to" + this.remote_id);
        out.println("VOTE_REQUEST");
    }

    public synchronized void sendMissingUpdates(int remotePVN) {
        System.out.println("send MISSING_UPDATES to" + this.remote_id);
        out.println("MISSING_UPDATES");
        // send the missing updates based on the received version number
        synchronized (my_master.votingAlgo.controlWord) {
            for(int i = remotePVN-1;i<my_master.votingAlgo.controlWord.Updates.size();i++) {
                out.println(my_master.votingAlgo.controlWord.Updates.get(i));
            }
            // end of transmission
            out.println("EOM");
        }
    }

    public synchronized void processMissingUpdates() {
        System.out.println("processing MISSING_UPDATES got from S" + this.remote_id);
        Pattern eom = Pattern.compile("^EOM");
        String rd_in = null;
        Matcher m_eom = eom.matcher("start");  // initializing the matcher. "start" does not mean anything
        // get updates till EOM message is received and add to updates list
        try
        {
            while(!m_eom.find())
            {
                rd_in = in.readLine();
                m_eom = eom.matcher(rd_in);
                if(!m_eom.find())
                {
                    String update = rd_in;
                    // add the update, increment the version number and write to file
                    synchronized (my_master.votingAlgo.controlWord) {
                        my_master.votingAlgo.controlWord.Updates.add(update);
                        my_master.votingAlgo.writeToFile(my_master.fileObjectName,update);
                        ++my_master.votingAlgo.controlWord.PVN;
                    }
                } 
                else { break; }  // break out of loop when EOM is received
            }
        }
        catch (IOException e) 
        {
        	System.out.println("Read failed");
        	System.exit(-1);
        }
        synchronized (my_master.votingAlgo.controlWord) {
            my_master.votingAlgo.controlWord.notifyAll();
            System.out.println("notify catchup");
        }
    }

    public synchronized void sendAbort() {
        System.out.println("send ABORT to" + this.remote_id);
        out.println("ABORT");
    }

    public synchronized void sendInfoReply(int LVN, int PVN, int RU, int DS) {
        System.out.println("send INFO_REPLY to S" + this.remote_id);
        System.out.println("LVN = " + LVN);
        System.out.println("PVN = " + PVN);
        System.out.println("RU = " + RU);
        System.out.println("DS = " + DS);
        out.println("INFO_REPLY");
        out.println(LVN);
        out.println(PVN);
        out.println(RU);
        out.println(DS);
    }

    public synchronized void sendCommit(int LVN, int RU, int DS, String update) {
        System.out.println("send COMMIT to S" + this.remote_id);
        System.out.println("LVN = " + LVN);
        System.out.println("RU = " + RU);
        System.out.println("DS = " + DS);
        System.out.println("update_command = " + update);
        out.println("COMMIT");
        out.println(LVN);
        out.println(RU);
        out.println(DS);
        out.println(update);
    }

    public synchronized void sendGetMissingUpdates(int PVN) {
        System.out.println("send GET_MISSING_UPDATES to S" + this.remote_id);
        System.out.println("PVN = " + PVN);
        out.println("GET_MISSING_UPDATES");
        out.println(PVN);
    }

    public synchronized void closeSocketServer() {
        System.out.println("Sending out close socket connection to Server " + this.remote_id);
        out.println("CLOSE_SOCKET");
        out.println(this.my_id);

    }

    public synchronized void sendPhaseMoveAck(){
        System.out.println("Sending drop connection phase move ack from server " + this.my_id + " to master node at 100");
        out.println("PHASE_MOVE_ACK");
        out.println(this.my_id);
    }


    public synchronized void sendAutoSetup(String serverID){
        System.out.println("Sending AUTO setUp to Sever with ID: " + serverID);
        out.println("AUTO_SETUP");
    }

    public synchronized void sendPing(){
        out.println("PING");
    }

}
