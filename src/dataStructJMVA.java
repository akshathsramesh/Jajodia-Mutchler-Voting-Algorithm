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

    public int M;

    // counters to identify end of a transaction based on connections available in current partition
    public int target_msg_count;
    public int received_msg_count;

    // file locked? boolean flag to atomicize transaction (based on 2-phase commit protocol)
    public boolean locked;
    public boolean isCopyCurrent;
    HashMap<Integer, DSmessage> voteInfo = null;
    List<Integer> Logical = null;
    List<Integer> Physical= null;
    List<String> Updates= null;
    String potentialUpdate = null;
    // constructor takes site ID
    dataStructJMVA(int ME)
    {
        this.LVN = 1;
        this.PVN = 1;
        this.RU = 8;
        this.DS = 0;
        this.M = -1;
        this.locked= false;
        this.isCopyCurrent= false;
        this.target_msg_count = -1;
        this.received_msg_count = 0;
        this.ME = ME;
        this.voteInfo = new HashMap<Integer, DSmessage>();
        this.Logical  = new LinkedList<Integer>();
        this.Physical = new LinkedList<Integer>();
        this.Updates  = new LinkedList<String>();
    }

    public void clearAllInfo() {
        this.isCopyCurrent = false;
        this.M = -1;
        this.voteInfo.clear();
        this.Logical.clear();
        this.Physical.clear();
    }
}
