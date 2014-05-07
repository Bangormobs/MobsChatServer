package uk.co.mobsoc.chat.server;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;

import org.json.JSONObject;

import uk.co.mobsoc.chat.common.Colour;
import uk.co.mobsoc.chat.common.Connector;
import uk.co.mobsoc.chat.common.packet.ActionPacket;
import uk.co.mobsoc.chat.common.packet.Packet;
import uk.co.mobsoc.chat.common.packet.PingPacket;
import uk.co.mobsoc.chat.server.irc.IRCConnector;
import uk.co.mobsoc.chat.server.json.JSONConnector;


/**
 * Main entry point
 * @author triggerhapp
 *
 */
public class Main {
	public static boolean stopped = false;
	public static Vector<Connector> clients = new Vector<Connector>();
	public static Vector<PingPacket> pingList = new Vector<PingPacket>();
	static Properties props = new Properties();
	
	public static void main(String[] args){
		Connector c2 = new Connector();
		System.out.println(c2);
		Packet.init();
		Colour.dC = new Colour("\u000f",  0,  0,  0);
		if(!loadConfigs()){
			makeConfigs();
			System.exit(1);
		}
		
		// First we need to populate Connection Types. At some point I want a plugin system to allow others to contribute easier
		// These are acceptable types of connectors in the server JSON config
		HashMap<String, Class<? extends DataConnector>> classes = new HashMap<String, Class<? extends DataConnector>>();
		classes.put("ircconnector", IRCConnector.class);
		classes.put("jsonconnector", JSONConnector.class);
		
		JSONObject mainconf = getServersConfig();
		JSONObject conf = mainconf.getJSONObject("connections");
		for(String s : JSONObject.getNames(conf)){
			final JSONObject node = conf.getJSONObject(s);
			String type = node.getString("Type");
			if(!classes.containsKey(type.toLowerCase())){
				System.out.println("Unknown type : '"+type+"' - ignoring");
				continue;
			}
			Class<? extends DataConnector> klass = classes.get(type.toLowerCase());
			DataConnector dc = null;
			try {
				dc = klass.newInstance();
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
			if(dc!=null){
				dc.name = s;
				final DataConnector fdc = dc;
				Runnable r = new Runnable(){

					@Override
					public void run() {
						fdc.setConfig(node);
					}
					
				};
				Thread t = new Thread(r);
				t.start();
				ServerCallbacks scb = new ServerCallbacks(dc);
				dc.addCallback(scb);
	            synchronized(Main.clients){
	            	clients.add(dc);
	            }
				System.out.println("Started plugin : '"+s+"' of type '"+type+"'");
			}else{
				System.out.println("Error making instance of '"+type+"' - does it have a no-arguement constructor?");
			}
		}
		JSONObject mysql = mainconf.getJSONObject("mysql");
		Server.server = mysql.getString("host");
		Server.dataBase = mysql.getString("db");
		Server.userName = mysql.getString("username");
		Server.passWord = mysql.getString("password");
		

		for(Object key : props.keySet()){
			SUtils.setMessageFormat((String) key, props.getProperty((String) key));
		}
		Thread t = new Thread(new Server());
		t.start();
		long lastMySQL = System.currentTimeMillis();
		while(!stopped){
			try{
				Thread.sleep(10000);
				// 10 Second tick is acceptable?
			}catch (InterruptedException e){
				
			}
			if(Thread.interrupted()){
				// We don't care. Continue.
			}
			// TODO tick and/or tock
			long timeNow = System.currentTimeMillis();

			if(timeNow > lastMySQL + (1000*60*30)){
				lastMySQL = timeNow;
				Server.mysqlTick();
				System.out.println("Keeping MySQL alive");
			}
			PingPacket ping = new PingPacket((int)(Math.random()*Integer.MAX_VALUE));
			ping.timestamp = timeNow;
			synchronized(clients){
				// For concurrency reasons, copy out the contents
				ArrayList<Connector> newList = new ArrayList<Connector>();
				for(Connector c : clients){
					newList.add(c);
				}
				for(Connector c : newList){
					DataConnector dConn = (DataConnector) c;
					dConn.mainThreadTick();
					// If dead, close socket cleanly
					if(dConn.destroy){
						dConn.debug("Closed");
						if(!dConn.isHost){
							// Direct users need a logout message
							if(!dConn.hideLogout){
								if(!dConn.name.equals("")){
									ActionPacket ap = new ActionPacket();
									ap.action = SUtils.getMessageFormat("logout", dConn.name, "chat");
									ap.sourceColour = dConn.connectionColour;
									Server.sendAll(dConn, ap);
								}
							}
							Server.updateAllServerLists();
						}else{
							ActionPacket ap = new ActionPacket();
							ap.action = SUtils.getMessageFormat("serverquit", dConn.name, "chat");
							ap.sourceColour = dConn.connectionColour;
							Server.sendAll(dConn, ap);
							Server.updateAllServerLists();
						}
						clients.remove(c);
						dConn.destroy();
						continue;
					}
					// Else ping
					dConn.send(ping);
				}
			}
			synchronized(pingList){
				pingList.add(ping);
				// For concurrency reasons, copy out the contents
				ArrayList<PingPacket> newList = new ArrayList<PingPacket>();
				for(PingPacket pp : pingList){
					newList.add(pp);
				}
				for(PingPacket pp : newList){
					// Pings older than 1 min purged. You have ping that bad, you'll know without me saying.
					if(pp.timestamp+60000 < timeNow){
						pingList.remove(pp);
					}
				}
			}
		}
	}

	public static boolean isStopped() {
		return stopped;
	}
	
	/**
	 * Confusingly named because it loads the language files. Of which currently only English is supported
	 * @return
	 */
	public static boolean loadConfigs(){

			//props.load(ClassLoader.getSystemResourceAsStream("en.properties"));
		try {
			props.load(new FileInputStream("en.properties"));
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
		return true;
	}
	
	private static void makeConfigs() {
		copyOutFile("en.properties");
		copyOutFile("servers.json");
	}
	
	/**
	 * Copys a file from inside this jar-file to the working directory
	 * @param string
	 */
	private static void copyOutFile(String string) {
		File f = new File(string);
		if(f.exists()){
			System.out.println("Confg File '"+string+"' already exists : not overwriting");
			return;
		}
		InputStream stream = Main.class.getResourceAsStream("/"+string);
	    if (stream == null) {
	        System.out.println("Setup file '"+string+"' not found. Aborting");
	        System.exit(1);
	    }
	    OutputStream resStreamOut=null;
	    int readBytes;
	    byte[] buffer = new byte[4096];
	    try {
	        resStreamOut = new FileOutputStream(f);
	        while ((readBytes = stream.read(buffer)) > 0) {
	            resStreamOut.write(buffer, 0, readBytes);
	        }
	    } catch (IOException e1) {
	        e1.printStackTrace();
	    } finally {
	    	if(resStreamOut!=null){
	    		try {
					resStreamOut.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	    	}
	    }
	    try {
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	    
	}

	/**
	 * Return the JSON data from the config file - loads fresh copy	
	 * @return
	 */
	public static JSONObject getServersConfig(){
		FileReader fread=null;
		BufferedReader reader=null;
		JSONObject serverConfig=null;
		try {
			fread = new FileReader("config.json");
			reader = new BufferedReader(fread);
			String line = null;
			StringBuilder stringBuilder = new StringBuilder();
			while ( ( line = reader.readLine() ) !=null ){
				stringBuilder.append(line);
				stringBuilder.append("\n");
			}
			String s = stringBuilder.toString();
			serverConfig = new JSONObject(s);
			
		} catch (IOException e) {
			System.out.println("Config appears to not be present, creating");
			return null;
		} finally {
			try {
				if(fread!=null){
					fread.close();
				}
			} catch 	(IOException e) {
			// 	TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(reader!=null){
				try {
					reader.close();
				} catch (IOException e) {
					// 		TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		
		}
		return serverConfig;
		
	}

}
