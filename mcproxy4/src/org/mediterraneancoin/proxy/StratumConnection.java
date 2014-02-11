package org.mediterraneancoin.proxy;

import com.diablominer.DiabloMiner.NetworkState.WorkState;
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
import java.util.concurrent.LinkedBlockingDeque;
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
    private volatile boolean miningSubscribed = false;
    //private PoolUser user;
    //private Config config;
   
    private byte[] extranonce1;
    
    private String extraNonce1Str;
    private long extranonce2_size;

    //private UserSessionData user_session_data;
    
    private AtomicLong nextRequestId=new AtomicLong(10000);

    private LinkedBlockingQueue<ObjectNode> out_queue = new LinkedBlockingQueue<ObjectNode>();
    private SecureRandom rnd;
    
    static final ObjectMapper mapper = new ObjectMapper();
    
    private long id = -1;
    private String client_version;
    
    String serverAddress;
    int port;
    
    String notifySubscription;
    
    boolean lastOperationResult;
    long difficulty;
    
    LinkedBlockingDeque<ServerWork> workQueue = new LinkedBlockingDeque<ServerWork>();
    
    public static void main(String [] arg) throws IOException, InterruptedException {
        StratumConnection connection = new StratumConnection("node4.mediterraneancoin.org", 3333, "1");
        
        connection.open();
        
        connection.sendWorkerAuthorization("mrtexaznl.1", "xxx");
        
        Thread.sleep(500);
        
        connection.sendMiningSubscribe();
        
        Thread.sleep(300);
        
        

        
        Thread.sleep(25000);
        
        
        
        //"{\"id\": 1, \"method\": \"mining.subscribe\", \"params\": []}\\n"
        //ObjectNode o = new ObjectNode(JsonNodeFactory.instance)
        
        
    }

    public StratumConnection(String serverAddress, int port,   String connection_id)
    {
 
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
    
    public void sendMiningSubscribe() {
        ObjectNode resultNode = mapper.createObjectNode();
        // {"id": 1, "method": "mining.subscribe", "params": []}\n
        resultNode.put("id", nextRequestId.incrementAndGet() );
        resultNode.put("method", "mining.subscribe");    
        
        resultNode.putArray("params");
        
        System.out.println(resultNode.asText());
        
        sendMessage(resultNode);        
    }
    
    public void sendWorkerAuthorization(String username, String password) {
        
        ObjectNode resultNode = mapper.createObjectNode();
        // {"params": ["slush.miner1", "password"], "id": 2, "method": "mining.authorize"}\n
        ArrayNode putArray = resultNode.putArray("params");
        
        putArray.add(username);
        putArray.add(password);
        
        resultNode.put("id", nextRequestId.incrementAndGet() );
        
        resultNode.put("method", "mining.authorize");  
        
        System.out.println(resultNode.asText());
        
        sendMessage(resultNode);            
        
    }
    
    public void sendWorkSubmission(ServerWork work) {
        
        ObjectNode resultNode = mapper.createObjectNode();
        
        ArrayNode putArray = resultNode.putArray("params");
        
        putArray.add(work.workerName);
        putArray.add(work.jobId);
        putArray.add(work.extraNonce2Str);
        putArray.add(work.nTime);
        putArray.add(work.nonce);
        
        resultNode.put("id", nextRequestId.incrementAndGet() );
        
        resultNode.put("method", "mining.submit");
        
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
        return nextRequestId.getAndIncrement();        
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
    
     public static class ServerWork {
         String jobId;
         String hashPrevBlock;
         String coinbasePart1;
         
         String extraNonce1Str;
         long extranonce2_size;         
         
         String coinbasePart2;
         
         String [] merkleBranches;
         
         String version;
         String nBit;
         String nTime;
         
         boolean cleanJobs;
         
         long difficulty;
         
         long timestamp = System.currentTimeMillis();
         
         //
         String nonce;
         String workerName;
         String extraNonce2Str;
         

        @Override
        public String toString() {
            return "ServerWork{" + "jobId=" + jobId + ", hashPrevBlock=" + hashPrevBlock + ", coinbasePart1=" + coinbasePart1 + ", extraNonce1Str=" + extraNonce1Str + ", extranonce2_size=" + extranonce2_size + ", coinbasePart2=" + coinbasePart2 + ", merkleBranches=" + merkleBranches + ", version=" + version + ", nBit=" + nBit + ", nTime=" + nTime + ", cleanJobs=" + cleanJobs + ", difficulty=" + difficulty + ", timestamp=" + timestamp + '}';
        }


         
         
         
     }    
    
    private void processInMessage(ObjectNode msg) {
        
        System.out.println("processInMessage " + msg.toString());
        // https://www.btcguild.com/new_protocol.php
        
        long idx = msg.get("id").asLong();
        
        JsonNode errorNode = msg.get("error");
        
        
        JsonNode resultNode = msg.get("result");
        
        String msgStr = msg.toString();
        
        if (msgStr.contains("\"mining.notify\"") && msgStr.contains("result")) {
            System.out.println("MESSAGE result - mining.notify");
            
            // {"error":null,"id":6268754711428788574,"result":[["mining.notify","ae6812eb4cd7735a302a8a9dd95cf71f"],"f8000008",4]}
            
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
            System.out.println();
            
            return;
        } else if (msgStr.contains("\"mining.set_difficulty\"")) {
            System.out.println("MESSAGE mining.set_difficulty");
            // {"params":[256],"id":null,"method":"mining.set_difficulty"}
            
            ArrayNode paramsNode = (ArrayNode) msg.get("params");
            
            long newDifficulty = paramsNode.get(0).asLong();
            
            difficulty = newDifficulty;
            
            System.out.println("new difficulty: " + newDifficulty);
            System.out.println();
            return;
        } else if (msgStr.contains("\"mining.notify\"") && msgStr.contains("params")) {
            System.out.println("MESSAGE result - mining.notify");
            
            ArrayNode params = (ArrayNode) msg.get("params");
            
            //{"params":[
            // "178a",
            // "ce2e706306028f9e215c14944c9369b229492e4d70ee2fe6759dae2fbef68114",
            // "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff270398f100062f503253482f04454dfa5208",
            // "0d2f7374726174756d506f6f6c2f000000000100b4f135010000001976a914cc31a98aba0c51cd8f355e35adaa86011c0a2a4a88ac00000000",
            // [],
            // "00000002",
            // "1b01a0e9",
            // "52fa4d3e",
            // true],
            // "id":null,"method":"mining.notify"}
            
            ServerWork newServerWork = new ServerWork();
            newServerWork.jobId = params.get(0).asText();
            newServerWork.hashPrevBlock = params.get(1).asText();
            newServerWork.coinbasePart1 = params.get(2).asText();
            newServerWork.coinbasePart2 = params.get(3).asText();
            
            newServerWork.extraNonce1Str = this.extraNonce1Str;
            newServerWork.extranonce2_size = this.extranonce2_size;
            
            newServerWork.version = params.get(5).asText();
            newServerWork.nBit = params.get(6).asText();
            newServerWork.nTime = params.get(7).asText();
            newServerWork.cleanJobs = params.get(8).asBoolean();
            
            newServerWork.difficulty = this.difficulty;
            
            
            workQueue.add(newServerWork);
            
/*            
params[0] = Job ID. This is included when miners submit a results so work can be matched with proper transactions.
params[1] = Hash of previous block. Used to build the header.
params[2] = Coinbase (part 1). The miner inserts ExtraNonce1 and ExtraNonce2 after this section of the coinbase.
params[3] = Coinbase (part 2). The miner appends this after the first part of the coinbase and the two ExtraNonce values.
params[4][] = List of merkle branches. The coinbase transaction is hashed against the merkle branches to build the final merkle root.
params[5] = Bitcoin block version, used in the block header.
params[6] = nBit, the encoded network difficulty. Used in the block header.
params[7] = nTime, the current time. nTime rolling should be supported, but should not increase faster than actual time.
params[8] = Clean Jobs. If true, miners should abort their current work and immediately use the new job. If false, they can still use the current job, but should move to the new one after exhausting the current nonce range.             
*/
            
            
            System.out.println(newServerWork.toString());
            System.out.println();
            return;            
        } else if (msgStr.contains("\"error\"") && msgStr.contains("\"result\"")) {
            System.out.println("MESSAGE reply");
            
            lastOperationResult = msg.get("result").asBoolean();
            System.out.println("result = " + lastOperationResult);
            
            
            System.out.println();
        }
 
        
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

