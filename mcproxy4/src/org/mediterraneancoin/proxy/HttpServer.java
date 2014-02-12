package org.mediterraneancoin.proxy;

import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.nio.NetworkTrafficSelectChannelConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
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
        
        String bindAddress = "localhost";
        localport = 8080;        

        int i = 0;
        
        long minDeltaTime = 50;         
        
        int minQueueLength = 4;

         while (i < args.length) {

             if (args[i].equals("-s")) {
                 i++;
                 hostname = args[i];
             } else if (args[i].equals("-p")) {
                 i++;
                 port = Integer.parseInt(args[i]);
             } else if (args[i].equals("-m")) {
                 i++;
                 minQueueLength = Integer.parseInt(args[i]);
             } else if (args[i].equals("-t")) {
                 i++;
                 minDeltaTime = Long.parseLong(args[i]);
             } else if (args[i].equals("-b")) {
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
                           "-t: min delta time (default: 50 ms)\n" + 
                           "-m: mininum queue length (default: 4)\n" + 
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
                "bind to local address: " + bindAddress + "\n" +
                "local proxy port: " + localport + "\n"
                );
 
        int cores = Runtime.getRuntime().availableProcessors();
        
        System.out.println("number of detected cores: " + cores);
        
        
 
        QueuedThreadPool threadPool = new QueuedThreadPool(200,16);
       
        // default port: 8080
        Server server = new Server(threadPool);
        
        //server.addBean(new ScheduledExecutorScheduler());
        server.manage(threadPool);
 
         
    
        ServerConnector connector = new ServerConnector(server, 8, 8);
                //new ServerConnector(server, null, null, null, 16, 16, new HttpConnectionFactory(config));
    
                //
        connector.setHost(bindAddress);
        connector.setPort(localport);
        connector.setIdleTimeout(30000);
        connector.setStopTimeout(40000);
        
        System.out.println( "connector.getAcceptors(): " +  connector.getAcceptors() );
        System.out.println( "connector.getAcceptQueueSize(): " + connector.getAcceptQueueSize()) ;
 
        
        //server.setHandler(new McproxyHandler());
        
        ServletHandler handler = new ServletHandler();
        

        
        server.setHandler(handler);
        
        if (false) {
            
            handler.addServletWithMapping(McproxyServlet.class, "/*");        

            McproxyServlet.hostname = hostname;
            McproxyServlet.localport = localport;
            McproxyServlet.port = port;

            McproxyServlet.url = new URL("http", hostname, port, "/");        
            McproxyServlet.utils = new RPCUtils(McproxyServlet.url, "", "");          
        
        } else {
            
            handler.addServletWithMapping(McproxyStratumServlet.class, "/*");        

            McproxyStratumServlet.hostname = hostname;
            McproxyStratumServlet.localport = localport;
            McproxyStratumServlet.port = port;

            McproxyStratumServlet.url = new URL("http", hostname, port, "/");        
            McproxyStratumServlet.utils = new RPCUtils(McproxyStratumServlet.url, "", "");                    
            
        }
        
        server.addConnector(connector);
         
          
        if (false) {
                  
            GetworkThread [] getworkThreads = new GetworkThread[cores];
            
            GetworkThread.setMinDeltaTime(minDeltaTime);

            GetworkThread.setMinQueueLength(4);

            for (int h = 0; h < getworkThreads.length; h++) {
                getworkThreads[h] = new GetworkThread(McproxyServlet.url, McproxyServlet.utils);

                getworkThreads[h].start();

                Thread.sleep(250);
            }
            
        } else {            
            
            StratumThread [] stratumThreads = new StratumThread[cores];        
            
            StratumThread.setMinDeltaTime(minDeltaTime);

            StratumThread.setMinQueueLength(4);

            for (int h = 0; h < stratumThreads.length; h++) {
                stratumThreads[h] = new StratumThread();

                stratumThreads[h].start();

                Thread.sleep(250);
            }
            
            
        }
        
        
        server.start();
         
        server.join();
       
    }    
}
