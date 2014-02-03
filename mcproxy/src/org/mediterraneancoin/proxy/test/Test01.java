package org.mediterraneancoin.proxy.test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.commons.codec.binary.Base64;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.mediterraneancoin.proxy.net.RPCUtils;
import org.mediterraneancoin.proxy.net.WorkState;

/**
 *
 * @author test
 */
public class Test01 {
    
    String username = "admin";
    String password = "12345";
    

    String hostname = "localhost";
    int port = 8080;    
    
    final ObjectMapper mapper = new ObjectMapper();
    private boolean DEBUG;
    
    int repetitions = 1000;
    
    public void doTest() throws MalformedURLException, IOException {
        
        URL url;
        
        url = new URL("http", hostname, port, "/");
        
        
        RPCUtils utils = new RPCUtils(url, username, password);
        
        String userPass = "Basic " + Base64.encodeBase64String((username + ":" + password).getBytes()).trim().replace("\r\n", "");
        // multithreading test
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < repetitions; i++) {
        
            WorkState doGetWorkMessage = utils.doGetWorkMessage(false, userPass);
            
        }
        
        long end = System.currentTimeMillis();
        
        long totalTime= end - start / 1000;
        
        System.out.println("total time(s)=" + totalTime);
        
        System.out.println("time/iter(s)=" + totalTime/repetitions);
    }
    
    public static void main(String [] args) throws MalformedURLException, IOException {
 
        Test01 test = new Test01();
        
        int i = 0;

         while (i < args.length) {

             if (args[i].equals("-s")) {
                 i++;
                 test.hostname = args[i];
             } else if (args[i].equals("-p")) {
                 i++;
                 test.port = Integer.parseInt(args[i]);
             } else if (args[i].equals("-r")) {
                 i++;
                 test.repetitions = Integer.parseInt(args[i]);
             } else if (args[i].equals("-u")) {
                 i++;
                 test.username = args[i];
             }  else if (args[i].equals("-P")) {
                 i++;
                 test.password = args[i];
             }  else if (args[i].equals("-h") || args[i].equals("--help")) {
                   System.out.println("parameters:\n" +
                           "-s: hostname of mcproxy (default: localhost)\n" + 
                           "-p: port of wallet/pool (default: 8080)\n" +   
                           "-r: number of repetitions (default: 1000)\n" +  
                           "-u: wallet rpc username (default: admin)\n" +
                           "-P: wallet rpc password (default: 12345)\n" +
                           "-v: verbose\n\n\n" +
                           "example: "
                           + "java -cp dist\\mcproxy.jar org.mediterraneancoin.proxy.test.Test01  -U admin -P 12345\n"
                           );
                   return;                 
             } else if (args[i].equals("-v")) {
                 test.DEBUG = true;
             }
  
             i++;
         }             
         
         
         test.doTest();
                
        
    }
    
}
