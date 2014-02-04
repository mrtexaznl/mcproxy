package org.mediterraneancoin.proxy.test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.mediterraneancoin.miner.SuperHasher;
import static org.mediterraneancoin.proxy.AsyncHttpServer.DEBUG;

import org.mediterraneancoin.proxy.net.RPCUtils;
import static org.mediterraneancoin.proxy.net.RPCUtils.tohex;
import org.mediterraneancoin.proxy.net.WorkState;


/**
 *
 * @author dev5
 */
public class Test02 {
    
    class SessionStorage {
        WorkState work;
        
        String sentData;
        String dataFromWallet;
    }    
    
    final ObjectMapper mapper = new ObjectMapper();
    
    static final HashMap<String, SessionStorage> works = new HashMap<String, SessionStorage>();
    static URL url;
    
    static {
        try {
            url = new URL("http", "localhost", 9372, "/");
        } catch (MalformedURLException ex) {
            Logger.getLogger(Test02.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    public void doRequest() throws GeneralSecurityException, IOException {
        
                String answer = "";
         
                  System.out.println("request from miner...");
                  
                  

                  WorkState work;
                  
                  String dataFromWallet;
                  
                  if (true) {
                      RPCUtils utils = new RPCUtils(url, "admin", "12345");
                      
                      
                    work = utils.doGetWorkMessage(false, utils.getUserPass());

                    dataFromWallet = work.getAllDataHex();

                    if (DEBUG) {  
                        // data has already been byteswapped inside doGetWorkMessage
                        System.out.println("data: " + dataFromWallet);              
                        System.out.println("target: " + work.getTarget());
                    }
                  } else {
                      
                      work = new WorkState(null);
                      dataFromWallet = "";
                  }


                  SuperHasher hasher = new SuperHasher();


                  byte dummy[] = new byte[80];

                  byte [] part1 = hasher.firstPartHash(/*work.getData1()*/ dummy );

                  if (DEBUG) { 
                    System.out.println("part1: " + tohex(part1));
                    System.out.println();                
                  }

                  ObjectNode resultNode = mapper.createObjectNode();

                  // we need to byteswap data before sending it
                  String tempData = tohex(part1) + tohex(work.getData2());

                  String dataStr = WorkState.byteSwap( tempData );

                  resultNode.put("data", dataStr );
                  resultNode.put("target", work.getTarget());                

                  ObjectNode answerNode = mapper.createObjectNode();
                  answerNode.put("result", resultNode);
                  answerNode.put("error", (String)null);
                  //answerNode.put("id", Long.parseLong(id) );        

                  answer = answerNode.toString();


                  SessionStorage sessionStorage = new SessionStorage();
                  sessionStorage.work = work;
                  sessionStorage.sentData = dataStr;
                  sessionStorage.dataFromWallet = dataFromWallet;
                   

                  works.put(dataStr.substring(0, 68*2) , sessionStorage);
        
    }
    
    public static void main(String [] arg) throws GeneralSecurityException, IOException {
        
        final Test02 t = new Test02();
        
        for (int i = 0; i < 100; i ++) {
            
            new Thread() {
                @Override
                public void run() {
                    try {
                        t.doRequest();
                    } catch (GeneralSecurityException ex) {
                        Logger.getLogger(Test02.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IOException ex) {
                        Logger.getLogger(Test02.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                
            }.start();
            
            
        }
        
    }
    
}
