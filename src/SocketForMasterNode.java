import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Date;

/*MasterNode socket connection handler class. This class is used to consume the connection buffer to send out message and redirect to functionality for received messages*/
public class SocketForMasterNode {

    Socket otherClient;
    String my_id;
    String remote_id;
    BufferedReader in;
    PrintWriter out;
    MasterNode my_master;
    String numberOfClients;

    public String getRemote_id() {
        return remote_id;
    }

    public void setRemote_id(String remote_id) {
        this.remote_id = remote_id;
    }

    public Socket getOtherClient() {
        return otherClient;
    }

    public void setOtherClient(Socket otherClient) {
        this.otherClient = otherClient;
    }

    public SocketForMasterNode(Socket otherClient, String myId, MasterNode my_master) {
        this.otherClient = otherClient;
        this.my_id = myId;
        this.my_master = my_master;
        try {
            in = new BufferedReader(new InputStreamReader(this.otherClient.getInputStream()));
            out = new PrintWriter(this.otherClient.getOutputStream(), true);
        } catch (Exception e) {
            System.out.println("Exception while creating buffer in out for socket connection");
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


    public int rx_cmd(BufferedReader cmd, PrintWriter out) {
        try {
            String cmd_in = cmd.readLine();

            if (cmd_in.equals("SEND_YOUR_ID")) {
                System.out.println("Received SEND_YOUR_ID - Replying with my ID: " + this.my_id);
                out.println(this.my_id);
            }

            else if (cmd_in.equals("PHASE_MOVE_ACK")) {
                String ackSeverId = cmd.readLine();
                System.out.println("Phase move ack received sent by server ID " + ackSeverId);
                my_master.processPhaseMoveAck();
            }

        } catch (Exception e) {
        }
        return 1;
    }

    public synchronized void testConnection(){
        System.out.println("Sending .... Master to Server ID test connection packer to server " );
        out.println("TEST_MASTER_SERVER_CONNECTION");
    }

    public synchronized void dropConnection(String toSeverID, String dropConnectionWith){
        System.out.println(" SENDING DROP CONNECTION WITH" + dropConnectionWith + " TO Sever ID " + toSeverID);
        out.println("DROP_CONNECTION");
        out.println(dropConnectionWith);
    }

    public synchronized void rejoinConnection(String toSeverID, String rejoinConnectionWith){
        System.out.println(" SENDING DROP CONNECTION WITH" + rejoinConnectionWith + " TO Sever ID " + toSeverID);
        out.println("REJOIN_CONNECTION");
        out.println(rejoinConnectionWith);
    }


}
