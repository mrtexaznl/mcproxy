package org.mediterraneancoin.proxy;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.mediterraneancoin.miner.SuperHasher;
import org.mediterraneancoin.proxy.McproxyHandler.SessionStorage;
import org.mediterraneancoin.proxy.net.RPCUtils;
import static org.mediterraneancoin.proxy.net.RPCUtils.tohex;
import org.mediterraneancoin.proxy.net.WorkState;

/**
 *
 * @author test
 */
public class GetworkThread implements Runnable {
          
    private final URL url;
    private final RPCUtils utils;
    
    private static String authHeader;
    
    private static long minDeltaTime = 200; // ms
    private static int minQueueLength = 4;
    
    private long lastGetwork;
    private long localMinDeltaTime;
    
    final ObjectMapper mapper = new ObjectMapper();
    
    private static final ConcurrentLinkedQueue<SessionStorage> queue = new ConcurrentLinkedQueue<SessionStorage>();
    
    private static int counter = 0;
    
    private int threadId; 

    private static boolean DEBUG;
    
    public GetworkThread(URL url, RPCUtils utils) {   
        
        threadId = counter++;
        
        this.url = url;
        this.utils = utils;        
        
        this.localMinDeltaTime = minDeltaTime;
    }
    
    public void start() {
        
        new Thread(this).start();
        
    }
    
    
    public void getwork() {
        
        if (authHeader == null)
            return;
        
        long now = System.currentTimeMillis();
        
        if (now - lastGetwork < localMinDeltaTime) {
            if (DEBUG)
                System.out.println("too near getwork, skipping; delta = " + (now - lastGetwork) + ", localMinDeltaTime=" + localMinDeltaTime);
            
            try {
                Thread.sleep(now - lastGetwork);
            } catch (InterruptedException ex) {
        
            }
            
            return;
        }
        
        System.out.println("getwork request to wallet/daemon... thread " + threadId);
        
        SessionStorage storage = new SessionStorage();
        
        storage.timestamp = now;
        
        try {
            storage.work = utils.doGetWorkMessage(false,authHeader);
        } catch (IOException ex) {
            Logger.getLogger(GetworkThread.class.getName()).log(Level.SEVERE, null, ex);
        }

        String dataFromWallet = storage.work.getAllDataHex();

        if (DEBUG) {  
            // data has already been byteswapped inside doGetWorkMessage
            System.out.println("data: " + dataFromWallet);              
            System.out.println("target: " + storage.work.getTarget());
        }
        

        SuperHasher hasher = null;
        try {
            hasher = new SuperHasher();
        } catch (GeneralSecurityException ex) {
            Logger.getLogger(GetworkThread.class.getName()).log(Level.SEVERE, null, ex);
        }

        byte [] part1 = null;
        try {
            part1 = hasher.firstPartHash(storage.work.getData1() );
        } catch (GeneralSecurityException ex) {
            Logger.getLogger(GetworkThread.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (DEBUG) { 
            System.out.println("part1: " + tohex(part1));
            System.out.println();                
        }

        ObjectNode resultNode = mapper.createObjectNode();

        // we need to byteswap data before sending it
        String tempData = tohex(part1) + tohex(storage.work.getData2());

        String dataStr = WorkState.byteSwap( tempData );

        resultNode.put("data", dataStr );
        resultNode.put("target", storage.work.getTarget());                

        ObjectNode answerNode = mapper.createObjectNode();
        answerNode.put("result", resultNode);
        answerNode.put("error", (String)null);
        
        // ...
        answerNode.put("id", 1 );        

        storage.answer = answerNode.toString();

 
        storage.sentData = dataStr;
        storage.dataFromWallet = dataFromWallet;

        //works.put(dataStr.substring(0, 68*2) , sessionStorage);



        if (DEBUG) { 
            System.out.println("json: " + storage.answer);
            System.out.println();
            System.out.println();
        }        
        
        lastGetwork = now;
        
        queue.add(storage);

    }
    
    public void cleanup() {
        
        while (queue.size() > minQueueLength * 4) {
            if (DEBUG)
                System.out.println("queue.size(): " + queue.size());
            
            SessionStorage item = queue.poll();
            
            item.work.setUtils(null);
            item.work = null;
            item.serverWork.extranonce2 = null;
            item.serverWork = null;
            
        }
                    
    }    
    
    public static SessionStorage getSessionStorage() {
        SessionStorage result;
        
        //long dt = 0;
        
        //do {
        
            while ((result = queue.poll()) == null) {
                try {
                    Thread.sleep(minDeltaTime);
                } catch (InterruptedException ex) {
                }
            }
        
        //} while (dt < );
        
        
        if (DEBUG)
            System.out.println("poll from servlet");
                
        return result;        
    }

    @Override
    public void run() {
        
        
        while (true) {
            
            if (DEBUG)
                System.out.println("thread " + threadId + ", queue.size()=" + queue.size() + ", minQueueLength=" + minQueueLength +
                    ", localMinDeltaTime=" + localMinDeltaTime); 
            
            try {
                getwork();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            
            long now = System.currentTimeMillis();
            
            try {
                Thread.sleep( localMinDeltaTime / 2 );
            } catch (InterruptedException ex) {
            }
            
            cleanup();
            
            //
            if (queue.size() <= 1) {
                localMinDeltaTime = 10;
            } else if (queue.size() < minQueueLength) {
                
                if (DEBUG)    
                    System.out.print(threadId + "***decreasing localMinDeltaTime from " + localMinDeltaTime + " ");
                
                localMinDeltaTime = (localMinDeltaTime * 85) / 100;
                
                if (localMinDeltaTime < 10) {
                    localMinDeltaTime = 10;
                    continue;
                }                
                
                if (DEBUG)
                    System.out.println("to " + localMinDeltaTime + " ms");
                
            } else if (queue.size() > (int)((minQueueLength * 3.) / 2.)) {
                
                if (DEBUG)
                    System.out.print(threadId + "+++increasing localMinDeltaTime from " + localMinDeltaTime + " ");
                
                localMinDeltaTime =  (localMinDeltaTime * 115) / 100;
                
                if (localMinDeltaTime > 10000)
                    localMinDeltaTime = 10000;
                
                if (DEBUG)
                    System.out.println("to " + localMinDeltaTime + " ms");
                
            }
            
        }
        
        
    }

    public static String getAuthHeader() {
        return authHeader;
    }

    public static void setAuthHeader(String authHeader) {
        GetworkThread.authHeader = authHeader;
    }



    public static boolean isDEBUG() {
        return DEBUG;
    }

    public static void setDEBUG(boolean _DEBUG) {
        DEBUG = _DEBUG;
    }

    public static long getMinDeltaTime() {
        return minDeltaTime;
    }

    public static void setMinDeltaTime(long minDeltaTime) {
        GetworkThread.minDeltaTime = minDeltaTime;
    }



    public long getLastGetwork() {
        return lastGetwork;
    }

    public void setLastGetwork(long lastGetwork) {
        this.lastGetwork = lastGetwork;
    }

    public int getThreadId() {
        return threadId;
    }

    public void setThreadId(int threadId) {
        this.threadId = threadId;
    }

    public static int getMinQueueLength() {
        return minQueueLength;
    }

    public static void setMinQueueLength(int minQueueLength) {
        GetworkThread.minQueueLength = minQueueLength;
    }


    

    
    
    
}
