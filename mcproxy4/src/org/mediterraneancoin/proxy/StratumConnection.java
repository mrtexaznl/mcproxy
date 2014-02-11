package org.mediterraneancoin.proxy;

import java.io.IOException;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;

import java.io.PrintStream;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Scanner;
import java.util.Random;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
 

import org.apache.commons.codec.binary.Hex;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;


public class StratumConnection
{

    /** At least for now the job info is held in memory
     * so generate a new session id on JVM restart since all the jobs
     * from the old one are certainly gone.  Also, helps with switching nodes.
     * This way, we reject resumes from other runs.
     */
    //public static final String RUNTIME_SESSION=HexUtil.sha256("" + new Random().nextLong());

    //private StratumServer server;
    private Socket sock;
    private String connection_id;
    private AtomicLong last_network_action;
    private volatile boolean open;
    private volatile boolean mining_subscribe=false;
    //private PoolUser user;
    //private Config config;
   
    private byte[] extranonce1;
    
    private String extraNonce1Str;
    private long extranonce2_size;

    //private UserSessionData user_session_data;
    
    private AtomicLong next_request_id=new AtomicLong(10000);

    private LinkedBlockingQueue<ObjectNode> out_queue = new LinkedBlockingQueue<ObjectNode>();
    private SecureRandom rnd;
    
    static final ObjectMapper mapper = new ObjectMapper();
    
    private long id = -1;
    private String client_version;
    
    String serverAddress;
    int port;
    
    String notifySubscription;
    
    public static void main(String [] arg) throws IOException, InterruptedException {
        StratumConnection connection = new StratumConnection("node4.mediterraneancoin.org", 3333, "1");
        
        connection.open();
        
        connection.miningSubscribe();

        
        Thread.sleep(5000);
        
        
        
        //"{\"id\": 1, \"method\": \"mining.subscribe\", \"params\": []}\\n"
        //ObjectNode o = new ObjectNode(JsonNodeFactory.instance)
        
        
    }

    public StratumConnection(String serverAddress, int port, /*Socket sock,*/ String connection_id)
    {
        //this.server = server;
        //this.config = server.getConfig();
        this.serverAddress = serverAddress;
        this.port = port;
        
        this.connection_id = connection_id;

        open=true;

        last_network_action=new AtomicLong(System.nanoTime());
    
        //Get from user session for now.  Might do something fancy with resume later.
        //extranonce1=UserSessionData.getExtranonce1();

        rnd = new SecureRandom();

        id = rnd.nextLong();
    }
    
    public void open() throws IOException {
        this.sock = new Socket(serverAddress, port);        
        
        new OutThread().start();
        new InThread().start();        
    }
    
    public void miningSubscribe() {
        ObjectNode resultNode = mapper.createObjectNode();
        // {"id": 1, "method": "mining.subscribe", "params": []}\n
        resultNode.put("id", id );
        resultNode.put("method", "mining.subscribe");    
        
        resultNode.putArray("params");
        
        System.out.println(resultNode.asText());
        
        sendMessage(resultNode);        
    }

    public void close()
    {
        open=false;
        try
        {
            sock.close();
        }
        catch(Throwable t){}
    }

    public long getLastNetworkAction()
    {
        return last_network_action.get();
    }

    public long getNextRequestId()
    {
        return next_request_id.getAndIncrement();        
    }

    protected void updateLastNetworkAction()
    {
        last_network_action.set(System.nanoTime());
    }

