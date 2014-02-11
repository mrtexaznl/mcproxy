package com.diablominer.DiabloMiner;

import static com.diablominer.DiabloMiner.DiabloMiner.CLEAR;
import com.diablominer.DiabloMiner.NetworkState.JSONRPCNetworkState;
import com.diablominer.DiabloMiner.NetworkState.NetworkState;
import com.diablominer.DiabloMiner.NetworkState.WorkState;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author test
 */
public class StratumAgent implements MinerInterface {
        private boolean debug;
        boolean debugtimer = false;
    
	public final static long TWO32 = 4294967295L;
	public final static long TIME_OFFSET = 7500;    
    
    	AtomicBoolean running = new AtomicBoolean(true);
	List<Thread> threads = new ArrayList<Thread>();
        
        long startTime;
        
	NetworkState networkStateHead = null;
	NetworkState networkStateTail = null;        
        
        Proxy proxy = null;
        int workLifetime = 5000;
        
	AtomicLong blocks = new AtomicLong(0);
	AtomicLong attempts = new AtomicLong(0);
	AtomicLong rejects = new AtomicLong(0);
	AtomicLong hwErrors = new AtomicLong(0);
	Set<String> enabledDevices = null;
	AtomicLong hashCount = new AtomicLong(0);        
        
        public static void main(String[] args) throws Exception {
            StratumAgent agent = new StratumAgent();
            
            agent.execute(args);
        }
    
	void execute(String[] args) throws Exception {
            threads.add(Thread.currentThread());

            String protocol = "http";
            String host = "node4.mediterraneancoin.org";
            int port = 3333;
            String path = "/";
            String user = "mrtexaznl.1";
            String pass = "xxx";         
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            if (true)
                return;
            
            
                
            NetworkState networkState;

            try {
                    networkState = new JSONRPCNetworkState(this, new URL(protocol, host, port, path), user, pass, (byte)0);
            } catch(MalformedURLException e) {
                    throw new RuntimeException("Malformed connection paramaters");
            }
            
            

            if(networkStateHead == null) {
                    networkStateHead = networkStateTail = networkState;
            } else {
                    networkStateTail.setNetworkStateNext(networkState);
                    networkStateTail = networkState;
            }                
                
            
            networkStateTail.setNetworkStateNext(networkStateHead);
            
            debug = true;
            
            
            

            info("Started");

            StringBuilder list = new StringBuilder(networkStateHead.getQueryUrl().toString());
            
            networkState = networkStateHead.getNetworkStateNext();

            while(networkState != networkStateHead) {
                    list.append(", " + networkState.getQueryUrl());
                    networkState = networkState.getNetworkStateNext();
            }

            info("Connecting to: " + list);
            
            
            for (int i = 0; i < 1000; i++) {
                //WorkState doGetWorkMessage = ((JSONRPCNetworkState)networkState).doGetWorkMessage(false);
                
                //System.out.println(doGetWorkMessage.toString());
                
                Thread.sleep(1000);
            }
            
            
            if (true)
                return;
            

            long previousHashCount = 0;
            double previousAdjustedHashCount = 0.0;
            long previousAdjustedStartTime = startTime = (now()) - 1;
            StringBuilder hashMeter = new StringBuilder(80);
            Formatter hashMeterFormatter = new Formatter(hashMeter);            
            

            while(running.get()) {
                /*
                    for(List<? extends DeviceState> deviceStates : allDeviceStates) {
                            for(DeviceState deviceState : deviceStates) {
                                    deviceState.checkDevice();
                            }
                    }
                    */

                    long now = now();
                    
                    long currentHashCount = hashCount.get();
                    double adjustedHashCount = (double) (currentHashCount - previousHashCount) / (double) (now - previousAdjustedStartTime);
                    double hashLongCount = (double) currentHashCount / (double) (now - startTime) / 1000.0;
                    
                    
                    if(now - startTime > TIME_OFFSET * 2) {
                            double averageHashCount = (adjustedHashCount + previousAdjustedHashCount) / 2.0 / 1000.0;

                            hashMeter.setLength(0);

                            if(!debug) {
                                    hashMeterFormatter.format("\rmhash: %.1f/%.1f | accept: %d | reject: %d | hw error: %d", averageHashCount, hashLongCount, blocks.get(), rejects.get(), hwErrors.get());
                            } else {
                                    hashMeterFormatter.format("\rmh: %.1f/%.1f | a/r/hwe: %d/%d/%d | gh: ", averageHashCount, hashLongCount, blocks.get(), rejects.get(), hwErrors.get());

                                    double basisAverage = 0.0;

                                    /*
                                    for(List<? extends DeviceState> deviceStates : allDeviceStates) {
                                            for(DeviceState deviceState : deviceStates) {
                                                    hashMeterFormatter.format("%.1f ", deviceState.getDeviceHashCount() / 1000.0 / 1000.0 / 1000.0);
                                                    basisAverage += deviceState.getBasis();
                                            }
                                    }
                                    */

                                    basisAverage = 1000 / (basisAverage /* / deviceCount*/);

                                    hashMeterFormatter.format("| fps: %.1f", basisAverage);
                            }

                            System.out.print(hashMeter);
                    } else {
                            System.out.print(now() +  "\rWaiting...");
                    }

                    if(now() - TIME_OFFSET * 2 > previousAdjustedStartTime) {
                            previousHashCount = currentHashCount;
                            previousAdjustedHashCount = adjustedHashCount;
                            previousAdjustedStartTime = now - 1;
                    }

                    if(debugtimer && now() > startTime + 60 * 1000) {
                            System.out.print("\n");
                            info("Debug timer is up, quitting...");
                            System.exit(0);
                    }

                    try {
                            if(now - startTime > TIME_OFFSET)
                                    Thread.sleep(1000);
                            else
                                    Thread.sleep(1);
                    } catch(InterruptedException e) {
                    }
            }            
            
            hashMeterFormatter.close();
                
        }

    @Override
    public void addThread(Thread thread) {
        threads.add(thread);
    }

    @Override
    public int getWorkLifetime() {
        return workLifetime;
    }

    @Override
    public Proxy getProxy() {
        return proxy;
    }

    @Override
    public void debug(String msg) {
            if(debug) {
                    System.out.println("\r" + CLEAR + "\r" + DiabloMiner.dateTime() + " DEBUG: " + msg);
                    threads.get(0).interrupt();
            }
    }

    @Override
    public void info(String msg) {
            System.out.println("\r" + CLEAR + "\r" + DiabloMiner.dateTime() + " " + msg);
            threads.get(0).interrupt();
    }

    @Override
    public boolean getRunning() {
        return running.get();
    }

    @Override
    public void error(String msg) {
            System.err.println("\r" + CLEAR + "\r" + DiabloMiner.dateTime() + " ERROR: " + msg);
            threads.get(0).interrupt();
    }

    @Override
    public long incrementBlocks() {
        return blocks.incrementAndGet();
    }

    @Override
    public long incrementRejects() {
        return rejects.incrementAndGet();
    }

    public long incrementAttempts() {
            return attempts.incrementAndGet();
    }    

    public long incrementHWErrors() {
            return hwErrors.incrementAndGet();
    }    


    public static long now() {
            return System.nanoTime() / 1000000;
    }    
    
}
