package org.mediterraneancoin.proxy;

import java.io.PrintStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;
import java.util.HashMap;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.mediterraneancoin.miner.SuperHasher;
import static org.mediterraneancoin.miner.SuperHasher.readCompact;
import org.mediterraneancoin.proxy.net.RPCUtils;
import static org.mediterraneancoin.proxy.net.RPCUtils.tohex;
import org.mediterraneancoin.proxy.net.WorkState;
import org.simpleframework.http.ContentType;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;

/**
 *
 * @author test
 */
public class HttpServer implements Container  {
    
    final ObjectMapper mapper = new ObjectMapper();
    final URL url;
    RPCUtils utils;
 
    class SessionStorage {
        WorkState work;
        
        String sentData;
        String dataFromWallet;
    }
    
    public HttpServer(String hostname, int port) throws MalformedURLException {
        url = new URL("http", hostname, port, "/");
        
        utils = new RPCUtils(url, "", "");
        
        works = new HashMap<String, SessionStorage>();
        
        SuperHasher.DEBUG = false;
    }
    
    final HashMap<String, SessionStorage> works;
    
    public static boolean DEBUG = false;
    
   @Override
   public void handle(Request request, Response response) {
      try {
          
        if (DEBUG) {  
            System.out.println("method: " + request.getMethod());
        }
          
        ContentType type = request.getContentType(); 
        String primary = type.getPrimary(); 
        String secondary = type.getSecondary(); 
        String charset = type.getCharset();          
        
        
        
        // get authorization credentials from miner
        // and use them for getwork
        String authHeader = request.getValue("Authorization");
        
        if (DEBUG) {
            System.out.println("auth: " + authHeader);
            System.out.println("content-type: " + type );
        }
         
          
        long l = request.getContentLength();
        String content = request.getContent();


        if (DEBUG) {  
          System.out.println("content-len: " + l);          
          System.out.println("content: " + content);          
        }


        ObjectNode node = (ObjectNode) mapper.readTree(content);

        //System.out.println(node);


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
 
            WorkState work = utils.doGetWorkMessage(false,authHeader);

            String dataFromWallet = work.getAllDataHex();

            if (DEBUG) {  
                // data has already been byteswapped inside doGetWorkMessage
                System.out.println("data: " + dataFromWallet);              
                System.out.println("target: " + work.getTarget());
            }

 
            SuperHasher hasher = new SuperHasher();



            byte [] part1 = hasher.firstPartHash(work.getData1() );

            System.out.println("part1: " + tohex(part1));
            System.out.println();                

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
                boolean result = utils.doSendWorkMessage(workStr,authHeader);


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
          
          
        PrintStream body = response.getPrintStream();
        long time = System.currentTimeMillis();

        response.setValue("Content-Type", "application/json");
        response.setValue("Server", "MediterraneanCoinProxy/1.0 (Simple 1.0)");
        response.setDate("Date", time);
        response.setDate("Last-Modified", time);

        body.println(answer);
        body.close();
        
      } catch(Exception e) {
         e.printStackTrace();
      }
   } 

   public static void main(String[] args) throws Exception {
       
       
        String hostname = "localhost";
        int port = 9372;
        
        String bindAddress = "";
        int localport = 8080;        

        int i = 0;


         while (i < args.length) {

             if (args[i].equals("-s")) {
                 i++;
                 hostname = args[i];
             } else if (args[i].equals("-p")) {
                 i++;
                 port = Integer.parseInt(args[i]);
             }  else if (args[i].equals("-b")) {
                 i++;
                 bindAddress = args[i];
             }  else if (args[i].equals("-l")) {
                 i++;
                 localport = Integer.parseInt(args[i]);
             } else if (args[i].equals("-h") || args[i].equals("--help")) {
                   System.out.println("parameters:\n" +
                           "-s: hostname of wallet/pool (default: localhost)\n" + 
                           "-p: port of wallet/pool (default: 9372)\n" + 
                           "-b: bind to local address (default: )\n" +
                           "-l: local proxy port (default: 8080)\n" +
                           "-v: verbose"
                           );
                   return;                 
             } else if (args[i].equals("-v")) {
                 HttpServer.DEBUG = true;
             }
  
             i++;
         }       
        
        System.out.println("MediterraneanCoin Proxy");
        System.out.println("parameters:\n" + 
                "wallet hostname: " + hostname + "\n" +
                "wallet port: " + port + "\n" +
                "bind to local address: " + bindAddress + "\n" +
                "local proxy port: " + localport + "\n"
                );
        
        Container container = new HttpServer(hostname, port);
        Server server = new ContainerServer(container);
        Connection connection = new SocketConnection(server);
        SocketAddress address;

        if (bindAddress.equals(""))
            address = new InetSocketAddress(localport);
        else
            address = new InetSocketAddress(bindAddress, localport);

        connection.connect(address);
        
   }
    
    
}