    public void sendMessage(ObjectNode msg)
    {
        try
        {
            out_queue.put(msg); 
        }
        catch(java.lang.InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

/*
    public void sendRealJob(ObjectNode block_template, boolean clean)
        throws Exception
    {
       
        if (user_session_data == null) return;
        if (!mining_subscribe) return;

        String job_id = user_session_data.getNextJobId();

        JobInfo ji = new JobInfo(server, user, job_id, block_template, extranonce1);

        user_session_data.saveJobInfo(job_id, ji);

        JSONObject msg = ji.getMiningNotifyMessage(clean);
    

        sendMessage(msg);

    }
*/



    public class OutThread extends Thread
    {
        public OutThread()
        {
            setName("OutThread");
            setDaemon(true);
        }

        public void run()
        {
            try
            {
                PrintStream out = new PrintStream(sock.getOutputStream());
                while(open)
                {
                    //Using poll rather than take so this thread will
                    //exit if the connection is closed.  Otherwise,
                    //it would wait forever on this queue
                    ObjectNode msg = out_queue.poll(30, TimeUnit.SECONDS);
                    if (msg != null)
                    {

                        String msg_str = msg.toString();
                        out.println(msg_str);
                        out.flush();

                        System.out.println("Out: " + msg.toString());
                        updateLastNetworkAction();
                    }

                }

            }
            catch(Exception e)
            {
                System.out.println(connection_id + ": " + e);
                e.printStackTrace();
            }
            finally
            {
                close();
            }

        }
    }
    public class InThread extends Thread
    {
        public InThread()
        {
            setName("InThread");
            setDaemon(true);
        }

        public void run()
        {
            try
            {
                Scanner scan = new Scanner(sock.getInputStream());

                while(open)
                {
                    String line = scan.nextLine();
                    updateLastNetworkAction();
                    line = line.trim();
                    if (line.length() > 0)
                    {
                        ObjectNode msg = (ObjectNode) mapper.readTree(line);
                        System.out.println("In: " + msg.toString());
                        processInMessage(msg);
                    }

                }

            }
            catch(Exception e)
            {
                System.out.println("" + connection_id + ": " + e);
                e.printStackTrace();
            }
            finally
            {
                close();
            }

        }
    }
    
    private void processInMessage(ObjectNode msg) {
        
        System.out.println("processInMessage " + msg.toString());
 
        
        long idx = msg.get("id").asLong();
        
        JsonNode errorNode = msg.get("error");
        
        
        JsonNode resultNode = msg.get("result");
        
        String msgStr = msg.toString();
        
        if (msgStr.contains("\"mining.notify\"")) {
            System.out.println("MESSAGE mining.notify");
            
            if (resultNode != null && resultNode instanceof ArrayNode) {
                ArrayNode arrayNode = (ArrayNode) resultNode;                
                
                System.out.println("len: " + arrayNode.size());
                
                for (int i = 0; i < arrayNode.size(); i++) {
                    JsonNode node = arrayNode.get(i);
                    
                    //System.out.println(i + " - " + node.toString());
                    
                    if (node instanceof ArrayNode && node.toString().contains("mining.notify")) {
                        
                        notifySubscription = ((ArrayNode) node).get(1).asText();
                    } else if (i == 1) {
                        extraNonce1Str = node.asText();
                    } else if (i == 2) {
                        extranonce2_size = node.asLong();
                    }
            
                }
            }  
            
            System.out.println("notifySubscription = " + notifySubscription);
            System.out.println("extraNonce1Str = " + extraNonce1Str);
            System.out.println("extranonce2_size = " + extranonce2_size);
            
            return;
        } else if (msgStr.contains("\"mining.set_difficulty\"")) {
            System.out.println("MESSAGE mining.set_difficulty");
            
            
            
            
            return;
        }
        
        
        

        //msg.
        
        //String method = msg.get("method").asText();
        
        
        //System.out.println("method: " + method);
        
        
        
        
        System.out.println(resultNode);
        
        System.out.println(resultNode.getClass().getCanonicalName());
        
/*
 
Out: {"id":6268754711428788574,"method":"mining.subscribe","params":[]}
In: {"error":null,"id":6268754711428788574,"result":[["mining.notify","ae6812eb4cd7735a302a8a9dd95cf71f"],"f8000008",4]}
processInMessage
In: {"params":[256],"id":null,"method":"mining.set_difficulty"}
processInMessage
In: {"params":["1726","ba80e0721a236fca3b1ea994aca86fb5498d81ece49dd1b32ca2fc3c7295d80c","01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff27036bf100062f503253482f046843fa5208","0d2f7374726174756d506f6f6c2f00000000010001b2c4000000001976a914cc31a98aba0c51cd8f355e35adaa86011c0a2a4a88ac00000000",[],"00000002","1b01a0e9","52fa4361",true],"id":null,"method":"mining.notify"}
processInMessage
In: {"params":["1727","ba80e0721a236fca3b1ea994aca86fb5498d81ece49dd1b32ca2fc3c7295d80c","01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff27036bf100062f503253482f048643fa5208","0d2f7374726174756d506f6f6c2f00000000010001b2c4000000001976a914cc31a98aba0c51cd8f355e35adaa86011c0a2a4a88ac00000000",[],"00000002","1b01a0e9","52fa437f",false],"id":null,"method":"mining.notify"}
processInMessage
 */        
        
        
    }

    /*
    private void processInMessage(ObjectNode msg)
        throws Exception
    {
        long idx = msg.optLong("id",-1);
        if (idx != -1 && idx == get_client_id && msg.has("result"))
        {
            client_version = msg.getString("result");
            return;
        }
        Object id = msg.opt("id");
        if (!msg.has("method"))
        {
            System.out.println("Unknown message: " + msg.toString());
            return;
        }
        String method = msg.getString("method");
        if (method.equals("mining.subscribe"))
        {
            JSONObject reply = new JSONObject();
            reply.put("id", id);
            reply.put("error", JSONObject.NULL);
            JSONArray lst2 = new JSONArray();
            lst2.put("mining.notify");
            lst2.put("hhtt");
            JSONArray lst = new JSONArray();
            lst.put(lst2);
            lst.put(Hex.encodeHexString(extranonce1));
            lst.put(4);
            lst.put(RUNTIME_SESSION);
            reply.put("result", lst);

            sendMessage(reply);
            mining_subscribe=true;
        }
        else if (method.equals("mining.authorize"))
        {
            JSONArray params = msg.getJSONArray("params");
            String username = (String)params.get(0);
            String password = (String)params.get(1);

            PoolUser pu = server.getAuthHandler().authenticate(username, password);

            JSONObject reply = new JSONObject();
            reply.put("id", id);
            if (pu==null)
            {
                reply.put("error", "unknown user");
                reply.put("result", false);
                sendMessage(reply);
            }
            else
            {
                reply.put("result", true);
                reply.put("error", JSONObject.NULL);
                //reply.put("difficulty", pu.getDifficulty());
                //reply.put("user", pu.getName());
                user = pu;
                sendMessage(reply);
                sendDifficulty();
                sendGetClient();
                user_session_data = server.getUserSessionData(pu);
                sendRealJob(server.getCurrentBlockTemplate(),false);
            }
            
        }
        else if (method.equals("mining.resume"))
        {
            JSONArray params = msg.getJSONArray("params");
            String session_id = params.getString(0);

            JSONObject reply = new JSONObject();
            reply.put("id", id);
            // should be the same as mining.subscribe
            if (!session_id.equals(RUNTIME_SESSION))
            {
                reply.put("error", "bad session id");
                reply.put("result", false);
                sendMessage(reply);
            }
            else
            {
                reply.put("result", true);
                reply.put("error", JSONObject.NULL);
                sendMessage(reply);
                mining_subscribe=true;
            }
        }
        else if (method.equals("mining.submit"))
        {
            JSONArray params = msg.getJSONArray("params");

            String job_id = params.getString(1);
            JobInfo ji = user_session_data.getJobInfo(job_id);
            if (ji == null)
            {
                JSONObject reply = new JSONObject();
                reply.put("id", id);
                reply.put("result", false);
                reply.put("error", "unknown-work");
                sendMessage(reply);
            }
            else
            {
                SubmitResult res = new SubmitResult();
                res.client_version = client_version;

                ji.validateSubmit(params,res);
                JSONObject reply = new JSONObject();
                reply.put("id", id);

                if (res.our_result.equals("Y"))
                {
                    reply.put("result", true);
                }
                else
                {
                    reply.put("result", false);
                    
                }
                if (res.reason==null)
                {
                    reply.put("error", JSONObject.NULL);
                }
                else
                {
                    reply.put("error", res.reason);
                }
                sendMessage(reply);

                
                if ((res !=null) && (res.reason != null) && (res.reason.equals("H-not-zero")))
                {
                    //User is not respecting difficulty, remind them
                    sendDifficulty();

                }
            }

        }
    }

    private void sendDifficulty()
        throws Exception
    {
        JSONObject msg = new JSONObject();
        msg.put("id", JSONObject.NULL);
        msg.put("method","mining.set_difficulty");

        JSONArray lst = new JSONArray();
        lst.put(user.getDifficulty());
        msg.put("params", lst);

        sendMessage(msg);
    }

    private void sendGetClient()
        throws Exception
    {
        long id = getNextRequestId();

        get_client_id = id;

        JSONObject msg = new JSONObject();
        msg.put("id", id);
        msg.put("method","client.get_version");

        sendMessage(msg);
        
    }
*/

}

