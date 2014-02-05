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
    
    private final static Object lock = new Object();
    
    private static GetworkThread instance;
    
    private URL url;
    private RPCUtils utils;
    
    private String authHeader;
    
    private long minDeltaTime = 200; // ms
    private long lastGetwork;
    
    final ObjectMapper mapper = new ObjectMapper();
    
    private final ConcurrentLinkedQueue<SessionStorage> queue = new ConcurrentLinkedQueue<SessionStorage>();
    
    public static GetworkThread getInstance() {
        if (instance == null) {
            synchronized(lock) {
                if (instance == null) {
                    instance = new GetworkThread();
                }
            }
        }
        
        return instance;
    }
    private boolean DEBUG;
    
    private GetworkThread() {        
    }
    
    public void start(URL url, RPCUtils utils) {
    
        this.url = url;
        this.utils = utils;
        
        new Thread(this).start();
        
    }
    
    
    public void getwork() {
        
        if (authHeader == null)
            return;
        
        long now = System.currentTimeMillis();
        
        if (now - lastGetwork < minDeltaTime) {
            System.out.println("too near getwork, skipping");
            return;
        }
        
        System.out.println("getwork request to wallet/daemon...");
        
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
        
        while (queue.size() > 32) {
            System.out.println("queue.size(): " + queue.size());
            queue.poll();
        }
                    
    }    
    
    public SessionStorage getSessionStorage() {
        SessionStorage result;
        
        while ((result = queue.poll()) == null) {
            try {
                Thread.sleep(minDeltaTime);
            } catch (InterruptedException ex) {
            }
        }
        
        System.out.println("poll from servlet");
                
        return result;        
    }

    @Override
    public void run() {
        
        
        while (true) {
            
            try {
                getwork();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            
            long now = System.currentTimeMillis();
            
            try {
                Thread.sleep( minDeltaTime / 2 );
            } catch (InterruptedException ex) {
            }
            
            cleanup();
        }
        
        
    }

    public String getAuthHeader() {
        return authHeader;
    }

    public void setAuthHeader(String authHeader) {
        this.authHeader = authHeader;
    }

    public boolean isDEBUG() {
        return DEBUG;
    }

    public void setDEBUG(boolean DEBUG) {
        this.DEBUG = DEBUG;
    }

    public long getMinDeltaTime() {
        return minDeltaTime;
    }

    public void setMinDeltaTime(long minDeltaTime) {
        this.minDeltaTime = minDeltaTime;
    }


    

    
    
    
}
