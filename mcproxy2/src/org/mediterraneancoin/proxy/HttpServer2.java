package org.mediterraneancoin.proxy;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.ServerRunner;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.mediterraneancoin.miner.SuperHasher;
import org.mediterraneancoin.proxy.net.RPCUtils;
import static org.mediterraneancoin.proxy.net.RPCUtils.tohex;
import org.mediterraneancoin.proxy.net.WorkState;

/**
 *
 * @author dev
 */
public class HttpServer2  extends NanoHTTPD {
    private static boolean DEBUG;
    
    static int localport;
    static String hostname;
    static int port;
    
    
    final ObjectMapper mapper = new ObjectMapper();
    final URL url;
    RPCUtils utils;
    
    final HashMap<String, SessionStorage> works;
 
    class SessionStorage {
        WorkState work;
        
        String sentData;
        String dataFromWallet;
    }    
    
    public HttpServer2() throws MalformedURLException {
               
        super(localport);
        
        url = new URL("http",hostname, port, "/");
        
        utils = new RPCUtils(url, "", "");
        
        works = new HashMap<String, SessionStorage>();
        
        SuperHasher.DEBUG = false;        
    }    
    
    
    
    @Override public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();
        
        if (DEBUG) {
            System.out.println(method + " '" + uri + "' ");
        }
        
        Map<String, String> headers = session.getHeaders();
        
        String type = headers.get("content-type");
        String authHeader = headers.get("authorization");
        int contentLength = Integer.parseInt( headers.get("content-length") );
        
        byte cbuf[] = new byte[contentLength]; 
        
