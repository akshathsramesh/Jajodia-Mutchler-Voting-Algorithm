import java.util.Date;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedList;
import java.util.*;
import java.lang.management.*;
import java.lang.*;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.text.*;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;

// class that implements Jajodia-Mutchler dynamic-linear voting algorithm for maintaining consistent file replication in a DDBS
public class JMVAlgorithm
{
    // shared data structure to run algorithm
    dataStructJMVA controlWord = null;
    // handle to the my_master instance
    Server my_master = null;

    JMVAlgorithm(Server my_master)
    {
        this.my_master = my_master;
        this.controlWord = new dataStructJMVA(Integer.valueOf(my_master.Id));
        clearTheFile(my_master.fileObjectName);
    }

    // method to request an update / determine votes
    public void requestUpdate(String Update)
    {
        boolean lock = false;
        int target = 0;
        int ts = 0;
        Pattern NULL = Pattern.compile("^NULL$");
        Matcher m_NULL = NULL.matcher(Update);
        synchronized(controlWord)
        {
            lock = controlWord.locked;
        }

        if(lock){
            System.out.println("SITE ALREADY LOCKED");
            System.out.println("wait till previous voting round to finish");
            return;
        }

        synchronized(controlWord)
        {
            controlWord.locked = true;
            controlWord.clearAllInfo();

            // set variables;
            controlWord.target_msg_count = my_master.serverSocketConnectionHashMap.size();
            controlWord.received_msg_count = 0;
            if (m_NULL.find()) {
                controlWord.potentialUpdate = Integer.toString(controlWord.PVN+1);
            } else {
                controlWord.potentialUpdate = Update;
            }
            System.out.println("New WRITE/UPDATE requested with update string="+controlWord.potentialUpdate);
            System.out.println("SITE LOCKED for new update request");
        }

        // send VOTE_REQUEST message to all sites
        synchronized (my_master.serverSocketConnectionHashMap) {
            my_master.serverSocketConnectionHashMap.keySet().forEach(key -> {
                my_master.serverSocketConnectionHashMap.get(key).sendVoteRequest();
            });
        }

        // only node in the partition
        if(my_master.serverSocketConnectionHashMap.isEmpty()) {
            System.out.println("Only node in the partition!");
            Thread v = new Thread() {
                public void run() {
                    executeVotingAlgorithm();
                }
            };
            v.setDaemon(true);
            v.setName("votingAlgorithm_ThreadInsideAlgo_onlyNodeInPartition");
            v.start();
        }
    }

    public void executeVotingAlgorithm() {
        boolean distinguished = false;
        boolean isCopyCurrent = false;
        synchronized (controlWord) {
            // put my stats also along with others stats in voteInfo
            DSmessage my_obj = new DSmessage(controlWord.LVN, controlWord.PVN, controlWord.RU, controlWord.DS);
            controlWord.voteInfo.put(Integer.valueOf(my_master.Id), my_obj);
        }
        // check if partition is distinguished and proceed further
        distinguished = isDistinguished();

        if (distinguished) {
            // get flag : is file copy current in this site ?
            synchronized (controlWord) {
                isCopyCurrent = controlWord.isCopyCurrent;
            }

            if (isCopyCurrent) {
                System.out.println("File copy is current!");
            } else {
                doCatchUp();
            }

            doUpdateStats();
            // sendMissingUpdates();
            synchronized (controlWord) {
                synchronized (my_master.serverSocketConnectionHashMap) {
                    my_master.serverSocketConnectionHashMap.keySet().forEach(key -> {
                        if (!controlWord.Physical.contains(Integer.valueOf(key))) {
                            //System.out.println("Physical does not contain "+key);
                            my_master.serverSocketConnectionHashMap.get(key).sendMissingUpdates(controlWord.voteInfo.get(Integer.valueOf(key)).getPVN());
                        }
                    });
                }
            }
            // unlock site
            printSiteStats();
            synchronized (controlWord) {
                controlWord.locked = false;
            }
            System.out.println("SITE UNLOCKED due to successful UPDATE and COMMIT");
        } else {
            //release lock and send abort to all in current partition
            System.out.println("Not a distinguished partition: send ABORT to subordinates");
            releaseAbort();
            printSiteStats();
        }
    }

