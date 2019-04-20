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

        }
        catch (Exception e){}
        return 1;
    }
}
