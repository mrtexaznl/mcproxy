package org.mediterraneancoin.proxy;
 
import java.io.IOException;
import java.net.Socket;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Scanner;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
 


public class StratumConnection
{

    private static StratumConnection instance;
    
    public static synchronized StratumConnection getInstance() {
        if (instance == null)
            instance = new StratumConnection();
        
        return instance;
    }
    
    public static synchronized StratumConnection reset() {
        instance = new StratumConnection();
        return instance;
    }
 
    private Socket sock;

    //private AtomicLong last_network_action;
    private AtomicLong lastOutputNetworkAction = new AtomicLong(System.currentTimeMillis());
    private AtomicLong lastInputNetworkAction = new AtomicLong(System.currentTimeMillis());
    
    private volatile boolean open;
    private volatile boolean miningSubscribed;
    
    private String workerName, workerPassword;
 
   
    //private byte[] extranonce1;
    
    private String extraNonce1Str;
    private long extranonce2_size;
 
    
    private AtomicLong nextRequestId = new AtomicLong(0);

    private LinkedBlockingQueue<ObjectNode> out_queue = new LinkedBlockingQueue<ObjectNode>();
    private SecureRandom rnd;
    
    private LinkedBlockingQueue<String> okJobs = new LinkedBlockingQueue<String>();
    
    static final ObjectMapper mapper = new ObjectMapper();
      
    String serverAddress;
    int port;
    
    String notifySubscription;
    
    boolean lastOperationResult;
    long difficulty;
    
    private LinkedBlockingDeque<ServerWork> workQueue = new LinkedBlockingDeque<ServerWork>();
    
    private LinkedBlockingDeque<StratumResult> stratumResults = new LinkedBlockingDeque<StratumResult>();
    public static boolean DEBUG = true;
    private static final String prefix = "STRATUM-CONNECTION ";
    
    public static class StratumResult {
        long id;
        boolean result;
        
        String error;
        
