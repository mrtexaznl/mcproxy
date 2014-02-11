package org.mediterraneancoin.miner;

import static java.lang.System.arraycopy;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import org.mediterraneancoin.miner.scrypt.SCrypt;

/**
 *
 * @author test
 */
public class SuperHasher {
    
    private Mac mac;
 
    
    public SuperHasher() throws GeneralSecurityException {
	    mac = Mac.getInstance("HmacSHA256");
    }
 
    
    public byte [] subarray(byte [] b, int len) {
        
        if (b.length < len)
            throw new RuntimeException("b.length < len");
        
        byte [] result = new byte[len];
        
        arraycopy(b, 0, result, 0, len);
        
        return result;
    }
    
    public byte [] xor(byte [] a, byte [] b) {
        if (a.length != b.length)
            throw new RuntimeException("a.length != b.length");
        
        byte [] result = new byte[a.length];
        
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte)((a[i] ^ b[i]) & 0xFF);
        }
        
        return result;
    }
    
    public byte [] and(byte [] a, byte [] b) {
        if (a.length != b.length)
            throw new RuntimeException("a.length != b.length");
        
        byte [] result = new byte[a.length];
        
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte)((a[i] & b[i]) & 0xFF);
        }
        
        return result;
    }    
    
    public void checkLen(byte [] a, int len) {
        if (a.length != len)
            throw new RuntimeException("checkLen: a.length != len, " + a.length + ", " + len);        
    }
    
    public static int countTopmostZeroBits(byte v) {
        // Count the consecutive zero bits (trailing) on the right in parallel
        // http://graphics.stanford.edu/~seander/bithacks.html#ZerosOnRightLinear

        if (v == 0)
            return 8;
 
        
        for (int x = 7; x >= 0; x--) {
           if ((v & (1L << x)) != 0)
              return (7-x);
        }
        
        return 8;
        
        /*
        if ((v & (1L << 7)) != 0)
           return 0;
        
        if ((v & (1L << 6)) != 0)
           return 1;
        */
         
    }
    
    
    byte [] mask;
    
    private int getMaskByte(int v) {
        switch (v) {
            case 8: return 0;
            case 7: return 1;
            case 6: return 3;    
            case 5: return 7;
            case 4: return 15;    
            case 3: return 31;
            case 2: return 63;
            case 1: return 127;
            case 0: return 255;
            default: System.out.println("!!!!"); return 255;
        }
    }
    
    public int countTopmostZeroBits(byte []arr) {
        int result = 0;
        
        mask = new byte[arr.length];
        
        for (int i = arr.length-1; i >= 0; i--) {
            int v = countTopmostZeroBits(arr[i]);
            
            result += v;
            
            if (v < 8) {
                
                mask[i] = (byte) getMaskByte(v);
                
                for (int h = i-1; h >= 0; h-- ) {
                    if (h >= 0)
                        mask[h] = (byte)255;
                }
                
                break; 
            } 
 
        }
                
        return result;
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
    
    public byte [] hash256(byte [] a) throws NoSuchAlgorithmException {
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        // hash1 size: 32 bytes
        byte [] hash1 = digest.digest(a);        
        
        digest.reset();
        
        // hash2 size: 32 bytes
        byte [] hash2 = digest.digest(hash1);
        
        return hash2;
        
        //System.out.println("hash2 len: " + hash2.length);        
    }
    
    public static byte [] swap(byte [] a) {
        byte [] res = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            res[a.length - 1 - i] = a[i];
        }
        
        return res;
    }    
    
    public static boolean DEBUG = false;
    

    public byte [] firstPartHash(byte[] header) throws GeneralSecurityException {
        

        if (header.length != 80)
            throw new RuntimeException("wrong header len! must be 80");        

        //
        
        byte a,  b,  c,  d;
        
        a = header[75];
        b = header[74];
        c = header[73];
        d = header[72];
                
        
        int nSize;
        
        nSize = a & 0xFF;
        
        boolean negative = (b & 0x80) != 0;
        
        int nWord = ((b & 0x7F) << 16) + ((c & 0xFF) << 8) + (d & 0xFF);        
        
        if (DEBUG) {
            System.out.println("size=" + nSize);
            System.out.println("negative=" + negative);
            System.out.println("nWord=" + nWord);
        }
        
        BigInteger hashTarget = new BigInteger("" + nWord, 10);      
        
        hashTarget = hashTarget.shiftLeft( 8 * (nSize -3));
 

        double [] dd = HasherData.getRow(nSize);
        
        int multiplier = (int) dd[0];
        int rParam = (int) dd[1];
        int pParam = (int) dd[2];
        
        //
        
        if (DEBUG) 
            System.out.println("multiplier: " + multiplier + ", rParam: " + rParam + ", pParam: " + pParam);
        
        if (header.length != 80) {
            throw new RuntimeException("header.length != 80");
        }
        
        // get first 68 bytes of array, out of 80
        byte [] h68 = subarray(header,68);
        
        //
        if (DEBUG) {
            System.out.println("h68");
            printHex2(h68);
        }
        //
        
        long t1,t2;
        
        t1 = System.currentTimeMillis();
        // s76 has a size of 76 bytes
        
        byte [] s68;        
 
        s68 = SCrypt.scryptJ(h68, h68, 1024 * multiplier, rParam, pParam, 68);
 
        if (DEBUG) {
            System.out.print("scryptJ: ");
            printHex2(s68);
        }

        t2 = System.currentTimeMillis();
        
        checkLen(s68, 68);
        
        //System.out.println("hashTarget: " + hashTarget);        
        //System.out.print("hashTarget(binary): ");
        //print( swap( hashTarget.toByteArray() ) );
                
        
        int topmostZeroBits1 = countTopmostZeroBits( swap( hashTarget.toByteArray() ) );
        
        if (DEBUG) {
            System.out.println("topmostZeroBits=" + topmostZeroBits1);
            System.out.println("hashTarget bit len=" + hashTarget.bitLength());
        }
        
        // max length: 512 bit
        
        if (DEBUG) 
            System.out.println("***dt(scrypt1)=" + (t2-t1));
        //
        
        // s76 size: 76 bytes
        s68 = xor(h68, s68);        
        checkLen(s68, 68);  
        
        if (DEBUG) {
            System.out.print("xor: ");
            printHex2(s68);        
        }
        
        
        //
        
        byte [] s68nonce = new byte[80];
        arraycopy(s68, 0, s68nonce, 0, 68);
        
        // keep nTime and nBits fields
        arraycopy(header,68, s68nonce, 68, 8);         
 
        return s68nonce;
        
    }
    
    public byte [] secondPartHash(byte[] header, byte [] targetBits) throws NoSuchAlgorithmException, GeneralSecurityException {

        if (header.length != 80)
            throw new RuntimeException("wrong header len! must be 80. instead it is " + header.length);
        
        //
        byte a,  b,  c,  d;
 
        a = targetBits[3];
        b = targetBits[2];
        c = targetBits[1];
        d = targetBits[0];
                
        
        int nSize;
        
        nSize = a & 0xFF;
        
        boolean negative = (b & 0x80) != 0;
        
        int nWord = ((b & 0x7F) << 16) + ((c & 0xFF) << 8) + (d & 0xFF);        
        
        if (DEBUG) {
            System.out.println("size=" + nSize);
            System.out.println("negative=" + negative);
            System.out.println("nWord=" + nWord);
        }
        
        BigInteger hashTarget = new BigInteger("" + nWord, 10);      
        
        hashTarget = hashTarget.shiftLeft( 8 * (nSize -3));     
        
        if (DEBUG) {
            System.out.println("hashTarget: " + hashTarget.toString(16));
        }
        
        
        double [] dd = HasherData.getRow(nSize);
        
        int multiplier = (int) dd[0];
        int rParam = (int) dd[1];
        int pParam = (int) dd[2];        
        
        //
         
        byte [] s256 = hash256(header);

        //BigInteger hash = new BigInteger( swap(s256) );
        BigInteger hash = new BigInteger(1, swap(s256));

        if (hash.compareTo(BigInteger.ZERO) <= 0) {
            //System.out.println();
            System.out.print("NEG: ");
            printHex2(s256);
            throw new RuntimeException();
 
        } 
  
        if (DEBUG) {
            System.out.print("hash256: ");
            printHex2(s256);            
        }
        //
        
        // this prepares also the mask byte array
        int topmostZeroBits = countTopmostZeroBits(s256);
        
        //
        if (DEBUG) {
            System.out.println("mask");
            printHex2(mask);
            System.out.println("topmostZeroBits=" + topmostZeroBits);
        }

        
        byte [] sc256 = SCrypt.scryptJ(s256, s256, 1024 * multiplier, rParam, pParam, 32); 

        
        
        byte [] maskedSc256 = and(sc256, mask);
        
        //
        if (DEBUG) {
            System.out.println("sc256 = SCrypt.scryptJ(s256, s256, 1024, 1, 4, 32)");
            printHex2(sc256);
            System.out.println("verify: topmostZeroBits=" + countTopmostZeroBits(maskedSc256));
        }
        
        byte [] finalHash = xor(s256, maskedSc256 );        
        
        
        return finalHash;
    }
    

    public byte[] singleHash(byte[] header, int nonce) throws GeneralSecurityException {

        byte a,  b,  c,  d;
        
        a = header[75];
        b = header[74];
        c = header[73];
        d = header[72];
                
        
        int nSize;
        
        nSize = a & 0xFF;
        
        boolean negative = (b & 0x80) != 0;
        
        int nWord = ((b & 0x7F) << 16) + ((c & 0xFF) << 8) + (d & 0xFF);        
        
        if (DEBUG) {
            System.out.println("size=" + nSize);
            System.out.println("negative=" + negative);
            System.out.println("nWord=" + nWord);
        }
        
        BigInteger hashTarget = new BigInteger("" + nWord, 10);      
        
        hashTarget = hashTarget.shiftLeft( 8 * (nSize -3));
 
 
        /////////////////////////////////////////////////////////////////////
        

        double [] dd = HasherData.getRow(nSize);
        
        int multiplier = (int) dd[0];
        int rParam = (int) dd[1];
        int pParam = (int) dd[2];
        
        if (DEBUG) 
            System.out.println("multiplier: " + multiplier + ", rParam: " + rParam + ", pParam: " + pParam);
        
        if (header.length != 80) {
            throw new RuntimeException("header.length != 80");
        }
         
        if (DEBUG) {
            System.out.println("header");
            printHex2(header);            
        }
        
         
        // get first 68 bytes of array, out of 80
        byte [] h68 = subarray(header,68);
        
        //
        if (DEBUG) {
            System.out.println("h68");
            printHex2(h68);
        }
        //
        
        long t1,t2;
        
        t1 = System.currentTimeMillis();
        // s76 has a size of 76 bytes
        
        byte [] s68;        
 
        s68 = SCrypt.scryptJ(h68, h68, 1024 * multiplier, rParam, pParam, 68);
 
        if (DEBUG) {
            System.out.print("scryptJ: ");
            printHex2(s68);
        }

        t2 = System.currentTimeMillis();
        
        checkLen(s68, 68);
 
        int topmostZeroBits1 = countTopmostZeroBits( swap( hashTarget.toByteArray() ) );
        
        if (DEBUG) {
            System.out.println("topmostZeroBits=" + topmostZeroBits1);
            System.out.println("hashTarget bit len=" + hashTarget.bitLength());
        }
        
        // max length: 512 bit
        
        if (DEBUG) 
            System.out.println("***dt(scrypt1)=" + (t2-t1));
        //
        
        // s76 size: 76 bytes
        s68 = xor(h68, s68);        
        checkLen(s68, 68);  
        
        if (DEBUG) {
            System.out.print("xor: ");
            printHex2(s68);        
        }
        
        
        //
        
        byte [] s68nonce = new byte[80];
        arraycopy(s68, 0, s68nonce, 0, 68);
        
        // keep nTime and nBits fields
        arraycopy(header,68, s68nonce, 68, 12);        
        
        // nTime field, keep it
        /*
        s76nonce[68] = header[68];
        s76nonce[69] = header[69];
        s76nonce[70] = header[70];
        s76nonce[71] = header[71];
        */
        if (DEBUG) {
            System.out.print("s68nonce: ");
            printHex2(s68nonce);
        }
        
        //        
        //
        //System.out.println("s76nonce = SCrypt.scryptJ(h76, h76, 1024, 1, 1, 76) | nonce");
        //print(s76nonce);    
        //
        
        byte [] s256 = null; 
        
        //boolean found = false;
        
        t1 = System.currentTimeMillis();
        
        if (true) {
            s68nonce[76] = (byte) (nonce >>  0);
            s68nonce[77] = (byte) (nonce >>  8);
            s68nonce[78] = (byte) (nonce >> 16);
            s68nonce[79] = (byte) (nonce >> 24); 
            
            s256 = hash256(s68nonce);
            
            //BigInteger hash = new BigInteger( swap(s256) );
            BigInteger hash = new BigInteger(1, swap(s256));
             
            if (hash.compareTo(BigInteger.ZERO) <= 0) {
                //System.out.println();
                System.out.print("NEG: ");
                printHex2(s256);
                throw new RuntimeException();
                
                //continue;
            }
            
             
            
        }
        
        t2 = System.currentTimeMillis();
        
        if (DEBUG) 
            System.out.println("***dt(hash256)=" + (t2-t1));
            
        //checkLen(s256, 32);
        
        //
        //System.out.println("s256 = hash256(s76nonce)");
        //print(s256);  
        if (DEBUG) {
            System.out.print("hash256: ");
            printHex2(s256);            
        }
        //
        
        // this prepares also the mask byte array
        int topmostZeroBits = countTopmostZeroBits(s256);
        
        //
        if (DEBUG) {
            System.out.println("mask");
            printHex2(mask);
            System.out.println("topmostZeroBits=" + topmostZeroBits);
        }
        
        t1 = System.currentTimeMillis();
        
        byte [] sc256 = SCrypt.scryptJ(s256, s256, 1024 * multiplier, rParam, pParam, 32); 
        
        t2 = System.currentTimeMillis();
        
        if (DEBUG) 
            System.out.println("***dt(scrypt2)=" + (t2-t1));
        
        
        byte [] maskedSc256 = and(sc256, mask);
        
        //
        if (DEBUG) {
            System.out.println("sc256 = SCrypt.scryptJ(s256, s256, 1024, 1, 4, 32)");
            printHex2(sc256);
            System.out.println("verify: topmostZeroBits=" + countTopmostZeroBits(maskedSc256));
        }
        
        byte [] finalHash = xor(s256, maskedSc256 );
        
        //
        //System.out.println("s256");
        //print(s256);
        
        //
        if (DEBUG) {
            System.out.println("maskedSc256 = and(sc256, mask)");
            printHex2(maskedSc256);

            //
            System.out.println("finalHash = xor(s256, maskedSc256 )");
            printHex2(finalHash);
            //print(finalHash);


            System.out.println("hashTarget");
            printHex2( swap(hashTarget.toByteArray()) );
        }
 
        return finalHash;        
        
        
        
        
        
        //////////////////////////////////////////////////////////////////////
     
        
        
        
    }    
    

    public static BigInteger readCompact(byte a, byte b, byte c, byte d) {
        
        int nSize = a & 0xFF;
        
        boolean negative = (b & 0x80) != 0;
        
        int nWord = ((b & 0x7F) << 16) + ((c & 0xFF) << 8) + (d & 0xFF);
        
        if (DEBUG) {
            System.out.println("size=" + nSize);
            System.out.println("negative=" + negative);
            System.out.println("nWord=" + nWord);
        }
        
        BigInteger result = new BigInteger("" + nWord, 10);
        
        
        result = result.shiftLeft( 8 * (nSize -3));
        
        if (DEBUG)
            System.out.println("result=" + result.toString(16));
        
        return result;
    }    
    
    private static void printHex2(byte [] b) {
        for (int i = 0; i < b.length ; i++) {
            System.out.print( ff2( Integer.toHexString(((int) b[i]) & 0xFF )) + " ");
        }
        
        System.out.println();
    }          
    
    private static String ff2(String a) {
        while (a.length() < 2)
            a = "0" + a;
        
        return a;
    }        
    
    
}