    public boolean isDistinguished() {
        // check if distinguished partition
        boolean exitReturn = false;
        synchronized (controlWord) {
            // get max LVN to M, get the min site ID to S
            controlWord.voteInfo.keySet().forEach(key -> {
                int tempLVN = controlWord.voteInfo.get(key).getLVN();
                int tempS = Integer.valueOf(key);
                if (controlWord.M < tempLVN) {
                    controlWord.M = tempLVN;
                }
                if (controlWord.S > tempS) {
                    controlWord.S = tempS;
                }
            });

            // gather votes
            controlWord.voteInfo.keySet().forEach(key -> {
                int tempLVN = controlWord.voteInfo.get(key).getLVN();
                int tempPVN = controlWord.voteInfo.get(key).getPVN();
                if (tempLVN == controlWord.M) {
                    controlWord.Logical.add(key);
                }
                if (tempPVN == controlWord.M) {
                    controlWord.Physical.add(key);
                }
            });

            System.out.println("Physical = " + controlWord.Physical);
            System.out.println("Logical  = " + controlWord.Logical);
            // physical set contains this site ID ? then current
            controlWord.isCopyCurrent = (controlWord.Physical.contains(Integer.valueOf(my_master.Id)));
            //System.out.println("isCopyCurrent = "+votingAlgo.controlWord.isCopyCurrent);
            //( votingAlgo.controlWord.M == votingAlgo.controlWord.PVN );

            if (controlWord.Physical.isEmpty()) {
                // S is not in a distinguished partition
                exitReturn = false;
            } else {
                // get RU from any site in logical
                int N = controlWord.voteInfo.get(controlWord.Logical.get(0)).getRU();
                int DS = controlWord.voteInfo.get(controlWord.Logical.get(0)).getDS();
                if (controlWord.Logical.size() > (N / 2)) {
                    // S is in a distinguished partition
                    exitReturn = true;
                } else if ((controlWord.Logical.size() == (N / 2)) & (controlWord.Logical.contains(DS))) {
                    // S is in a distinguished partition
                    exitReturn = true;
                } else {
                    // S is not in a distinguished partition
                    exitReturn = false;
                }
            }
        }
        return exitReturn;
    }

    // get missing updates from a site that has latest copy of file
    public void doCatchUp() {
        System.out.println("Getting updates from site that has latest copy!");
        synchronized (controlWord) {
            System.out.println("Older version of file = " + controlWord.PVN);
            synchronized (my_master.serverSocketConnectionHashMap) {
                my_master.serverSocketConnectionHashMap.get(Integer.toString(controlWord.Physical.get(0))).sendGetMissingUpdates(controlWord.PVN);
            }
            try {
                System.out.println("waiting to catchup");
                controlWord.wait();
            } catch (InterruptedException e) {
                System.out.println("interrupt");
            }
            //votingAlgo.controlWord.PVN = votingAlgo.controlWord.voteInfo.get(votingAlgo.controlWord.Physical.get(0)).getPVN();
            System.out.println("Updated version of file = " + controlWord.PVN);
        }
        System.out.println("done catchup");
    }

    // update and commit 
    public void doUpdateStats() {
        System.out.println("Updating the file for as per current given request");
        synchronized (controlWord) {
            controlWord.Updates.add(controlWord.potentialUpdate);
            writeToFile(my_master.fileObjectName,controlWord.potentialUpdate);
            controlWord.LVN = controlWord.M + 1;
            controlWord.PVN = controlWord.M + 1;
            controlWord.RU = (controlWord.target_msg_count+1);
            // DS update if RU is even : pick lexicographically lowest site ID in current partition
            if( (controlWord.RU % 2) == 0) {
                controlWord.DS  = controlWord.S;
                System.out.println("sites cardinality is even, thus update DS to "+controlWord.DS);
            }
            synchronized (my_master.serverSocketConnectionHashMap) {
                my_master.serverSocketConnectionHashMap.keySet().forEach(key -> {
                    if (controlWord.Physical.contains(Integer.valueOf(key))) {
                        //System.out.println("Physical contains "+key);
                        my_master.serverSocketConnectionHashMap.get(key).sendCommit(controlWord.LVN, controlWord.RU, controlWord.DS, controlWord.potentialUpdate);
                    } else {
                        //System.out.println("Physical not contains "+key);
                        my_master.serverSocketConnectionHashMap.get(key).sendCommit(controlWord.LVN, controlWord.RU, controlWord.DS, "NULL");
                    }
                });
            }
        }
    }

