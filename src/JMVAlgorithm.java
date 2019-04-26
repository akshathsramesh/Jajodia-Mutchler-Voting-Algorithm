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
            System.out.println("SITE LOCKED for new update request");

            // set variables;
            controlWord.target_msg_count = my_master.serverSocketConnectionHashMap.size();
            controlWord.received_msg_count = 0;
            if (m_NULL.find()) {
                controlWord.potentialUpdate = Integer.toString(controlWord.PVN+1);
            } else {
                controlWord.potentialUpdate = Update;
            }
        }

        // send VOTE_REQUEST message to all sites
        my_master.serverSocketConnectionHashMap.keySet().forEach(key -> {
                my_master.serverSocketConnectionHashMap.get(key).sendVoteRequest();
        });


        if(my_master.serverSocketConnectionHashMap.isEmpty()) {
            System.out.println("Only node in the partition!");
            Thread v = new Thread() {
                public void run() {
                    my_master.executeVotingAlgorithm();
                }
            };
            v.setDaemon(true);
            v.setName("votingAlgorithm_ThreadInsideAlgo_onlyNodeInPartition");
            v.start();

        }
        
    }

    // synchronized method to release resource/ critical section
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
        my_master.serverSocketConnectionHashMap.keySet().forEach(key -> {
                my_master.serverSocketConnectionHashMap.get(key).sendAbort();
        });

    }

}
