package org.mediterraneancoin.proxy;

import java.net.URL;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.mediterraneancoin.proxy.net.RPCUtils;

/**
 *
 * @author test
 */
public class HttpServer {
    public static void main(String[] args) throws Exception
    {
        
        int localport;
        String hostname;
        int port;

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
                 McproxyHandler.DEBUG = true;
             }
  
             i++;
         }       
        
        System.out.println("MediterraneanCoin Proxy3");
        System.out.println("parameters:\n" + 
                "wallet hostname: " + hostname + "\n" +
                "wallet port: " + port + "\n" +
                "local proxy port: " + localport + "\n"
                );
                
        
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(32);
       
        // default port: 8080
        Server server = new Server(threadPool);
 
        
        server.setHandler(new McproxyHandler());
         
        
        McproxyHandler.url = new URL("http", hostname, port, "/");
        
        McproxyHandler.utils = new RPCUtils(McproxyHandler.url, "", "");        
        
        server.start();
         
        server.join();
    }    
}