    // send abort to all sites
    public void releaseAbort()
    {
        synchronized(controlWord)
        {
            controlWord.locked = false;
            controlWord.clearAllInfo();
            System.out.println("SITE UNLOCKED due to ABORT");

            // set variables;
            controlWord.target_msg_count = -1;
            controlWord.received_msg_count = 0;
        }

        // send ABORT message to all sites
        synchronized (my_master.serverSocketConnectionHashMap) {
            my_master.serverSocketConnectionHashMap.keySet().forEach(key -> {
                my_master.serverSocketConnectionHashMap.get(key).sendAbort();
            });
        }

    }

    // check node lock and process vote request
    public synchronized void processInfoReply(String requestingClientId, int LVN, int PVN, int RU, int DS) {
        int current = -1;
        int target = 0;
        DSmessage obj = new DSmessage(LVN, PVN, RU, DS);
        System.out.println("processing INFO_REPLY from S" + requestingClientId);
        System.out.println("LVN = " + LVN);
        System.out.println("PVN = " + PVN);
        System.out.println("RU = " + RU);
        System.out.println("DS = " + DS);
        synchronized (controlWord) {
            ++controlWord.received_msg_count;
            controlWord.voteInfo.put(Integer.valueOf(requestingClientId), obj);
            current = controlWord.received_msg_count;
            target = controlWord.target_msg_count;
        }
        // run voting algorithm when all replies received
        if (target == current) {
            System.out.println("received all INFO_REPLY messages for current partition");
            Thread v = new Thread() {
                public void run() {
                    executeVotingAlgorithm();
                }
            };
            v.setDaemon(true);
            v.setName("votingAlgorithm_Thread");
            v.start();
        }
    }


    public void printSiteStats() {
        synchronized (controlWord) {
            System.out.println("Site STATS");
            System.out.println("LVN = " + controlWord.LVN);
            System.out.println("PVN = " + controlWord.PVN);
            System.out.println("RU = " + controlWord.RU);
            System.out.println("DS = " + controlWord.DS);
        }
    }

    // check node lock and process commit message
    public synchronized void processCommit(String requestingClientId, int LVN, int RU, int DS, String update) {
        System.out.println("processing COMMIT from S" + requestingClientId);
        System.out.println("LVN = " + LVN);
        System.out.println("RU = " + RU);
        System.out.println("DS = " + DS);
        System.out.println("update_command = " + update);
        synchronized (controlWord) {
            controlWord.LVN = LVN;
            controlWord.RU = RU;
            controlWord.DS = DS;
            Pattern NULL = Pattern.compile("^NULL$");
            Matcher m_NULL = NULL.matcher(update);
            if (!m_NULL.find()) {
                controlWord.Updates.add(update);
                writeToFile(my_master.fileObjectName,update);
                System.out.println("File also updated with commit");
                controlWord.PVN = LVN;
            }
            controlWord.locked = false;
            printSiteStats();
            System.out.println("SITE UNLOCKED due to COMMIT");
        }
    }

    // method to clear the contents of the file when starting up
    // adapted from StackOverflow
    public void clearTheFile(String filename) {
        try
        {
            FileWriter fwOb = new FileWriter("./"+filename, false); 
            PrintWriter pwOb = new PrintWriter(fwOb, false);
            pwOb.write("");
            pwOb.flush();
            pwOb.close();
            fwOb.close();
        }
        catch (FileNotFoundException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    // write to file 
    public void writeToFile(String filename,String content)
    {
        // directory is based on serverID
        File file = new File("./"+filename);
	if (!file.exists()) 
        {
	    System.out.println("File "+filename+" does not exist");
            return;
	}
        try
        {
            // write / append to the file
            FileWriter fw = new FileWriter(file, true);
            fw.write(content+"\n");
            fw.close();
        }
        catch (FileNotFoundException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e) 
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
