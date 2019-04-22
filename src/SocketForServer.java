import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SocketForServer {
    Socket otherClient;
    String my_id;
    String remote_id;
    BufferedReader in;
    PrintWriter out;
    Server my_master;

    public String getRemote_id() {
        return remote_id;
    }

    public void setRemote_id(String remote_id) {
        this.remote_id = remote_id;
    }

    public SocketForServer(Socket otherClient, String myId, Boolean isInitiator, Server my_master) {
        this.otherClient = otherClient;
        this.my_id = myId;
        this.my_master = my_master;
        try{
            in = new BufferedReader(new InputStreamReader(this.otherClient.getInputStream()));
            out = new PrintWriter(this.otherClient.getOutputStream(), true);
        }
        catch (Exception e){

        }

        try {
            if(!isInitiator) {
                out.println("SEND_YOUR_ID");
                System.out.println("SEND_YOUR_ID request sent");
                remote_id = in.readLine();
                System.out.println("SEND_YOUR_ID request response received with ID: " + remote_id);
            }
        }

        catch (Exception e){

        }
        Thread read = new Thread(){
            public void run(){
                while(rx_cmd(in,out) != 0) { }
            }
        };
        read.setDaemon(true); 	// terminate when main ends
        read.start();
    }


    public int rx_cmd(BufferedReader cmd,PrintWriter out) {
        try {
            String cmd_in = cmd.readLine();
            if(cmd_in.equals("SEND_YOUR_ID")){
                System.out.println("Received SEND_YOUR_ID - Replying with my ID: " + this.my_id);
                out.println(this.my_id);
            }
            else if(cmd_in.equals("VOTE_REQUEST")){
                System.out.println("Received VOTE_REQUEST from S"+this.remote_id);
                boolean lock = false;
                synchronized(my_master.votingAlgo.controlWord)
                {
                    lock = my_master.votingAlgo.controlWord.locked;
                }
                if(lock){
                    System.out.println("SITE ALREADY LOCKED");
                    System.out.println("DISCARDING REQUEST");
                } else {
                    int LVN =0;
                    int PVN =0;
                    int RU  =0;
                    int DS  =0;
                    synchronized(my_master.votingAlgo.controlWord)
                    {
                        my_master.votingAlgo.controlWord.locked = true;
                        System.out.println("SITE LOCKED for request to update");
                        LVN = my_master.votingAlgo.controlWord.LVN;
                        PVN = my_master.votingAlgo.controlWord.PVN;
                        RU  = my_master.votingAlgo.controlWord.RU;
                        DS  = my_master.votingAlgo.controlWord.DS;
                        sendInfoReply(LVN,PVN,RU,DS);
                    }
                }
            }
            else if(cmd_in.equals("INFO_REPLY")){
                System.out.println("Received INFO_REPLY from S"+this.remote_id);
                int LVN = Integer.valueOf(in.readLine());
                int PVN = Integer.valueOf(in.readLine());
                int RU  = Integer.valueOf(in.readLine());
                int DS  = Integer.valueOf(in.readLine());
                my_master.processInfoReply(this.remote_id, LVN, PVN, RU, DS);
            }
            else if(cmd_in.equals("ABORT")){
                System.out.println("Received ABORT from S"+this.remote_id);
                synchronized(my_master.votingAlgo.controlWord)
                {
                    my_master.votingAlgo.controlWord.locked = false;
                    System.out.println("SITE UNLOCKED due to ABORT");
                }
            }

        }
        catch (Exception e){}
        return 1;
    }

    public synchronized void sendVoteRequest(){
        System.out.println("send VOTE_REQUEST to" + this.remote_id);
        out.println("VOTE_REQUEST");
    }
    public synchronized void sendAbort(){
        System.out.println("send ABORT to" + this.remote_id);
        out.println("ABORT");
    }
    public synchronized void sendInfoReply(int LVN,int PVN,int RU,int DS){
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
}
