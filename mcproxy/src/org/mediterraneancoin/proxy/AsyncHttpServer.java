package org.mediterraneancoin.proxy;

import org.mediterraneancoin.proxy.thread.TaskImpl;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.mediterraneancoin.miner.SuperHasher;
import org.mediterraneancoin.proxy.net.RPCUtils;
import static org.mediterraneancoin.proxy.net.RPCUtils.tohex;
import org.mediterraneancoin.proxy.net.WorkState;
import org.mediterraneancoin.proxy.util.SessionStorage;
import org.simpleframework.http.ContentType;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;


/**
 *
 * @author marco
 */
public class AsyncHttpServer implements Container {
 
 
    
    public static boolean DEBUG = false;    
    
    private final Executor executor;
    
   
   
   public AsyncHttpServer(String hostname, int port ) throws MalformedURLException {
       
        this.executor = Executors.newCachedThreadPool();
 
       
        URL url = new URL("http", hostname, port, "/");
        
 
        SuperHasher.DEBUG = false;       
        
        TaskImpl.setUrl(url);
         
   }
   

   public AsyncHttpServer(String hostname, int port, int size) throws MalformedURLException {
       
        this.executor = Executors.newFixedThreadPool(size);
 
       
        URL url = new URL("http", hostname, port, "/");
        
 
        SuperHasher.DEBUG = false;       
         
        TaskImpl.setUrl(url);

   }

    @Override
   public void handle(Request request, Response response) {
      TaskImpl task = new TaskImpl(request, response);
      
      executor.execute(task);
   }
    
    
}
