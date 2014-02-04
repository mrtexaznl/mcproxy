package org.mediterraneancoin.proxy.net;
 

/**
 *
 * @author test
 */
public class WorkState {
    
	//final int[] data = new int[32];
	//final int[] midstate = new int[8];
	//final long[] target = new long[8];
    final byte [] data1 = new byte[80];
    final byte [] data2 = new byte[128 - 80];
    
    String target;

    long timestamp;
    long base;

    boolean rollNTimeEnable;
    int rolledNTime;

    RPCUtils utils;

    public WorkState(RPCUtils utils) {
        this.utils = utils;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getBase() {
        return base;
    }

    public void setBase(long base) {
        this.base = base;
    }

    public boolean isRollNTimeEnable() {
        return rollNTimeEnable;
    }

    public void setRollNTimeEnable(boolean rollNTimeEnable) {
        this.rollNTimeEnable = rollNTimeEnable;
    }

    public int getRolledNTime() {
        return rolledNTime;
    }

    public void setRolledNTime(int rolledNTime) {
        this.rolledNTime = rolledNTime;
    }

    public RPCUtils getUtils() {
        return utils;
    }

    public void setUtils(RPCUtils utils) {
        this.utils = utils;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }
    
    
 
    @Override
    public String toString() {
        return "WorkState{" + 
                "data1=" + printHex2(data1) + 
                "data2=" + printHex2(data2) + 
                "target=" + target + 
                //", midstate=" + printHex2(midstate) + 
                //", target=" + printHex2(target) + 
                ", timestamp=" + timestamp + ", base=" + base + ", rollNTimeEnable=" + rollNTimeEnable + ", rolledNTime=" + rolledNTime + ", utils=" + utils + '}';
    }
        
        
    private String printHex2(byte [] b) {
        
        String res = "";
        
        for (int i = 0; i < b.length ; i++) {
            res += ( ff2( Integer.toHexString(((int) b[i]) & 0xFF )) + " ");
        }
        
        //System.out.println();
        res += "\n";
        
        return res;
    }        
    
    private String printHex2(long [] b) {
        
        String res = "";
        
        for (int i = 0; i < b.length ; i++) {
            res += ( ff2( Integer.toHexString(((int) b[i]) & 0xFF )) + " ");
        }
        
        //System.out.println();
        res += "\n";
        
        return res;
    }        
    
    private static String ff2(String a) {
        while (a.length() < 2)
            a = "0" + a;
        
        return a;
    }          

    public void parseData(String datas) {
        
        if (datas.length() != 256) 
            throw new RuntimeException("wrong array len! expected 256");
        
        
        String newStr = "";
        
        for (int i = 0; i < datas.length() ; i += 8) {
            String s0 = datas.substring(i, i + 2);
            String s1 = datas.substring(i + 2, i + 4);
            String s2 = datas.substring(i + 4, i + 6);
            String s3 = datas.substring(i + 6, i + 8);
            
            newStr += s3 + s2 + s1 + s0;
        }        
        
        datas = newStr;
        
        //byte [] a = new byte[80];
        
        for (int i = 0; i < 80*2; i+=2) {
            String n = datas.substring(i, i + 2);

            data1[i / 2] =  (byte) (0xFF & Integer.parseInt(n, 16)); //Byte.parseByte(n, 16);
        }             
        
        for (int i = 80*2; i < 256; i+=2) {
            String n = datas.substring(i, i + 2);

            data2[i / 2 - 80] =  (byte) (0xFF & Integer.parseInt(n, 16)); //Byte.parseByte(n, 16);
        }                 
        
        
    }
    
    public static String byteSwap(String datas) {
        String newStr = "";
        
        for (int i = 0; i < datas.length() ; i += 8) {
            String s0 = datas.substring(i, i + 2);
            String s1 = datas.substring(i + 2, i + 4);
            String s2 = datas.substring(i + 4, i + 6);
            String s3 = datas.substring(i + 6, i + 8);
            
            newStr += s3 + s2 + s1 + s0;
        }        
        
        return newStr;
    }
    
    public String getAllDataHex() {
        
        String res = "";
        String a;
        
        for (int i = 0; i < data1.length; i++) {
            a = Integer.toHexString(((int) data1[i]) & 0xFF );
            
            if (a.length() == 1)
                a = "0" + a;
            
            res += a;
        }
        
        for (int i = 0; i < data2.length; i++) {
            a = Integer.toHexString(((int) data2[i]) & 0xFF );
            
            if (a.length() == 1)
                a = "0" + a;
            
            res += a;
        }        
        
        return res;
    }
    
    public String getAllDataHexByteSwapped() {
        
        String data = getAllDataHex();
        
        return byteSwap(data);
    }

    public byte[] getData1() {
        return data1;
    }

    public byte[] getData2() {
        return data2;
    }
    
    
        
}

