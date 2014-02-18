package org.mediterraneancoin.proxy;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.mediterraneancoin.miner.SuperHasher;
import org.mediterraneancoin.proxy.McproxyHandler.SessionStorage;
import static org.mediterraneancoin.proxy.net.RPCUtils.tohex;
import org.mediterraneancoin.proxy.net.WorkState;

/**
 *
 * @author dev4
 */
public class StratumThread implements Runnable {
    
    private static long minDeltaTime = 200; // ms
    private static int minQueueLength = 4;   
    private static int maxQueueLength = 8;
    
    //static String workerName;
    //private static String workerPassword;
    
    //private long lastGetwork;
    private long localMinDeltaTime;    
    
    final ObjectMapper mapper = new ObjectMapper();
    
    private static final ConcurrentLinkedQueue<McproxyHandler.SessionStorage> queue = new ConcurrentLinkedQueue<McproxyHandler.SessionStorage>();
    
    private static AtomicLong lastGlobalGetwork = new AtomicLong(0);
    
    private static boolean DEBUG = true;
    private static final String prefix = "THREAD ";
    
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

    public static ConcurrentLinkedQueue<SessionStorage> getQueue() {
        return queue;
    }
  
    
    
    
    public void getWorkFromStratum() {  
        
        while (System.currentTimeMillis() - lastGlobalGetwork.get() < minDeltaTime) {
            
            long waitTime = minDeltaTime - (System.currentTimeMillis() - lastGlobalGetwork.get());
            waitTime = waitTime / 10;
            if (waitTime < 10)
                waitTime = 10;
            
            try {
                Thread.sleep(/*localMinDeltaTime*/ waitTime );
            } catch (InterruptedException ex) {             
            }            
        }
        
        lastGlobalGetwork.set(System.currentTimeMillis());
        
        if (DEBUG)
            System.out.println(prefix + "stratum work request... thread " + threadId);
        
        SessionStorage storage = new SessionStorage();
        
        while (true) {
        
            try {
                storage.serverWork = stratumConnection.getWork();
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(StratumThread.class.getName()).log(Level.SEVERE, null, ex);
            } catch (CloneNotSupportedException ex) {
                Logger.getLogger(StratumThread.class.getName()).log(Level.SEVERE, null, ex);
            }        

            if (storage.serverWork == null || storage.serverWork.block_header == null ) {
                System.out.println(prefix + "thread " + threadId + " getting null! Waiting for a while...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                }
                return;
            }
            
            long delta = (System.currentTimeMillis() - storage.serverWork.timestamp) / 1000;
            
            
            if (delta < 30) 
                break;
        
            System.out.println(prefix + "stratum work request... serverWork too old (" + delta + " s), skipping...");
            
        }
        
        storage.work = new WorkState(null);

        // storage.serverWork.block_header has to be byteswapped before going through stage1!!
        // 00000002ff9fc69577e6881d52ee081d9134f77934435ca6a9fe987548809bd5a8bb5750FFCB5BF7E595E88EFF7390CAEBF12673DF24A2AD81C9EEAA518B5F864E4698AB52FCA55D1b01a8bf00000000000000800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000

        // parseData does a byteswap of the args!!!
        storage.work.parseData( /*WorkState.byteSwap*/( storage.serverWork.block_header ) );
        storage.work.setTarget(storage.serverWork.target_hex);     
        

        String dataFromWallet = storage.work.getAllDataHex();
        
        

        if (DEBUG) {  
            // data has already been byteswapped
            System.out.println(prefix + "data: " + dataFromWallet);       
            //System.out.println(prefix + "storage.serverWork.block_header: " + storage.serverWork.block_header);
            //System.out.println(prefix + "byte swap                      : " + WorkState.byteSwap(storage.serverWork.block_header));
            System.out.println(prefix + "target: " + storage.work.getTarget());
        }
        
        // let's byteswap the target? NEIN!!!
        //storage.work.setTarget( WorkState.byteSwap( storage.work.getTarget() )  );

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
            System.out.println(prefix + "part1: " + tohex(part1));
            //System.out.println();                
        }

        ObjectNode resultNode = mapper.createObjectNode();

        // we need to byteswap data before sending it
        String tempData = tohex(part1) + tohex(storage.work.getData2());

        String dataStr = WorkState.byteSwap( tempData );
        
        if (DEBUG) { 
            System.out.println(prefix + "data for miner: " + dataStr);
            //System.out.println();                
        }        

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
            System.out.println(prefix + "json: " + storage.answer);
            System.out.println();
            System.out.println();
        }        
        
        //lastGetwork = now;
        
        
        
        queue.add(storage);
        
        
    }
    
    public void cleanup() {
        
        while (queue.size() > maxQueueLength) {
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
                getWorkFromStratum();
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
                localMinDeltaTime = minDeltaTime; //20;
            } else if (queue.size() < maxQueueLength && queue.size() >= minQueueLength) { 
                if (DEBUG)    
                    System.out.print(threadId + "*** decreasing localMinDeltaTime from " + localMinDeltaTime + " ");
                
                localMinDeltaTime = (localMinDeltaTime * 85) / 100;            
                

                if (localMinDeltaTime < /*20*/ minDeltaTime) {
                    localMinDeltaTime = /*20*/ minDeltaTime;
                    continue;
                }                
                
                if (DEBUG)
                    System.out.println("to " + localMinDeltaTime + " ms");                
                
                
            } else if (queue.size() < minQueueLength) {
                
                if (DEBUG)    
                    System.out.print(threadId + "*** decreasing localMinDeltaTime from " + localMinDeltaTime + " ");
                
                localMinDeltaTime = (localMinDeltaTime * 85) / 100;
                
                if (localMinDeltaTime < /*20*/ minDeltaTime) {
                    localMinDeltaTime = /*20*/ minDeltaTime;
                    continue;
                }                
                
                if (DEBUG)
                    System.out.println("to " + localMinDeltaTime + " ms");
                
            } else if (queue.size() >= /*(int)((minQueueLength * 3.) / 2.)*/ maxQueueLength) {
                
                if (DEBUG)
                    System.out.print(threadId + "+++ increasing localMinDeltaTime from " + localMinDeltaTime + " ");
                
                localMinDeltaTime =  (localMinDeltaTime * 115) / 100;
                
                if (localMinDeltaTime > 3000)
                    localMinDeltaTime = 3000;
                
                if (DEBUG)
                    System.out.println("to " + localMinDeltaTime + " ms");
                
            }
            
        }
        
        
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
        StratumThread.minDeltaTime = minDeltaTime;
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
        StratumThread.minQueueLength = minQueueLength;
        
        if (maxQueueLength <= StratumThread.minQueueLength)
            maxQueueLength = StratumThread.minQueueLength+1;
    }

    public static int getMaxQueueLength() {
        return maxQueueLength;
    }

    public static void setMaxQueueLength(int maxQueueLength) {
        StratumThread.maxQueueLength = maxQueueLength;
    }
 
    
    
}
