package org.mediterraneancoin.proxy;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.ServerRunner;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author dev3
 */
public class TestHttpServer  extends NanoHTTPD  {
    
    public TestHttpServer() {
        super(8080);
    }
    

    @Override public Response serve(IHTTPSession session) {
       
        
        Method method = session.getMethod();
        String uri = session.getUri();
        System.out.println(method + " '" + uri + "' ");
        
        Map<String, String> headers = session.getHeaders();
        
        for (Iterator<String> i = headers.keySet().iterator(); i.hasNext();) {
            String key = i.next();
            
            String value = headers.get(key);
            
            System.out.println(key + " = " + value);
        }
        
        String type = headers.get("content-type");
        String authHeader = headers.get("authorization");
        int contentLength = Integer.parseInt( headers.get("content-length") );
        
        byte cbuf[] = new byte[contentLength];
 
         
        try {
 
            
            session.getInputStream().read(cbuf);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(TestHttpServer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(TestHttpServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        String line = null;
        String content = new String(cbuf);
 
        
        System.out.println("content-len: " + content.length());
        System.out.println("content: " + content);
        
                 
        String msg = "<html><body><h1>Hello server</h1>\n";
        Map<String, String> parms = session.getParms();
        if (parms.get("username") == null)
            msg +=
                    "<form action='?' method='get'>\n" +
                            " <p>Your name: <input type='text' name='username'></p>\n" +
                            "</form>\n";
        else
            msg += "<p>Hello, " + parms.get("username") + "!</p>";

        msg += "</body></html>\n";

        return new NanoHTTPD.Response(msg);
    }


    public static void main(String[] args) {
        ServerRunner.run(TestHttpServer.class);
    }    
    
}
