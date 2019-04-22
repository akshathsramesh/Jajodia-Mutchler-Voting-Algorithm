import java.io.*;
import java.util.*;
public class DSmessage
{
    // logical version number: updated after gathering info from other sites
    public int LVN;
    // physical version number: updated only when updating file physically at this site
    public int PVN;
    // site cardinality / replicas updates in current partition
    public int RU;
    // distinguished site ID
    public int DS;

    DSmessage(int LVN,int PVN,int RU, int DS)
    {
        this.LVN = LVN;
        this.PVN = PVN;
        this.RU = RU;
        this.DS = DS;
    }
}
