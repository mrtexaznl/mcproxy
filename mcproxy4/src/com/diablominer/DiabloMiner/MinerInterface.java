package com.diablominer.DiabloMiner;

import java.net.Proxy;

/**
 *
 * @author test
 */
public interface MinerInterface {
    
    public void addThread(Thread thread);

    public int getWorkLifetime();

    public Proxy getProxy();

    public void debug(String string);

    public void info(String string);

    public boolean getRunning();

    public void error(String string);

    public long incrementBlocks();

    public long incrementRejects();
    
}
