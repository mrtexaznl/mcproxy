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

        hostname = "node4.mediterraneancoin.org";
        port = 3333;
        
        String bindAddress = "localhost";
        localport = 8080;        

        String workerName = "mrtexaznl.1";
        String workerPassword = "12345";
        
        int i = 0;
        
        long minDeltaTime = 200;         
        
        int minQueueLength = 2;
        int maxQueueLength = 8;

        boolean DEBUG = false;
        
         while (i < args.length) {

             if (args[i].equals("-s")) {
                 i++;
                 hostname = args[i];
             } else if (args[i].equals("-u")) {
                 i++;
                 workerName = args[i];
             } else if (args[i].equals("-P")) {
                 i++;
                 workerPassword = args[i];
             } else if (args[i].equals("-p")) {
                 i++;
                 port = Integer.parseInt(args[i]);
             } else if (args[i].equals("-m")) {
                 i++;
                 minQueueLength = Integer.parseInt(args[i]);
             } else if (args[i].equals("-M")) {
                 i++;
                 maxQueueLength = Integer.parseInt(args[i]);
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
                           "-t: min delta time (default: 200 ms)\n" + 
                           "-m: mininum queue length (default: 2)\n" + 
                           "-M: maximum queue length (default: 8)\n" + 
                           "-u: worker username\n" +
                           "-P: worker password\n" +
                           "-v: verbose"
                           );
                   return;                 
             } else if (args[i].equals("-v")) {
                 DEBUG = true;
             }
  
             i++;
         }       
        
        System.out.println("MediterraneanCoin Proxy4 - stratum support only!");
        System.out.println("parameters:\n" + 
                "wallet hostname: " + hostname + "\n" +
                "wallet port: " + port + "\n" +
                "bind to local address: " + bindAddress + "\n" +
                "local proxy port: " + localport + "\n" +
                "worker username: " + workerName + "\n" +
                "worker password: " + workerPassword + "\n" +
                "mininum queue length: " + minQueueLength + "\n" +
                "maximum queue length: " + maxQueueLength + "\n" +                
                "verbose: " + DEBUG + "\n"
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
        
        if (DEBUG) {
            System.out.println( "connector.getAcceptors(): " +  connector.getAcceptors() );
            System.out.println( "connector.getAcceptQueueSize(): " + connector.getAcceptQueueSize()) ;
        }
 
        
        StratumConnection.DEBUG = DEBUG;
        McproxyStratumServlet.DEBUG = DEBUG;
        StratumThread.setDEBUG(DEBUG);
        
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
            StratumConnection instance = StratumConnection.getInstance();
            
            instance.open(hostname, port);
            
            
            instance.sendWorkerAuthorization(workerName, workerPassword);
             
            instance.sendMiningSubscribe();
            
            //DEBUG
            //cores = 2;
            
            StratumThread [] stratumThreads = new StratumThread[cores];        
            
            StratumThread.setMinDeltaTime(minDeltaTime);

            StratumThread.setMinQueueLength(minQueueLength);
            
            StratumThread.setMaxQueueLength(maxQueueLength);

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
