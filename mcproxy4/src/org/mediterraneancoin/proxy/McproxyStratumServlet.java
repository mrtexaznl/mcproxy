package org.mediterraneancoin.proxy;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.mediterraneancoin.miner.SuperHasher;
import static org.mediterraneancoin.proxy.net.RPCUtils.tohex;
import org.mediterraneancoin.proxy.net.WorkState;

/**
 *
 * @author dev2
 */
public class McproxyStratumServlet  extends HttpServlet {
    
    public static boolean DEBUG = true;
    private static final String prefix = "SERVLET ";
    
    static int localport;
    static String hostname;
    static int port;
    
    
    final ObjectMapper mapper = new ObjectMapper();
 
    
    static HashMap<String, McproxyHandler.SessionStorage> works = new HashMap<String, McproxyHandler.SessionStorage>();
     
    private final static StratumConnection stratumConnection = StratumConnection.getInstance();

    
    SuperHasher hasher;
    
    @Override
    public void init() throws ServletException {
        super.init(); //To change body of generated methods, choose Tools | Templates.
        
        try {
            hasher = new SuperHasher();
        } catch (GeneralSecurityException ex) {
            Logger.getLogger(McproxyStratumServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        
  

        if (DEBUG) {  
            System.out.println(prefix + "method: " + request.getMethod());
        }        
        
        String type = request.getContentType(); 
        
        //String authHeader = request.getHeader("authorization");
        int contentLength = Integer.parseInt( request.getHeader("content-length") );
         
        
        if (DEBUG) {
            //System.out.println("auth: " + authHeader);
            //System.out.println("content-type: " + type );
        }
        
        
        byte cbuf[] = new byte[contentLength]; 
        
        request.getInputStream().read(cbuf);
        
        String content = new String(cbuf); 
 

        if (DEBUG) {  
          //System.out.println("content-len: " + contentLength);          
          //System.out.println("content: " + content);          
        }        
        


        ObjectNode node = null;

        try {
            node = (ObjectNode) mapper.readTree(content);
 
        } catch (IOException ex) {
            Logger.getLogger(McproxyStratumServlet.class.getName()).log(Level.SEVERE, null, ex);
        } 


        String jsonMethod = node.get("method").asText();

        if (DEBUG) {
          //System.out.println("jsonMethod: " + jsonMethod);
        }

        int paramSize = -1;

        if (node.get("params").isArray()) {
            paramSize = node.get("params").size();
        }

        String id = node.get("id").asText();


        String answer = "";
          
        if (type.toString().equals("application/json") && jsonMethod.equals("getwork") && paramSize == 0) {
            
            if (DEBUG) 
                System.out.println(prefix + "getwork request from miner...");

            // data has already been byteswapped
            McproxyHandler.SessionStorage sessionStorage = StratumThread.getSessionStorage();

            if (DEBUG) 
                System.out.println(prefix + "sending getwork: " + sessionStorage.answer);
            // 
            works.put(sessionStorage.sentData.substring(0, 68*2) , sessionStorage);

            answer = sessionStorage.answer;       
           

        } else if (type.toString().equals("application/json") && jsonMethod.equals("getwork") && paramSize != 0) {

            if (DEBUG) 
                System.out.println(prefix + "submitwork request from miner... works queue size: " + works.size());

            String receivedDataStr = node.get("params").get(0).asText();
            
            String receivedDataStr68 = receivedDataStr.substring(0, 68*2);
      
            McproxyHandler.SessionStorage sessionStorage = works.get( receivedDataStr68 );
            

            if (sessionStorage == null) {
                if (DEBUG) 
                    System.out.println(prefix + "WORK NOT FOUND!!! " + receivedDataStr);
                
                ObjectNode resultNode = mapper.createObjectNode();
        

                resultNode.put("result", false);
                resultNode.put("error", "WORK NOT FOUND!!!");
                resultNode.put("id", Integer.parseInt(id));                

                //answer = "{\"result\":false,\"error\":null,\"id\":1}";
                answer = resultNode.toString();
                
                System.out.println("ERROR: " +answer);

            } else {
                
                //works.remove( receivedDataStr68 );
                
//            
            //String START = "eecb92d5eefa3c91daed8b7a1ebc3093c15b4b459c05e54d92e78bf535d6c234a3e00426a7648f4ac8214fae1d9262427d2d7e6609f5323b4fd1f887a4aba6cf25eee7bf52fe29b01b01f9321dc38608" +
            //        "000000800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000";
            //receivedDataStr = START;
            //receivedDataStr68 = receivedDataStr.substring(0, 68*2);
//                
                

                WorkState work = sessionStorage.work;

                if (DEBUG) { 
                    System.out.println(prefix + "RECEIVED WORK: " + receivedDataStr);
                    System.out.println(prefix + "dataFromWallet: " + sessionStorage.dataFromWallet);
                    System.out.println(prefix + "sentData TO MINER: " + sessionStorage.sentData);
                }
                
                // second part verification (STAGE2)

                // 1 - byteswap all data received from miner
                receivedDataStr = WorkState.byteSwap(receivedDataStr);

                String nonceStr = receivedDataStr.substring(76*2, 76*2 + 8);        
                
                if (DEBUG) {
                    System.out.println(prefix + "byteswapped nonce: " + nonceStr);                     
                }

                
                
                // copy nonce from received work (and also nTime and nBits) to original work, a total of 12 bytes
                //byte [] data = work.getData1();
                
                byte [] data = new byte[80];
                for (int i = 0; i < 80; i++) {
                    String n = receivedDataStr.substring(i*2, i*2 + 2);
                    
                    data[i] = (byte) (0xFF & Integer.parseInt(n, 16));
                }
                
                /*
                for (int i = 0; i < 24; i += 2) {                   
                      String n = receivedDataStr.substring(i, i + 2);

                      data[68 + i / 2] =  (byte) (0xFF & Integer.parseInt(n, 16)); //Byte.parseByte(n, 16);                          
                }
                */

                // 2 - calculate part2 of hybridhash               

                byte [] targetBits = new byte[4];
                targetBits[0] = data[72];  //work.getData1()[72];  // LSB
                targetBits[1] = data[73];  //work.getData1()[73];
                targetBits[2] = data[74];  //work.getData1()[74];
                targetBits[3] = data[75];  //work.getData1()[75];  // MSB  

                byte[] finalHash = null;
                try {
                    finalHash = hasher.secondPartHash(data, targetBits);
                } catch (NoSuchAlgorithmException ex) {
                    Logger.getLogger(McproxyStratumServlet.class.getName()).log(Level.SEVERE, null, ex);
                } catch (GeneralSecurityException ex) {
                    Logger.getLogger(McproxyStratumServlet.class.getName()).log(Level.SEVERE, null, ex);
                }

                // 2.1 - verify hash
                
                /*
                byte [] header = work.getData1();
 
                byte a,  b,  c,  d;

                a = targetBits[3]; //header[72];  // MSB  
                b = targetBits[2]; // header[73];
                c = targetBits[1]; //header[74];
                d = targetBits[0]; //header[75]; 


                int nSize;

                nSize = a & 0xFF;

                boolean negative = (b & 0x80) != 0;

                int nWord = ((b & 0x7F) << 16) + ((c & 0xFF) << 8) + (d & 0xFF);        

                if (DEBUG) {
                    System.out.println(prefix + "size=" + nSize);
                    System.out.println(prefix + "negative=" + negative);
                    System.out.println(prefix + "nWord=" + nWord);
                }

                BigInteger hashTarget = new BigInteger("" + nWord, 10);      

                hashTarget = hashTarget.shiftLeft( 8 * (nSize -3));                    
                
                */
                
                String targetStr = new StringBuilder(work.getTarget()).reverse().toString() ; // work.getTarget()
                
                if (DEBUG) 
                    System.out.println(prefix + "hashTarget STR: " + targetStr);
                
                BigInteger hashTarget = new BigInteger( targetStr ,16);
                
                //

                //System.out.println("hashTarget: " + hashTarget);
                if (DEBUG)
                    System.out.println(prefix + "hashTarget: " + hashTarget.toString(16));                

                BigInteger hash = new BigInteger( 1 , SuperHasher.swap(finalHash) );

                boolean checkHash =  hash.compareTo(hashTarget) <= 0;

                if (DEBUG)
                    System.out.println(prefix + "hash: " + hash.toString(16));  

                
                System.out.println(prefix + "is hash ok? " + checkHash);      
                
                if (DEBUG)
                    System.out.println(prefix + " SERVERWORK: " + sessionStorage.serverWork.toString());
                
                /*
                if (DEBUG) {
                    

                    // 1. Increase extranonce2
                    long extranonce2 = StratumConnection.reverse(sessionStorage.serverWork.extranonce2.get());
 

                    // 2. Build final extranonce
                    String extranonce = sessionStorage.serverWork.extraNonce1Str + StratumConnection.extranonce2_padding(sessionStorage.serverWork);

                    

                    // 3. Put coinbase transaction together
                    String coinbase_bin = sessionStorage.serverWork.coinbasePart1 + extranonce + sessionStorage.serverWork.coinbasePart2;
                            // self.coinb1_bin + extranonce + self.coinb2_bin

                    // 4. Calculate coinbase hash
                    byte [] coinbase_hash = null;
                    try {
                        coinbase_hash = StratumConnection.hash256( StratumConnection.toByteArray(coinbase_bin) );
                    } catch (NoSuchAlgorithmException ex) {
                    }

                    // 5. Calculate merkle root
                    // merkle_root = binascii.hexlify(utils.reverse_hash(job.build_merkle_root(coinbase_hash)))
                    byte [] merkle_root = null;
                    try {
                        merkle_root = StratumConnection.reverseHash ( StratumConnection.buildMerkleRoot(sessionStorage.serverWork, coinbase_hash)  );
                    } catch (NoSuchAlgorithmException ex) {
                    }
                    
                    String merkleRootStr = StratumConnection.toHexString( merkle_root );

                    System.out.println(prefix + "!!! extranonce:" + extranonce);
                    System.out.println(prefix + "!!! extranonce2:" + extranonce2);
                    System.out.println(prefix + "!!! coinbase_bin:" + coinbase_bin);
                    System.out.println(prefix + "!!! merkleRootStr:" + merkleRootStr);
                    
                }
                
                */
                

                ObjectNode resultNode = mapper.createObjectNode();
                 
                
                if (!checkHash/*false*/ ) {
                                        
                    System.out.println(prefix + "returning FALSE to submit request");
                    
                    resultNode.put("result", false);
                    resultNode.put("error", (String) null);
                    resultNode.put("id", Integer.parseInt(id));
                    
                    answer = resultNode.toString();
                    
                    System.out.println("ERROR2: " +answer);
                    
                    //answer = "{\"result\":false,\"error\":null,\"id\":" + id + "}";
                } else {
                    
                    // TODO: modify sessionStorage.serverWork with correct nonce
                    sessionStorage.serverWork.nonce =  WorkState.byteSwap(nonceStr); // nonceStr;
                    
                    // submit work to Stratum
                    // CHECK: need to byteswap?
                    StratumConnection.StratumResult poolSubmitResult = stratumConnection.sendWorkSubmission(sessionStorage.serverWork);
  
                    if (DEBUG) {
                        System.out.println(prefix + "returning " + poolSubmitResult +" to submit request");  
                    }
                    
                    if (!poolSubmitResult.result && poolSubmitResult.error != null)
                        poolSubmitResult.error = null;
                    
                    resultNode.put("result", poolSubmitResult.result);
                    resultNode.put("error", poolSubmitResult.error);
                    resultNode.put("id", Integer.parseInt(id));
                    
                    answer = resultNode.toString();                    
                    
                    System.out.println("SUBMIT: " +answer);
                    //answer = "{\"result\":" + poolSubmitResult.result + ",\"error\":"  + poolSubmitResult.error + ",\"id\":" + id + "}";
                }

                //String workStr = WorkState.byteSwap(sessionStorage.dataFromWallet.substring(0, 68*2)) +
                //        receivedDataStr.substring(68*2);
                 
                
                

                
              }




        }        
        
        
        
        
        
        response.setContentType("application/json");
        
        response.setStatus(HttpServletResponse.SC_OK);
        //baseRequest.setHandled(true);
        
        
        
        response.getWriter().println(answer);        
        
        
    }
    
    
    public static void main(String [] arg) throws GeneralSecurityException {
        
        if (true) {
            
            String START = "eecb92d5eefa3c91daed8b7a1ebc3093c15b4b459c05e54d92e78bf535d6c234a3e00426a7648f4ac8214fae1d9262427d2d7e6609f5323b4fd1f887a4aba6cf25eee7bf52fe29b01b01f9321dc38608" +
                    "000000800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000";


            WorkState work = new WorkState(null);

            work.parseData(START);            

            System.out.println(work.getAllDataHex());

            SuperHasher hasher = new SuperHasher();
            
            String receivedDataStr = work.getAllDataHex();
            
                //receivedDataStr = WorkState.byteSwap(receivedDataStr);
                
                System.out.println(receivedDataStr);

                String nonceStr = receivedDataStr.substring(76*2, 76*2 + 8);        
                
                if (DEBUG) {
                    System.out.println(prefix + "byteswapped nonce: " + nonceStr);
                     
                }

                // copy nonce from received work (and also nTime and nBits) to original work, a total of 12 bytes
                byte [] data = work.getData1();

                
                for (int i = 0; i < 24; i += 2) {                   
                      String n = receivedDataStr.substring(68*2 + i, 68*2 + i + 2);

                      byte nv = (byte) (0xFF & Integer.parseInt(n, 16));
                      
                      if (nv != data[68 + i / 2]) {
                          System.out.println("*** " + (8 + i / 2));
                          
                          data[68 + i / 2] = nv;
                      }
                      
                      
                        //Byte.parseByte(n, 16);                          
                }
                

                
                // 2 - calculate part2 of hybridhash               

                byte [] targetBits = new byte[4];
                targetBits[0] = work.getData1()[72];  // LSB
                targetBits[1] = work.getData1()[73];
                targetBits[2] = work.getData1()[74];
                targetBits[3] = work.getData1()[75];  // MSB  

                byte[] finalHash = null;
                try {
                    finalHash = hasher.secondPartHash(data, targetBits);
                } catch (NoSuchAlgorithmException ex) {
                    Logger.getLogger(McproxyStratumServlet.class.getName()).log(Level.SEVERE, null, ex);
                } catch (GeneralSecurityException ex) {
                    Logger.getLogger(McproxyStratumServlet.class.getName()).log(Level.SEVERE, null, ex);
                }            
            
                
                work.setTarget("3fffc000000000000000000000000000000000000000000000000000000");

                BigInteger hashTarget = new BigInteger( ( work.getTarget()) ,16);
                
                //

                //System.out.println("hashTarget: " + hashTarget);
                if (DEBUG)
                    System.out.println(prefix + "hashTarget: " + hashTarget.toString(16));                

                BigInteger hash = new BigInteger( 1 , SuperHasher.swap(finalHash) );

                boolean checkHash =  hash.compareTo(hashTarget) <= 0;

                if (DEBUG)
                    System.out.println(prefix + "hash: " + hash.toString(16));  
                System.out.println(prefix + "hash: " + hash.toString(10));

                System.out.println(prefix + "is hash ok? " + checkHash);                     
            
            
            
            
            
            
            
            return;
        }
        
        
        
        
        
        if (true) {
            
            BigInteger bi = new BigInteger("15363741008652289555944905765563173368446543359880944893284847603");
            
            System.out.println(bi.toString(16));
            
            return;
        }
        
        // 52fdf7a6 1b01c274
        
        //1b01c274
        
                byte [] header = { 0x1b,
                    0x01,
                    (byte)0x9d,
                    (byte)0x93 };
                
                header = new byte [] { 0x1b,
                    0x01,
                    (byte)0xc2,
                    (byte)74 };
                
                SuperHasher.DEBUG = true;
                
                BigInteger hashTarget = SuperHasher.readCompact(header[0], header[1], header[2],header[3]);

                //System.out.println("hashTarget: " + hashTarget);
   
                    System.out.println(prefix + "hashTarget: " + hashTarget.toString(16));                

                //BigInteger hash = new BigInteger( 1 , SuperHasher.swap(finalHash) );

                //boolean checkHash =  hash.compareTo(hashTarget) <= 0;
        
                    BigInteger p = new BigInteger("421242738922051710830569942886312364625170292790221313845902704640");
                    
                    System.out.println(prefix + "POOL target: " + p.toString(16));     
        
    }
    
}