        try {
            session.getInputStream().read(cbuf);
        } catch (IOException ex) {
            Logger.getLogger(HttpServer2.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        String content = new String(cbuf);
 
        if (DEBUG) {
            System.out.println("content-len: " + content.length());
            System.out.println("content: " + content);
        }
        


        ObjectNode node = null;

        try {
            node = (ObjectNode) mapper.readTree(content);
 
        } catch (IOException ex) {
            Logger.getLogger(HttpServer2.class.getName()).log(Level.SEVERE, null, ex);
        } 


        String jsonMethod = node.get("method").asText();

        if (DEBUG) {
          System.out.println("jsonMethod: " + jsonMethod);
        }

        int paramSize = -1;

        if (node.get("params").isArray()) {
            paramSize = node.get("params").size();
        }

        String id = node.get("id").asText();


        String answer = "";
          
        if (type.toString().equals("application/json") && jsonMethod.equals("getwork") && paramSize == 0) {
            
            System.out.println("getwork request...");
 
            WorkState work = null;
            try {
                work = utils.doGetWorkMessage(false,authHeader);
            } catch (IOException ex) {
                Logger.getLogger(HttpServer2.class.getName()).log(Level.SEVERE, null, ex);
            }

            String dataFromWallet = work.getAllDataHex();

            if (DEBUG) {  
                // data has already been byteswapped inside doGetWorkMessage
                System.out.println("data: " + dataFromWallet);              
                System.out.println("target: " + work.getTarget());
            }

 
            SuperHasher hasher = null;
            try {
                hasher = new SuperHasher();
            } catch (GeneralSecurityException ex) {
                Logger.getLogger(HttpServer2.class.getName()).log(Level.SEVERE, null, ex);
            }



            byte [] part1 = null;
            try {
                part1 = hasher.firstPartHash(work.getData1() );
            } catch (GeneralSecurityException ex) {
                Logger.getLogger(HttpServer2.class.getName()).log(Level.SEVERE, null, ex);
            }

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
            answerNode.put("id", Long.parseLong(id) );        

            answer = answerNode.toString();


            SessionStorage sessionStorage = new SessionStorage();
            sessionStorage.work = work;
            sessionStorage.sentData = dataStr;
            sessionStorage.dataFromWallet = dataFromWallet;

            works.put(dataStr.substring(0, 68*2) , sessionStorage);

          

            if (DEBUG) { 
                System.out.println("json: " + answer);
                System.out.println();
                System.out.println();
            }

        } else if (type.toString().equals("application/json") && jsonMethod.equals("getwork") && paramSize != 0) {

            System.out.println("submitwork request...");

            String receivedDataStr = node.get("params").get(0).asText();
            //SuperHasher hasher 
            SessionStorage sessionStorage = works.get(receivedDataStr.substring(0, 68*2));



            if (sessionStorage == null) {
                System.out.println("WORK NOT FOUND!!! " + receivedDataStr);

                answer = "{\"result\":false,\"error\":null,\"id\":1}";

            } else {

                WorkState work = sessionStorage.work;

                if (DEBUG) { 
                    System.out.println("RECEIVED WORK: " + receivedDataStr);
                    System.out.println("dataFromWallet: " + sessionStorage.dataFromWallet);
                    System.out.println("sentData TO MINER: " + sessionStorage.sentData);
                }

                String workStr = WorkState.byteSwap(sessionStorage.dataFromWallet.substring(0, 68*2)) +
                        receivedDataStr.substring(68*2);

                  // send to wallet
                  // need to byteswap! it's done inside doSendWorkMessage                    
                boolean result = false;
                try {
                    result = utils.doSendWorkMessage(workStr,authHeader);
                } catch (IOException ex) {
                    Logger.getLogger(HttpServer2.class.getName()).log(Level.SEVERE, null, ex);
                }


                /*
                //

                // 1 - byteswap all data received from miner
                receivedDataStr = WorkState.byteSwap(receivedDataStr);


                String nonceStr = receivedDataStr.substring(76*2, 76*2 + 8);              
                System.out.println("byteswapped nonce: " + nonceStr);


                // copy nonce from received work (and also nTime and nBits) to original work, a total of 12 bytes
                byte [] data = work.getData1();

                for (int i = 0; i < 24; i += 2) {                   
                      String n = receivedDataStr.substring(i, i + 2);

                      data[68 + i / 2] =  (byte) (0xFF & Integer.parseInt(n, 16)); //Byte.parseByte(n, 16);                          
                }


                // 2 - calculate part2 of hybridhash
                SuperHasher hasher = new SuperHasher();

                byte [] targetBits = new byte[4];
                targetBits[0] = work.getData1()[75];
                targetBits[1] = work.getData1()[74];
                targetBits[2] = work.getData1()[73];
                targetBits[3] = work.getData1()[72];    

                byte[] finalHash = hasher.secondPartHash(data, targetBits);

                // 2.1 - verify hash
                byte [] header = work.getData1();
                BigInteger hashTarget = readCompact(header[75], header[74], header[73],header[72]);

                //System.out.println("hashTarget: " + hashTarget);
                System.out.println("hashTarget: " + hashTarget.toString(16));                

                BigInteger hash = new BigInteger( 1 , SuperHasher.swap(finalHash) );

                boolean checkHash =  hash.compareTo(hashTarget) <= 0;

                System.out.println("hash: " + hash.toString(16));  

                System.out.println("is hash ok? " + checkHash);
                */
                //


                works.remove(receivedDataStr.substring(0, 68*2));

                answer = "{\"result\":" + result + ",\"error\":null,\"id\":1}";
              }




        }        
        
        
        Response response = new NanoHTTPD.Response(answer);
        
        response.setMimeType("application/json");
        response.addHeader("Server", "MediterraneanCoinProxy/2.0 (nanoHTTPD 1.0)");
        
        return response;
        
        
    }    
    
    
    
    
    

   public static void main(String[] args) throws Exception {
       
       
        hostname = "localhost";
        port = 9372;
        
        //String bindAddress = "";
        localport = 8080;        

        int i = 0;
         

         while (i < args.length) {

             if (args[i].equals("-s")) {
                 i++;
                 hostname = args[i];
             } else if (args[i].equals("-p")) {
                 i++;
                 port = Integer.parseInt(args[i]);
             }/*  else if (args[i].equals("-b")) {
                 i++;
                 bindAddress = args[i];
             } */ else if (args[i].equals("-l")) {
                 i++;
                 localport = Integer.parseInt(args[i]);
             } else if (args[i].equals("-h") || args[i].equals("--help")) {
                   System.out.println("parameters:\n" +
                           "-s: hostname of wallet/pool (default: localhost)\n" + 
                           "-p: port of wallet/pool (default: 9372)\n" + 
                           "-l: local proxy port (default: 8080)\n" + 
                           "-v: verbose"
                           );
                   return;                 
             } else if (args[i].equals("-v")) {
                 HttpServer2.DEBUG = true;
             }
  
             i++;
         }       
        
        System.out.println("MediterraneanCoin Proxy");
        System.out.println("parameters:\n" + 
                "wallet hostname: " + hostname + "\n" +
                "wallet port: " + port + "\n" +
                "local proxy port: " + localport + "\n"
                );
        
        
        ServerRunner.run(HttpServer2.class);
        
  
        
   }
        
    
    
}