        long timestamp = System.currentTimeMillis();

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + (int) (this.id ^ (this.id >>> 32));
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final StratumResult other = (StratumResult) obj;
            if (this.id != other.id) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "StratumResult{" + "id=" + id + ", result=" + result + ", error=" + error + '}';
        }

        
        
    }
    
    public static void main(String [] arg) throws IOException, InterruptedException, NoSuchAlgorithmException, CloneNotSupportedException {
        
        StratumConnection connection = new StratumConnection();
        
        if (true) {
            connection.doTests();
            return;
        }
        
        
        connection.open("node4.mediterraneancoin.org", 3333);
        
        connection.sendWorkerAuthorization("mrtexaznl.1", "xxx");
        
        Thread.sleep(500);
        
        connection.sendMiningSubscribe();
        
        Thread.sleep(300);
        
        while (connection.workQueue.size() == 0) {
            Thread.sleep(50);
        }
        ServerWork work = connection.getWork();
        
        System.out.println(work);
        System.out.println("HEADER: " + work.block_header);
        
        Thread.sleep(25000);
        
        
        
        //"{\"id\": 1, \"method\": \"mining.subscribe\", \"params\": []}\\n"
        //ObjectNode o = new ObjectNode(JsonNodeFactory.instance)
        
        
    }

    public StratumConnection()
    {
        rnd = new SecureRandom(); 
    }
    
    public void open(String serverAddress, int port) throws IOException {
        this.serverAddress = serverAddress;
        this.port = port;        
        
        this.sock = new Socket(serverAddress, port);       
        
        //last_network_action=new AtomicLong(System.nanoTime());
        
        open=true;
        
        new OutThread().start();
        new InThread().start();        
        
        System.out.println("successfully connected to stratum pool " + serverAddress + "/" + port);
    }
    
    public void sendMiningSubscribe() {
        ObjectNode resultNode = mapper.createObjectNode();
        // {"id": 1, "method": "mining.subscribe", "params": []}\n
        resultNode.put("id", nextRequestId.incrementAndGet() );
        resultNode.put("method", "mining.subscribe");    
        ArrayNode putArray = resultNode.putArray("params");
        putArray.add("mcproxy4");
        
        if (DEBUG)
            System.out.println(resultNode.asText());
        
        sendMessage(resultNode);        
    }
    
    public boolean sendWorkerAuthorization(String workerName, String workerPassword) {
        
        this.workerName = workerName;
        this.workerPassword = workerPassword;
        
        ObjectNode resultNode = mapper.createObjectNode();
        // {"params": ["slush.miner1", "password"], "id": 2, "method": "mining.authorize"}\n
        ArrayNode putArray = resultNode.putArray("params");
        
        putArray.add(workerName);
        putArray.add(workerPassword);
        
        long requestId = nextRequestId.incrementAndGet();
        resultNode.put("id", requestId );
        
        resultNode.put("method", "mining.authorize");  
        
        if (DEBUG)
            System.out.println(resultNode.asText());
        
        sendMessage(resultNode);           
        
        StratumResult res = null;
        
        long delta = 0;
        
        while ( delta < 20000 && (res = findStratumResult(requestId)) == null) {
            try {
                Thread.sleep(10);
                delta += 10;
            } catch (InterruptedException ex) {
            }            
        }        
        
         if (res == null) {
            if (DEBUG)
               System.out.println(prefix + "sendWorkerAuthorization error: no response from pool in 20s");

            System.err.println("no response from stratum pool in 20s, while sending credentials; bailing out");
            System.err.flush();
            
            //throw new RuntimeException("no response from stratum pool");
            //return false;
        } else {
           if (res.result == false)  {
               
                System.err.println("registration with stratum pool not successful, wrong credentials? bailing out");
                System.err.flush();               
               
                //throw new RuntimeException("credentials registration with stratum pool failed");
           } else {
               
                System.out.println("registration with stratum pool is successful!");
                System.out.flush();                
               
           }
             
             
        }
        
        return res.result;
        
    }
    
    /**
     * the worker which submits this work, must update the nTime and nonce fields
     * @param work 
     */    
    public StratumResult sendWorkSubmission(ServerWork work) {
        
        if (!okJobs.contains(work.jobId)) {
            
            System.out.println(prefix + " sendWorkSubmission error: job " + work.jobId + " is no more valid!");
            
            StratumResult result = new StratumResult();
            result.result = false;
            result.error = "job " + work.jobId + " is no more valid!";
            result.id = 1;
            
            return result;            
            
        }
        
        // 1. Check if blockheader meets requested difficulty
        // NOTE: this is done previously
        
        // 2. Lookup for job and extranonce used for creating given block header
        // (job, extranonce2) = self.get_job_from_header(header)
        // job, extranonce2 are inside work


        // 3. Format extranonce2 to hex string
        //extranonce2_hex = binascii.hexlify(self.extranonce2_padding(extranonce2))
        
        String extranonce2_hex = extranonce2_padding(work);

        // 4. Parse ntime and nonce from header
        //ntimepos = 17*8 # 17th integer in datastring
        //noncepos = 19*8 # 19th integer in datastring
        //ntime = header[ntimepos:ntimepos+8]
        //nonce = header[noncepos:noncepos+8]
        //work.nTime
        //work.nonce
                
        
        
            
        // 5. Submit share to the pool
        //return self.f.rpc('mining.submit', [worker_name, job.job_id, extranonce2_hex, ntime, nonce])        
        // {"params": ["redacted", "1369818357 489", "12000000", "51a5c4f5", "41f20233"], "id": 2, "method": "mining.submit"}        
        
        ObjectNode resultNode = mapper.createObjectNode();
        
        ArrayNode putArray = resultNode.putArray("params");
        
        putArray.add(work.workerName);
        putArray.add(work.jobId);
        putArray.add(extranonce2_hex);
        putArray.add(work.minerNtime);
        putArray.add(work.nonce);
        
        long requestId = nextRequestId.incrementAndGet();
        resultNode.put("id", requestId );
        
        resultNode.put("method", "mining.submit");
        
        if (DEBUG)
            System.out.println(prefix + " sendWorkSubmission: " + resultNode.asText());
        
        sendMessage(resultNode); 
        
        
        StratumResult res = null;
        
        long delta = 0;
        
        while ( delta < 10000 && (res = findStratumResult(requestId)) == null) {
            try {
                Thread.sleep(10);
                delta += 10;
            } catch (InterruptedException ex) {
            }            
        }
        
        if (res == null) {
            if (DEBUG)
                System.out.println(prefix + " sendWorkSubmission error: no response from pool in 10s");
            
            StratumResult result = new StratumResult();
            result.result = false;
            result.error = "no response from pool in 10s";
            result.id = 1;
            
            return result;
        }
        
 
        return res;
        
    }
    
    private StratumResult findStratumResult(long requestId) {
        
        long now = System.currentTimeMillis();
        
        for (Iterator<StratumResult> i = stratumResults.iterator(); i.hasNext();) {
            StratumResult res = i.next();
            
            if (res.id == requestId) {
                if (DEBUG)
                    System.out.println("findStratumResult: " + res.toString());
                                
                stratumResults.remove(res);
                
                return res;
            }            
            
            long delta = (now - res.timestamp) / 1000;
            
            if (delta > 120)
                try {
                    stratumResults.remove(res);
                } catch (Exception ex) {}
            
        }
        
        return null;
    }
    
    final private Object lock = new Object();
    
    public ServerWork getStratumWork() {
        
        ServerWork result = null;
        
        synchronized(lock) {
            
            while ((result = workQueue.pollFirst()) == null) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {      
                }                
            }
        
        }
        
        return result;
    }
    
    public static String extranonce2_padding(ServerWork work) {
        // Update coinbase. Always use an LE encoded nonce2 to fill in values from left to right and prevent overflow errors with small n2sizes
        
        String res;
        
        res = String.format("%016X", work.extranonce2.get());
        
        //res = Long.toHexString(work.extranonce2.get());
        
        if (work.extranonce2_size * 2 < res.length()) {
            //res = res.substring((int)(res.length() - work.extranonce2_size * 2));
            
            res = res.substring(0, (int)(res.length() - work.extranonce2_size * 2));
        } else if (work.extranonce2_size * 2 > res.length()) {
            while (work.extranonce2_size * 2 > res.length())
                //res = "00" + res;
                res = res + "00";
        }
        
        if (DEBUG)
            System.out.println(prefix + " extranonce2_padding: " + res + ", len=" + res.length());
  
        return res;
        
        //ByteBuffer outputByteBuffer = ByteBuffer.allocate((int) work.extranonce2_size);
         
/*
>>> import struct
>>> struct.pack(">I",34)  // big-endian
'\x00\x00\x00"'
>>> ord('"')
34
>>> hex(ord('"'))
'0x22'

 */        
    }
    
    static public byte [] hash256(byte [] a) throws NoSuchAlgorithmException {
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        // hash1 size: 32 bytes
        byte [] hash1 = digest.digest(a);        
        
        digest.reset();
        
        // hash2 size: 32 bytes
        byte [] hash2 = digest.digest(hash1);
        
        return hash2;
        
        //System.out.println("hash2 len: " + hash2.length);        
    }    
    
    public static String toHexString(byte [] barr) {
        
        String result = "";
        
        for (int i = 0; i < barr.length; i++) {
            
            result += String.format("%02X", barr[i]);
                        
        }
        
        return result;
        
    }
    
    public static byte [] toByteArray(String bin) {
        if (bin.length() % 2 != 0)
            throw new RuntimeException("bin.length() % 2 != 0");
        
        byte [] result = new byte[bin.length() / 2];
        
        for (int i = 0; i < result.length; i++) {
            
            String b = bin.substring(i * 2, (i+1) * 2);
            
            result[i] = (byte) (Integer.parseInt(b, 16) & 0xFF);
            
        }
        
        return result;
    }
    
    public static byte [] reverseHash(byte [] barr) {
        if (barr.length % 4 != 0)
            throw new RuntimeException("barr.length() % 4 != 0");        
        
        byte [] result = new byte[barr.length];
        
        for (int i = 0; i < barr.length; i += 4) {
           result[i] = barr[i+3];
           result[i+1] = barr[i+2];
           result[i+2] = barr[i+1];
           result[i+3] = barr[i];
        }
        
        return result;
    }
    
 
    
    public static long reverse(long x) {  
        ByteBuffer bbuf = ByteBuffer.allocate(8);  
        bbuf.order(ByteOrder.BIG_ENDIAN);  
        bbuf.putLong(x);  
        bbuf.order(ByteOrder.LITTLE_ENDIAN);  
        return bbuf.getLong(0);
    }      
    
    public ServerWork getWork() throws NoSuchAlgorithmException, CloneNotSupportedException {
        
        if (DEBUG) {
            System.out.println(prefix + "getWork, workQueue size: " + workQueue.size());
        }
        
        // Pick the latest job from pool
        ServerWork originalWork = workQueue.peek();
        
        if (originalWork == null) {
            if (DEBUG) {
                System.out.println(prefix + "getWork, returning null!");
            }
            
            return null;
        }
        
        ServerWork work = (ServerWork) originalWork.clone();
        
        work.timestamp = System.currentTimeMillis();
        
        
        // 1. Increase extranonce2
        long extranonce2 = reverse(originalWork.extranonce2.incrementAndGet());
        
        // we need to truncate extranonce2, respecting its size!
        //extranonce2 = (extranonce2 >> 32) & 0xFFFFFFFF;
        
        work.extranonce2 = new AtomicLong(extranonce2);
        
        // 2. Build final extranonce
        String extranonce = work.extraNonce1Str + extranonce2_padding(work);
        
        if (DEBUG)
            System.out.println(prefix + " extranonce:" + extranonce);
        
        // 3. Put coinbase transaction together
        String coinbase_bin = work.coinbasePart1 + extranonce + work.coinbasePart2;
                // self.coinb1_bin + extranonce + self.coinb2_bin
        
        // 4. Calculate coinbase hash
        byte [] coinbase_hash = hash256( toByteArray(coinbase_bin) );
        
        // 5. Calculate merkle root
        // merkle_root = binascii.hexlify(utils.reverse_hash(job.build_merkle_root(coinbase_hash)))
        byte [] merkle_root = reverseHash ( buildMerkleRoot(work, coinbase_hash)  );
        String merkleRootStr = toHexString( merkle_root );
        
/*      
        # 3. Put coinbase transaction together
        coinbase_bin = job.build_coinbase(extranonce)
        
        # 4. Calculate coinbase hash
        coinbase_hash = utils.doublesha(coinbase_bin)
        
        # 5. Calculate merkle root
        merkle_root = binascii.hexlify(utils.reverse_hash(job.build_merkle_root(coinbase_hash)))

        # 6. Generate current ntime
        ntime = int(time.time()) + job.ntime_delta
        
        # 7. Serialize header
        block_header = job.serialize_header(merkle_root, ntime, 0)
        
        * 
 * 
     def build_merkle_root(self, coinbase_hash):
        merkle_root = coinbase_hash
        for h in self.merkle_branch:
            merkle_root = utils.doublesha(merkle_root + h)
        return merkle_root       
*/        
        
        // 6. Generate current ntime
        //ntime = int(time.time()) + job.ntime_delta
        // job.ntime_delta = int(ntime, 16) - int(time.time())         
        long unixTime = System.currentTimeMillis() / 1000L;               
        long ntime = unixTime + work.ntime_delta;
        
        work.minerNtime = String.format("%04X", ntime);
             
        // 7. Serialize header
        //block_header = job.serialize_header(merkle_root, ntime, 0)
        work.block_header = 
                work.version +
                work.hashPrevBlock +
                merkleRootStr + 
                work.minerNtime +
                work.nBit +
                "00000000000000" +                
                "800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000";
                
/*                
    def serialize_header(self, merkle_root, ntime, nonce):
        r = self.version
        r += self.prevhash
        r += merkle_root
        r += binascii.hexlify(struct.pack(">I", ntime))
        r += self.nbits
        r += binascii.hexlify(struct.pack(">I", nonce))
        r += '000000800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000' # padding
        return r                 
                
*/
        // 8. Register job params
        //self.register_merkle(job, merkle_root, extranonce2)    
        
        setDifficulty(work, difficulty);
        
        if (DEBUG) {
            System.out.println(prefix + "getWork, returning: " + work);
        }        
        
        return work;
    }
    
    static byte [] concat(byte [] a, byte [] b) {
        byte [] result = new byte[a.length + b.length];
        
        System.arraycopy(a, 0, result, 0, a.length);
        
        System.arraycopy(b,0, result, a.length, b.length);
        
        return result;
    }
    
    static public byte [] buildMerkleRoot(ServerWork work, byte [] coinbase_hash) throws NoSuchAlgorithmException {
        byte [] merkle_root = coinbase_hash;
        
        for (int i = 0; i < work.merkleBranches.length; i++) {
            
            byte [] h = toByteArray( work.merkleBranches[i] );
            
            merkle_root = hash256( concat(merkle_root, h) );
            
        }
        
        return merkle_root;
    }
    
