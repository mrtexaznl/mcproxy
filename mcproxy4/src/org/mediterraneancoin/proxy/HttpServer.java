package org.mediterraneancoin.proxy;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import static org.mediterraneancoin.proxy.McproxyStratumServlet.works;
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
        port = 3333;
        
        String bindAddress = "localhost";
        localport = 8080;        

        String workerName = "yourWorkeName";
        String workerPassword = "12345";
        
        int i = 0;
        
        long minDeltaTime = 200;         
        
        int minQueueLength = 2;
        int maxQueueLength = 8;

        boolean DEBUG = false;
        
        int cores = Runtime.getRuntime().availableProcessors();
        
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
             } else if (args[i].equals("-T")) {
                 i++;
                 cores = Integer.parseInt(args[i]);
             } else if (args[i].equals("-b")) {
                 i++;
                 bindAddress = args[i];
             }  else if (args[i].equals("-l")) {
                 i++;
                 localport = Integer.parseInt(args[i]);
             } else if (args[i].equals("-h") || args[i].equals("--help")) {
                   System.out.println("parameters:\n" +
                           "-s: hostname of stratum pool (default: localhost)\n" + 
                           "-p: port of stratum pool (default: 3333)\n" + 
                           "-b: bind to local address (default: )\n" +
                           "-l: local mcproxy port (default: 8080)\n" + 
                           "-t: minimum delta time (default: 200 ms)\n" + 
                           "-T: number of threads (default: number of cores, " + cores + ")\n" + 
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
                "minimum delta time: " + minDeltaTime + "\n" + 
                "number of threads: " + cores + "\n" + 
                "verbose: " + DEBUG + "\n"
                );
 
        
        
        System.out.println("number of detected cores: " + cores);
        
        
 
        QueuedThreadPool threadPool = new QueuedThreadPool(200,16);
       
        // default port: 8080
        Server server = new Server(threadPool);
        
        //server.addBean(new ScheduledExecutorScheduler());
        server.manage(threadPool);
 
         
    
        ServerConnector connector = new ServerConnector(server, 32, 32);
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
            
            
            final String stratumPoolAddress = hostname;
            final int stratumPoolPort = port;
            final String stratumPoolWorkerName = workerName;
            final String stratumPoolWorkerPassword = workerPassword;
             
            
            new Thread() {
            
                @Override
                public void run()  {
                    //System.out.println("***");
 

                    StratumConnection instance = StratumConnection.getInstance();
                    
                    try {
                        instance.open(stratumPoolAddress, stratumPoolPort);
                    } catch (IOException ex) {
                        System.err.println("Error while opening connection to stratum pool " + stratumPoolAddress + "/" + stratumPoolPort + ", " + ex.getMessage());                       
                    }

                    instance.sendMiningSubscribe();

                    instance.sendWorkerAuthorization(stratumPoolWorkerName, stratumPoolWorkerPassword);

                    

                
                    while (true) {
                        try {
                            Thread.sleep(60000);
                        } catch (InterruptedException ex) { }
                        
                        long now = System.currentTimeMillis();
                        
                        long lastOutput = instance.getLastOutputNetworkAction();
                        long lastInput = instance.getLastInputNetworkAction();
                        
                        long delta1 = (now - lastOutput) / 1000;
                        long delta2 = (now - lastInput) / 1000;
                        
                        if (/*delta1 > 120 ||*/ delta2 > 120) {
                            System.err.println(new Date() + " - current connection to stratum pool has timed! reopening a new one...");
                            System.err.flush();
                            
                            try {
                                instance.close();
                            } catch (Exception ex) {      
                                ex.printStackTrace();
                            }
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ex) {
                            }
                            
                            instance = StratumConnection.reset();
                            
                            instance.sendWorkerAuthorization(stratumPoolWorkerName, stratumPoolWorkerPassword);

                            instance.sendMiningSubscribe();                 
                            
                            
                        }
                        
                    }
                    
                    
                    
                    
                }
            
            }.start();
            
            Thread.sleep(5000);

            //for DEBUG only
            //cores = 2;

            StratumThread.setMinDeltaTime(minDeltaTime);            
            
            StratumThread [] stratumThreads = new StratumThread[cores];        

            StratumThread.setMinQueueLength(minQueueLength);

            StratumThread.setMaxQueueLength(maxQueueLength);

            for (int h = 0; h < stratumThreads.length; h++) {
                stratumThreads[h] = new StratumThread();

                stratumThreads[h].start();

                try {
                    Thread.sleep(1000 / cores);
                } catch (InterruptedException ex) { }
            }                
            
            
            
            
            new Thread() {
                
                public void run() {
                    
                    while (true) {
                        try {
                            Thread.sleep(30000);
                        } catch (InterruptedException ex) { }

                        try {
                            System.out.println("BEFORE works.size(): " + works.size());


                            for (Iterator<Map.Entry<String, McproxyHandler.SessionStorage>>it=works.entrySet().iterator();it.hasNext();) {
                            //for (Iterator<String> i = works.keySet().iterator(); i.hasNext();) {
                                //String key = i.next();
                                Map.Entry<String, McproxyHandler.SessionStorage> entry = it.next();

                                McproxyHandler.SessionStorage sessionStorage = entry.getValue();
                                
                                //McproxyHandler.SessionStorage ss = works.get(key);

                                if (sessionStorage == null)
                                    continue;
                                
                                long delta = (System.currentTimeMillis() - sessionStorage.timestamp) / 1000;

                                if (delta > 30) {
                                    sessionStorage.serverWork = null;
                                    sessionStorage.work = null;
                                     
                                    it.remove();
                                } else {
                                    //System.out.println(sessionStorage.serverWork.block_header.substring(0, 80*2));
                                }

                            }
                            
                            System.out.println("AFTER works.size(): " + works.size());
                        } catch (Exception ex) {
                            System.out.println("Error: " + ex.toString());
                        }               
                
                    }
                     
                    
                }
                
                
            }.start();
            
        }
        
        
        server.start();
         
        server.join();
        
    
       
    }    
}
