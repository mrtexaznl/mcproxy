package org.mediterraneancoin.proxy;

import java.util.concurrent.ConcurrentLinkedQueue;
import org.mediterraneancoin.proxy.StratumConnection.ServerWork;

/**
 *
 * @author dev4
 */
public class StratumThread implements Runnable {
    
    private static long minDeltaTime = 200; // ms
    private static int minQueueLength = 4;    
    
    private long lastGetwork;
    private long localMinDeltaTime;    
    
    private static final ConcurrentLinkedQueue<ServerWork> queue = new ConcurrentLinkedQueue<ServerWork>();
    
    private static boolean DEBUG;
    
    private static int counter = 0;
    private int threadId; 
    
    private StratumConnection stratumConnection;
    
    public StratumThread() {
        threadId = counter++;
        
        this.localMinDeltaTime = minDeltaTime;
    }
    
    public void start() {
        
        stratumConnection = StratumConnection.getInstance();
        
        new Thread(this).start();
        
    }
    
    
    public void getWorkFromStratum() {
        
        long now = System.currentTimeMillis();
        
        if (now - lastGetwork < localMinDeltaTime) {
            if (DEBUG)
                System.out.println("too near getWorkFromStratum, skipping; delta = " + (now - lastGetwork) + ", localMinDeltaTime=" + localMinDeltaTime);

                try {
                    Thread.sleep(now - lastGetwork);
                } catch (InterruptedException ex) {             
                }
            
                return;
        }        
        
        ServerWork work = stratumConnection.getStratumWork();
        
        
        
    }
    
    
}
