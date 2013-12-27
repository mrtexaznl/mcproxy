package org.mediterraneancoin.proxy.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import org.apache.commons.codec.binary.Base64;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.NullNode;
import org.codehaus.jackson.node.ObjectNode;
 
/**
 *
 * @author test
 */
public class RPCUtils {
    
    	URL queryUrl;
	String user;
	String pass;
        
        Proxy proxy = null;
    
    
	URL longPollUrl;
	String userPass;

	boolean rollNTime = false;
	boolean noDelay = false;
	String rejectReason = null;    

        //LongPollAsync longPollAsync = null;

	long workLifetime;
 
        
        final ObjectMapper mapper = new ObjectMapper();
        
        
	public RPCUtils(  URL queryUrl, String user, String pass  ) {
 
		this.queryUrl = queryUrl;
		this.user = user;
		this.pass = pass;
 
		this.rollNTime = false;

		//if("stratum".equals(queryUrl.getProtocol()))
		//	hostProtocol = PROTOCOL_STRATUM;            
            
		//super(diabloMiner, queryUrl, user, pass, hostChain);
		this.userPass = "Basic " + Base64.encodeBase64String((user + ":" + pass).getBytes()).trim().replace("\r\n", "");

                /*
		Thread thread = new Thread(getWorkAsync, "DiabloMiner JSONRPC GetWorkAsync for " + queryUrl.getHost());
		thread.start();
		diabloMiner.addThread(thread);

		thread = new Thread(sendWorkAsync, "DiabloMiner JSONRPC SendWorkAsync for " + queryUrl.getHost());
		thread.start();
		diabloMiner.addThread(thread);*/
                
	}        
        
        
    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }        
        
    JsonNode doJSONRPCCall(boolean longPoll, ObjectNode message, boolean printLogMessages, String authHeader) throws IOException {
      HttpURLConnection connection = null;
		try {
	      URL url;

	      if(longPoll)
	      	url = longPollUrl;
	      else
	      	url = queryUrl;

	      //Proxy proxy = diabloMiner.getProxy();

	      if(proxy == null)
	      	connection = (HttpURLConnection) url.openConnection();
	      else
	      	connection = (HttpURLConnection) url.openConnection(proxy);

	      if(longPoll) {
	      	connection.setConnectTimeout(10 * 60 * 1000);
	      	connection.setReadTimeout(10 * 60 * 1000);
	      } else {
	      	connection.setConnectTimeout(15 * 1000);
	      	connection.setReadTimeout(15 * 1000);
	      }

              //System.out.println("AUTH: " + authHeader);
              
	      connection.setRequestProperty("Authorization", authHeader != null ? authHeader : userPass);
	      connection.setRequestProperty("Accept", "application/json");
	      connection.setRequestProperty("Accept-Encoding", "gzip,deflate");
	      connection.setRequestProperty("Content-Type", "application/json");
	      connection.setRequestProperty("Cache-Control", "no-cache");
	      connection.setRequestProperty("User-Agent", "DiabloMiner");
	      connection.setRequestProperty("X-Mining-Extensions", "longpoll rollntime switchto");
	      connection.setDoOutput(true);

	      OutputStream requestStream = connection.getOutputStream();
	      Writer request = new OutputStreamWriter(requestStream);
	      request.write(message.toString());
	      request.close();
	      requestStream.close();
              
              if (printLogMessages) {
                  System.out.println("RCPUtils: sending data: " + message.toString());
              }

	      ObjectNode responseMessage = null;

	      InputStream responseStream = null;

              
	      try {
                  
                  /*
	      	String xLongPolling = connection.getHeaderField("X-Long-Polling");

	      	if(xLongPolling != null && !"".equals(xLongPolling) && longPollAsync == null) {
	      		if(xLongPolling.startsWith("http"))
	      			longPollUrl = new URL(xLongPolling);
	      		else if(xLongPolling.startsWith("/"))
	      			longPollUrl = new URL(queryUrl.getProtocol(), queryUrl.getHost(), queryUrl.getPort(), xLongPolling);
	      		else
	      			longPollUrl = new URL(queryUrl.getProtocol(), queryUrl.getHost(), queryUrl.getPort(), (url.getFile() + "/" + xLongPolling).replace("//", "/"));

	      		longPollAsync = new LongPollAsync();
	      		Thread thread = new Thread(longPollAsync, "DiabloMiner JSONRPC LongPollAsync for " + url.getHost());
	      		thread.start();
	      		diabloMiner.addThread(thread);

	      		workLifetime = 60000;

	      		debug(queryUrl.getHost() + ": Enabling long poll support");
	      	}

	      	String xRollNTime = connection.getHeaderField("X-Roll-NTime");

	      	if(xRollNTime != null && !"".equals(xRollNTime)) {
	      		if(!"n".equalsIgnoreCase(xRollNTime) && rollNTime == false) {
	      			rollNTime = true;

	      			if(xRollNTime.startsWith("expire=")) {
	      				try {
	      					workLifetime = Integer.parseInt(xRollNTime.substring(7)) * 1000;
	      				} catch(NumberFormatException ex) { }
	      			} else {
	      				workLifetime = 60000;
	      			}

	      			diabloMiner.debug(queryUrl.getHost() + ": Enabling roll ntime support, expire after " + (workLifetime / 1000) + " seconds");
	      		} else if("n".equalsIgnoreCase(xRollNTime) && rollNTime == true) {
	      			rollNTime = false;

	      			if(longPoll)
	      				workLifetime = 60000;
	      			else
	      				workLifetime = diabloMiner.getWorkLifetime();

	      			diabloMiner.debug(queryUrl.getHost() + ": Disabling roll ntime support");
	      		}
	      	}

	      	String xSwitchTo = connection.getHeaderField("X-Switch-To");

	      	if(xSwitchTo != null && !"".equals(xSwitchTo)) {
	      		String oldHost = queryUrl.getHost();
	      		JsonNode newHost = mapper.readTree(xSwitchTo);

	      		queryUrl = new URL(queryUrl.getProtocol(), newHost.get("host").asText(), newHost.get("port").getIntValue(), queryUrl.getPath());

	      		if(longPollUrl != null)
	      			longPollUrl = new URL(longPollUrl.getProtocol(), newHost.get("host").asText(), newHost.get("port").getIntValue(), longPollUrl.getPath());

	      		diabloMiner.info(oldHost + ": Switched to " + queryUrl.getHost());
	      	}

	      	String xRejectReason = connection.getHeaderField("X-Reject-Reason");

	      	if(xRejectReason != null && !"".equals(xRejectReason)) {
	      		rejectReason = xRejectReason;
	      	}

	      	String xIsP2Pool = connection.getHeaderField("X-Is-P2Pool");

	      	if(xIsP2Pool != null && !"".equals(xIsP2Pool)) {
	      		if(!noDelay)
	      			diabloMiner.info("P2Pool no delay mode enabled");

	      		noDelay = true;
	      	}
                * */

	      	if(connection.getContentEncoding() != null) {
	      		if(connection.getContentEncoding().equalsIgnoreCase("gzip"))
	      			responseStream = new GZIPInputStream(connection.getInputStream());
	      		else if(connection.getContentEncoding().equalsIgnoreCase("deflate"))
	      			responseStream = new InflaterInputStream(connection.getInputStream());
	      	} else {
	      		responseStream = connection.getInputStream();
	      	}

	      	if(responseStream == null)
	      		throw new IOException("Drop to error handler");
                
                String content = convertStreamToString(responseStream).trim();
                responseStream.close();
                
                if (printLogMessages) {
                    //System.out.println("RCPUtils: sending data: " + message.toString());
                    
                    System.out.println("RCPUtils: return data : " + content);
                }                
                
                

	      	Object output = mapper.readTree(/*responseStream*/content);
                
                //System.out.println(output.getClass());
                
                //System.out.println(((ObjectNode) output).get("result").asText());

	      	if(NullNode.class.equals(output.getClass())) {
	      		throw new IOException("Bitcoin returned unparsable JSON");
	      	} else {
	      		try {
	      			responseMessage = (ObjectNode) output;
	      		} catch(ClassCastException e) {
	      			throw new IOException("Bitcoin returned unparsable JSON");
	      		}
	      	}

	      	//responseStream.close();
	      } catch(JsonProcessingException e) {
                  e.printStackTrace();
                  
	      	throw new IOException("Bitcoin returned unparsable JSON");
	      } catch(IOException e) {
	      	InputStream errorStream = null;
	      	IOException e2 = null;

	      	if(connection.getErrorStream() == null)
	      		throw new IOException("Bitcoin disconnected during response: " + connection.getResponseCode() + " " + connection.getResponseMessage());

	      	if(connection.getContentEncoding() != null) {
	      		if(connection.getContentEncoding().equalsIgnoreCase("gzip"))
	      			errorStream = new GZIPInputStream(connection.getErrorStream());
	      		else if(connection.getContentEncoding().equalsIgnoreCase("deflate"))
	      			errorStream = new InflaterInputStream(connection.getErrorStream());
	      	} else {
	      		errorStream = connection.getErrorStream();
	      	}

	      	if(errorStream == null)
	      		throw new IOException("Bitcoin disconnected during response: " + connection.getResponseCode() + " " + connection.getResponseMessage());

	      	byte[] errorbuf = new byte[8192];

	      	if(errorStream.read(errorbuf) < 1)
	      		throw new IOException("Bitcoin returned an error, but with no message");

	      	String error = new String(errorbuf).trim();

	      	if(error.startsWith("{")) {
	      		try {
	      			Object output = mapper.readTree(error);

	      			if(NullNode.class.equals(output.getClass()))
	      				throw new IOException("Bitcoin returned an error message: " + error);
	      			else
	      				try {
	      					responseMessage = (ObjectNode) output;
	      				} catch(ClassCastException f) {
	      					throw new IOException("Bitcoin returned unparsable JSON");
	      				}

	      			if(responseMessage.get("error") != null) {
	      				if(responseMessage.get("error").get("message") != null && responseMessage.get("error").get("message").asText() != null) {
	      					error = responseMessage.get("error").get("message").asText().trim();
	      					e2 = new IOException("Bitcoin returned error message: " + error);
	      				} else if(responseMessage.get("error").asText() != null) {
	      					error = responseMessage.get("error").asText().trim();

	      					if(!"null".equals(error) && !"".equals(error))
	      						e2 = new IOException("Bitcoin returned an error message: " + error);
	      				}
	      			}
	      		} catch(JsonProcessingException f) {
	      			e2 = new IOException("Bitcoin returned unparsable JSON");
	      		}
	      	} else {
	      		e2 = new IOException("Bitcoin returned an error message: " + error);
	      	}

	      	errorStream.close();

	      	if(responseStream != null)
	      		responseStream.close();

	      	if(e2 == null)
	      		e2 = new IOException("Bitcoin returned an error, but with no message");

	      	throw e2;
	      }

	      if(responseMessage.get("error") != null) {
	      	if(responseMessage.get("error").get("message") != null && responseMessage.get("error").get("message").asText() != null) {
	      		String error = responseMessage.get("error").get("message").asText().trim();
	      		throw new IOException("Bitcoin returned error message: " + error);
	      	} else if(responseMessage.get("error").asText() != null) {
	      		String error = responseMessage.get("error").asText().trim();

	      		if(!"null".equals(error) && !"".equals(error))
	      			throw new IOException("Bitcoin returned error message: " + error);
	      	}
	      }

	      JsonNode result;

	      try {
	      	result = responseMessage.get("result");
	      } catch(Exception e) {
	      	throw new IOException("Bitcoin returned unparsable JSON(2)");
	      }

	      if(result == null)
	      	throw new IOException("Bitcoin did not return a result or an error");

	      return result;
      } catch(IOException e) {
      	if(connection != null)
      		connection.disconnect();

	      throw e;
      }
	}

	public WorkState doGetWorkMessage(boolean longPoll, String authHeader) throws IOException {
		ObjectNode getWorkMessage = mapper.createObjectNode();

		getWorkMessage.put("method", "getwork");
		getWorkMessage.putArray("params");
		getWorkMessage.put("id", 1);

		JsonNode responseMessage = doJSONRPCCall(longPoll, getWorkMessage, true, authHeader);

		String datas;
		String midstates;
		String targets;

		try {
                    
                    
                    //JsonNode resultNode = responseMessage.get("result");
                    
                    //System.out.println("RESULT NODE: " + resultNode.toString());
                    
			datas = responseMessage.get("data").asText();
			//midstates = responseMessage.get("midstate").asText();
			targets = responseMessage.get("target").asText();
                        
                        //System.out.println("datas: " + datas + " " + datas.length());
                        //System.out.println("midstates: " + midstates);
                        //System.out.println("targets: " + targets);
                        
		} catch(Exception e) {
                    e.printStackTrace();
			throw new IOException("Bitcoin returned unparsable JSON");
		}

		WorkState workState = new WorkState(this);
                
                workState.parseData(datas);
                workState.setTarget(targets);
                
                //System.out.println("check: " + workState.getAllDataHex());
                
                /*
		String parse;

		for(int i = 0; i < 32; i++) {
			parse = datas.substring(i * 8, (i * 8) + 8);
			workState.setData(i, Integer.reverseBytes((int) Long.parseLong(parse, 16)));
		}

		for(int i = 0; i < 8; i++) {
			parse = midstates.substring(i * 8, (i * 8) + 8);
			workState.setMidstate(i, Integer.reverseBytes((int) Long.parseLong(parse, 16)));
		}

		for(int i = 0; i < 8; i++) {
			parse = targets.substring(i * 8, (i * 8) + 8);
			workState.setTarget(i, (Long.reverseBytes(Long.parseLong(parse, 16) << 16)) >>> 16);
		}
                */

		return workState;
	}
        
        
        public boolean doSendWorkMessage(String work, String authHeader) throws IOException {
		ObjectNode sendWorkMessage = mapper.createObjectNode();
		sendWorkMessage.put("method", "getwork");
		
                ArrayNode params = sendWorkMessage.putArray("params");
		
                params.add(/*dataOutput.toString()*/ work);
		
                sendWorkMessage.put("id", 1);

		JsonNode responseMessage = doJSONRPCCall(false, sendWorkMessage,true,authHeader);

		boolean accepted;

		//dataFormatter.close();

		try {
			accepted = responseMessage.getBooleanValue();
		} catch(Exception e) {
			throw new IOException("Bitcoin returned unparsable JSON");
		}

		return accepted;            
        }

	public boolean doSendWorkMessage(WorkState workState, String authHeader) throws IOException {
            /*
		StringBuilder dataOutput = new StringBuilder(8 * 32 + 1);
		
                Formatter dataFormatter = new Formatter(dataOutput);
		
                int[] data = workState.getData();

		dataFormatter.format(
			"%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x" +
			"%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x",
			Integer.reverseBytes(data[0]), Integer.reverseBytes(data[1]),
			Integer.reverseBytes(data[2]), Integer.reverseBytes(data[3]),
			Integer.reverseBytes(data[4]), Integer.reverseBytes(data[5]),
			Integer.reverseBytes(data[6]), Integer.reverseBytes(data[7]),
			Integer.reverseBytes(data[8]), Integer.reverseBytes(data[9]),
			Integer.reverseBytes(data[10]), Integer.reverseBytes(data[11]),
			Integer.reverseBytes(data[12]), Integer.reverseBytes(data[13]),
			Integer.reverseBytes(data[14]), Integer.reverseBytes(data[15]),
			Integer.reverseBytes(data[16]), Integer.reverseBytes(data[17]),
			Integer.reverseBytes(data[18]), Integer.reverseBytes(data[19]),
			Integer.reverseBytes(data[20]), Integer.reverseBytes(data[21]),
			Integer.reverseBytes(data[22]), Integer.reverseBytes(data[23]),
			Integer.reverseBytes(data[24]), Integer.reverseBytes(data[25]),
			Integer.reverseBytes(data[26]), Integer.reverseBytes(data[27]),
			Integer.reverseBytes(data[28]), Integer.reverseBytes(data[29]),
			Integer.reverseBytes(data[30]), Integer.reverseBytes(data[31]));
*/
		ObjectNode sendWorkMessage = mapper.createObjectNode();
		sendWorkMessage.put("method", "getwork");
		
                ArrayNode params = sendWorkMessage.putArray("params");
		
                params.add(/*dataOutput.toString()*/ workState.getAllDataHexByteSwapped());
		
                sendWorkMessage.put("id", 1);

		JsonNode responseMessage = doJSONRPCCall(false, sendWorkMessage,true, authHeader);

		boolean accepted;

		//dataFormatter.close();

		try {
			accepted = responseMessage.getBooleanValue();
		} catch(Exception e) {
			throw new IOException("Bitcoin returned unparsable JSON");
		}

		return accepted;
	}

    private void debug(String string) {
        System.out.println("DEBUG: " + string);
    }
        
        
      /*  
	class LongPollAsync implements Runnable {
		public void run() {
			while(diabloMiner.getRunning()) {
				try {
					WorkState workState = doGetWorkMessage(true);
					incomingQueue.add(workState);
					refreshTimestamp.set(workState.getTimestamp());

					diabloMiner.debug(queryUrl.getHost() + ": Long poll returned");
				} catch(IOException e) {
					diabloMiner.error("Cannot connect to " + queryUrl.getHost() + ": " + e.getLocalizedMessage());
				}

				try {
					if(!noDelay)
						Thread.sleep(250);
				} catch(InterruptedException e) { }
			}
		}
	}        
    */
    
    
    public static String tohex(byte [] arg) {
        
        String res = "";
        String a;        
        
        for (int i = 0; i < arg.length; i++) {
            a = Integer.toHexString(((int) arg[i]) & 0xFF );
            
            if (a.length() == 1)
                a = "0" + a;
            
            res += a;
        }        
        
        return res;
        
    }
    
    
    
    
}

