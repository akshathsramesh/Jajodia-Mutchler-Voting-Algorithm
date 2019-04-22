import java.io.*;
import java.util.*;
// Data structure to store Shared variables that help in running JM-DLV algorithm
public class dataStructJMVA
{
    // ME is my_id
    public int ME;
    // JMV algorithm DS 
    // logical version number: updated after gathering info from other sites
    public int LVN;
    // physical version number: updated only when updating file physically at this site
    public int PVN;
    // site cardinality / replicas updates in current partition
    public int RU;
    // distinguished site ID
    public int DS;

    // counters to identify end of a transaction based on connections available in current partition
    public int target_msg_count;
    public int received_msg_count;

    // file locked? boolean flag to atomicize transaction (based on 2-phase commit protocol)
    public boolean locked;
    HashMap<Integer, DSmessage> voteInfo = null;
    // constructor takes site ID
    dataStructJMVA(int ME)
    {
        this.LVN = 0;
        this.PVN = 0;
        this.RU = 8;
        this.DS = 0;
        this.locked= false;
        this.target_msg_count = -1;
        this.received_msg_count = 0;
        this.ME = ME;
        voteInfo = new HashMap<Integer, DSmessage>();
    }

    public void clearVoteInfo(){
        voteInfo = new HashMap<Integer, DSmessage>();
    }
}