/*
    def set_difficulty(self, new_difficulty):
        if self.scrypt_target:
            dif1 = 0x0000ffff00000000000000000000000000000000000000000000000000000000
        else:
            dif1 = 0x00000000ffff0000000000000000000000000000000000000000000000000000
        self.target = int(dif1 / new_difficulty)
        self.target_hex = binascii.hexlify(utils.uint256_to_str(self.target))
        self.difficulty = new_difficulty
 */    
    void setDifficulty(ServerWork work, long new_difficulty) {
        BigInteger dif1 = new BigInteger("00000000ffff0000000000000000000000000000000000000000000000000000", 16);
        
        work.target = dif1.divide( BigInteger.valueOf(new_difficulty) );
        
        byte [] tbe = work.target.toByteArray(); // big endian
        
        work.target_hex = "";
        
        for (int i = tbe.length - 1; i >= 0; i--)
            work.target_hex += String.format("%02X", tbe[i]);
        
        while (work.target_hex.length() < 64)
            work.target_hex += "00";
        
    }
    
/*
        
https://github.com/slush0/stratum-mining-proxy/blob/master/mining_libs/jobs.py

    def getwork(self, no_midstate=True):
        '''Miner requests for new getwork'''
        
        job = self.last_job # Pick the latest job from pool

        # 1. Increase extranonce2
        extranonce2 = job.increase_extranonce2()
        
        # 2. Build final extranonce
        extranonce = self.build_full_extranonce(extranonce2)
        
        # 3. Put coinbase transaction together
        coinbase_bin = job.build_coinbase(extranonce)
        
        # 4. Calculate coinbase hash
        coinbase_hash = utils.doublesha(coinbase_bin)
        
        # 5. Calculate merkle root
        merkle_root = binascii.hexlify(utils.reverse_hash(job.build_merkle_root(coinbase_hash)))

        # 6. Generate current ntime
        ntime = int(time.time()) + job.ntime_delta
        
        # 7. Serialize header
        block_header = job.serialize_header(merkle_root, ntime, 0)

        # 8. Register job params
        self.register_merkle(job, merkle_root, extranonce2)
        
        # 9. Prepare hash1, calculate midstate and fill the response object
        header_bin = binascii.unhexlify(block_header)[:64]
        hash1 = "00000000000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000010000"

        result = {'data': block_header,
                'hash1': hash1}
        
        if self.use_old_target:
            result['target'] = 'ffffffffffffffffffffffffffffffffffffffffffffffffffffffff00000000'
        elif self.real_target:
            result['target'] = self.target_hex
        else:
            result['target'] = self.target1_hex
    
        if calculateMidstate and not (no_midstate or self.no_midstate):
            # Midstate module not found or disabled
            result['midstate'] = binascii.hexlify(calculateMidstate(header_bin))
            
        return result 
 */    

    public void close()
    {
        open=false;
        try
        {
            sock.close();
        }
        catch(Throwable t){}
    }


    public long getNextRequestId()
    {
        return nextRequestId.getAndIncrement();        
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

                        if (DEBUG)
                            System.out.println("Out: " + msg.toString());
                        
                        lastOutputNetworkAction.set(System.currentTimeMillis());
                        //updateLastNetworkAction();
                    }

                }

            }
            catch(Exception e)
            {
                System.out.println( e.toString() );
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
                    //updateLastNetworkAction();
                    lastInputNetworkAction.set(System.currentTimeMillis());
                    
                    line = line.trim();
 
                    
                    if (line.length() > 0)
                    {
                        ObjectNode msg = (ObjectNode) mapper.readTree(line);
                        
                        if (DEBUG)
                            System.out.println("In: " + msg.toString());
                        
                        try {
                            processInMessage(msg);
                        } catch (Exception ex) {
                            System.err.println(prefix + " exception in InThread: " + ex.toString());
                        }
                    }

                }

            }
            catch(Exception e)
            {
                System.out.println(e.toString());
                e.printStackTrace();
            }
            finally
            {
                close();
            }

        }
    }
    
     public static class ServerWork implements Cloneable {
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
         //String extraNonce2Str;
         
         //long extranonce1;
         AtomicLong extranonce2  = new AtomicLong(0L);
         
         long ntime_delta;
         
         // for getwork
         
         BigInteger target;
         String target_hex;
         String block_header;
         String minerNtime;

        @Override
        public String toString() {
            
            String mb = "";
            for (int i = 0; i < merkleBranches.length; i++)
                mb += "merkleBranches[" + i + "]=" + merkleBranches[i] + ";";
            
            return "ServerWork{" + "jobId=" + jobId + ", hashPrevBlock=" + hashPrevBlock + ", coinbasePart1=" + coinbasePart1 + 
                    ", extraNonce1Str=" + extraNonce1Str + ", extranonce2_size=" + extranonce2_size + ", coinbasePart2=" + coinbasePart2 + ", merkleBranches=" + mb + 
                    ", version=" + version + ", nBit=" + nBit + ", nTime=" + nTime + ", cleanJobs=" + cleanJobs + ", difficulty=" + difficulty + ", timestamp=" + timestamp + 
                    ", nonce=" + nonce + ", workerName=" + workerName + //", extranonce1=" + extranonce1 + 
                    ", extranonce2=" + extranonce2 + ", ntime_delta=" + ntime_delta + ", target=" + target + ", target_hex=" + target_hex + ", block_header=" + block_header + '}';
        }




        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone();  
        }
 
        
        
     }
     
    private void doTests() throws NoSuchAlgorithmException, CloneNotSupportedException {
        
// 'mining.notify', u'ae6812eb4cd7735a302a8a9dd95cf71f'], u'f800003e', 4        
            this.extraNonce1Str = "f800003e";
            this.extranonce2_size = 4;
            this.difficulty = 256;
        
            ServerWork newServerWork = new ServerWork();
            newServerWork.jobId = "281c";
            newServerWork.hashPrevBlock = "de68011079eef077f92c231a08cc08c411ced9d385f8d4c9adc961fc22c6c533";
            newServerWork.coinbasePart1 = "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff27030cf700062f503253482f044a92fb5208";
            newServerWork.coinbasePart2 = "0d2f7374726174756d506f6f6c2f000000000100adf4ca010000001976a914cc31a98aba0c51cd8f355e35adaa86011c0a2a4a88ac00000000";
             
            newServerWork.merkleBranches = new String[0];

            newServerWork.extraNonce1Str = this.extraNonce1Str;
            newServerWork.extranonce2_size = this.extranonce2_size;            
                        
            newServerWork.version = "00000002";
            newServerWork.nBit = "1b01b269";
            newServerWork.nTime = "52fb9247";
            newServerWork.cleanJobs = false;
            
            newServerWork.difficulty = this.difficulty;
            
            long unixTime = System.currentTimeMillis() / 1000L;        
            newServerWork.ntime_delta = Long.parseLong(newServerWork.nTime, 16) - unixTime;
            
            if (newServerWork.cleanJobs) {
                if (DEBUG)
                    System.out.println("cleanJobs == true! cleaning work queue");
                
                workQueue.clear();
            }
            
            workQueue.add(newServerWork);
        
            
/*
[u'281c',
 u'de68011079eef077f92c231a08cc08c411ced9d385f8d4c9adc961fc22c6c533', 
u'01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff27030cf700062f503253482f044a92fb5208', 
u'0d2f7374726174756d506f6f6c2f000000000100adf4ca010000001976a914cc31a98aba0c51cd8f355e35adaa86011c0a2a4a88ac00000000', 
[], 
u'00000002', 
u'1b01b269',
u'52fb9247',
False]
 * 
 * 
"data": "00000002bcc8c807468a23955657c90f74ba988d72b0762190f3bb7cb58b611d30a43570708b3f03aa00d5dcdbb4a874adbe748e218e80b120535605c86aa4efcc87d84152fb8e3e1b01b26900000000000000800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000", 
"target": "0000000000000000000000000000000000000000000000000000ffff00000000"}
* 
 */        

            ServerWork work = getWork();
            
            
            setDifficulty(work, 256);
            

            if (DEBUG) {
                System.out.println(work);         

                System.out.println(work.target);
                System.out.println(work.target_hex);
            }
        
        
    }     
    
    private void processInMessage(ObjectNode msg) {
        
        //System.out.println(prefix + "processInMessage " + msg.toString());
        // https://www.btcguild.com/new_protocol.php
        
        long idx = msg.get("id").asLong();
        
        JsonNode errorNode = msg.get("error");
        
        
        JsonNode resultNode = msg.get("result");
        
        String msgStr = msg.toString();
        
        boolean debugthis = true;
        
        if (msgStr.contains("\"mining.notify\"") && msgStr.contains("result")) {
            
            if (debugthis || DEBUG)
                System.out.println(prefix + "MESSAGE mining.notify (work subscription confirmation) " + msgStr);
            
            // {"error":null,"id":6268754711428788574,"result":[["mining.notify","ae6812eb4cd7735a302a8a9dd95cf71f"],"f8000008",4]}
            
            if (resultNode != null && resultNode instanceof ArrayNode) {
                ArrayNode arrayNode = (ArrayNode) resultNode;                
                
                //System.out.println("len: " + arrayNode.size());
                
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
            
            miningSubscribed = true;
            
            if (DEBUG) {
                System.out.println(prefix + "notifySubscription = " + notifySubscription);
                System.out.println(prefix + "extraNonce1Str = " + extraNonce1Str);
                System.out.println(prefix + "extranonce2_size = " + extranonce2_size);
                System.out.println();
            }
            
            return;
        } else if (msgStr.contains("\"mining.set_difficulty\"")) {
            if (debugthis || DEBUG)
                System.out.println(prefix + "MESSAGE mining.set_difficulty " + msgStr);
            // {"params":[256],"id":null,"method":"mining.set_difficulty"}
            
            ArrayNode paramsNode = (ArrayNode) msg.get("params");
            
            long newDifficulty = paramsNode.get(0).asLong();
            
            difficulty = newDifficulty;
            
            if (DEBUG) {
                System.out.println(prefix + "new difficulty: " + newDifficulty);
                System.out.println();
            }
            return;
        } else if (msgStr.contains("\"mining.notify\"") && msgStr.contains("params")) {
            if (debugthis || DEBUG)
                System.out.println(prefix + "MESSAGE mining.notify (work push) " + msgStr);
            
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
            newServerWork.workerName = this.workerName;
            //newServerWork.workerPassword = this.workerPassword;
            
            newServerWork.jobId = params.get(0).asText();
            newServerWork.hashPrevBlock = params.get(1).asText();
            newServerWork.coinbasePart1 = params.get(2).asText();
            newServerWork.coinbasePart2 = params.get(3).asText();
            
            ArrayNode branches = (ArrayNode) params.get(4);
            newServerWork.merkleBranches = new String[branches.size()];
            for (int i = 0; i < branches.size(); i++)
                newServerWork.merkleBranches[i] = branches.get(i).asText();
                       
            newServerWork.extraNonce1Str = this.extraNonce1Str;
            newServerWork.extranonce2_size = this.extranonce2_size;
            
            newServerWork.version = params.get(5).asText();
            newServerWork.nBit = params.get(6).asText();
            newServerWork.nTime = params.get(7).asText();
            newServerWork.cleanJobs = params.get(8).asBoolean();
            
            newServerWork.difficulty = this.difficulty;
            
            long unixTime = System.currentTimeMillis() / 1000L;        
            newServerWork.ntime_delta = Long.parseLong(newServerWork.nTime, 16) - unixTime;
            
            if (newServerWork.cleanJobs) {
                if (true || DEBUG)
                    System.out.println(prefix + " cleanJobs == true! cleaning work queue");
                
                StratumThread.getQueue().clear();
                
                // also clear the list of acceptable jobs
                okJobs.clear();
                
                workQueue.clear();
            }
            
            if (!okJobs.contains(newServerWork.jobId))
               okJobs.add(newServerWork.jobId);
            
            workQueue.add(newServerWork);
            
            if (DEBUG)
                System.out.println(prefix + " workQueue size: " + workQueue.size() );
            
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
            
            if (DEBUG) {
                System.out.println(newServerWork.toString());
                System.out.println();
            }
            return;            
        } else if (msgStr.contains("\"error\"") && msgStr.contains("\"result\"")) {
            
            
            
            if (debugthis || DEBUG)
                System.out.println(prefix + "MESSAGE result: " + msgStr);
            
            lastOperationResult = msg.get("result").asBoolean();
                
            if (debugthis || DEBUG) {
               System.out.println(prefix + "result = " + lastOperationResult);
            }
            
            StratumResult result = new StratumResult();
            result.id = msg.get("id").asLong();
            result.error = msg.get("error").toString();
            result.result = msg.get("result").asBoolean();
            
            stratumResults.add(result);
            
            if (debugthis || DEBUG) {
                System.out.println(prefix + "complete result = " + result);
                
                System.out.println(prefix + "stratumResults size: " + stratumResults.size());

                System.out.println();
            }
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

    public LinkedBlockingDeque<StratumResult> getStratumResults() {
        return stratumResults;
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

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public String getExtraNonce1Str() {
        return extraNonce1Str;
    }

    public void setExtraNonce1Str(String extraNonce1Str) {
        this.extraNonce1Str = extraNonce1Str;
    }

    public long getExtranonce2_size() {
        return extranonce2_size;
    }

    public void setExtranonce2_size(long extranonce2_size) {
        this.extranonce2_size = extranonce2_size;
    }

    public long getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(long difficulty) {
        this.difficulty = difficulty;
    }

    public long getLastOutputNetworkAction() {
        return lastOutputNetworkAction.get();
    }

    public long getLastInputNetworkAction() {
        return lastInputNetworkAction.get();
    }
    
    

}

