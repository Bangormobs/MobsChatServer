package uk.co.mobsoc.chat.server.json;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import uk.co.mobsoc.chat.common.Colour;
import uk.co.mobsoc.chat.common.Connector;
import uk.co.mobsoc.chat.common.SocketCallbacks;
import uk.co.mobsoc.chat.common.packet.ActionPacket;
import uk.co.mobsoc.chat.common.packet.ChatPacket;
import uk.co.mobsoc.chat.common.packet.Packet;
import uk.co.mobsoc.chat.common.packet.PrivateMessagePacket;
import uk.co.mobsoc.chat.common.packet.ServerData;
import uk.co.mobsoc.chat.common.packet.UserListUpdatePacket;
import uk.co.mobsoc.chat.server.DataConnector;
import uk.co.mobsoc.chat.server.UserData;
import uk.co.mobsoc.chat.server.Main;
import uk.co.mobsoc.chat.server.MessageData;
import uk.co.mobsoc.chat.server.PrivateMessageData;
import uk.co.mobsoc.chat.server.Server;
import uk.co.mobsoc.chat.server.irc.IRCConnector;

/**
 * A Pseudo-Client. Runs internally to the server and passes message back and forth to Clients connecting via HTTP (HTTPS is advised but not supported - use Apache forwarding!) 
 * @author triggerhapp
 *
 */
public class JSONConnector extends DataConnector{
	public ArrayList<ServerData> serverList;
	public Object lock = new Object(); // since we replace the reference in serverList it doesn't make a good lock

	public JSONConnector(){
		super();
	}

	JSONServer server = null;
	
	@Override
	public void setConfig(JSONObject config){
		super.setConfig(config);
		int port = 4241;
		if(config.has("port")){
			port = config.getInt("port");
		}
		server = new JSONServer(this, port);
		this.isHost = true;
		this.usable = true;
		this.name = "Mobile Chat";
		this.isConstantConnection = true;
		this.isCorrectVersion = true;
		this.hostIsMCNames = false;
	}

	@Override
	public void send(Packet packet){
		if(packet instanceof UserListUpdatePacket){
			UserListUpdatePacket ulup = (UserListUpdatePacket) packet;
			synchronized(lock){
				serverList = ulup.servers;
			}
		}
	}
	
	/**
	 * This needs to be 100% Thread safe. Fun times ahead :)
	 * 
	 * @param path
	 * @return
	 */
	public String getJSONFor(String path) {
		HashMap<String, String> args = new HashMap<String, String>();
		if(path.equals("") || path.toLowerCase().startsWith("index.htm")){
			JSONStringer js = new JSONStringer();
			String[] split = path.split("\\?");
			if(split.length>=2){
				split = split[1].split("&");
				for(String section : split){
					String[] splitAgain = section.split("=");
					if(splitAgain.length != 2){
						System.out.println("Unknown JSON arguement '"+section+"'");
					}else{
						try {
							args.put(URLDecoder.decode(splitAgain[0],"UTF-8"), URLDecoder.decode(splitAgain[1],"UTF-8"));
						} catch (UnsupportedEncodingException e) {
							e.printStackTrace();
						}
					}
				}
				if(!args.containsKey("id") || !args.containsKey("pass")){
				// 	No Identification code.
					js.object().key("Error").value("Did not attempt to identify").endObject();
				}else{
				// 	Check ID
					UserData viewingUser = Server.getUserData(Integer.parseInt(args.get("id")));
					if(viewingUser!=null && viewingUser.chatPass!=null && viewingUser.chatPass.equals(args.get("pass"))){
						// Logged in successfully.
						js.object();
						js.key("iam").value(viewingUser.getName(""));
						// Even if not asked for, push any new Private messages.
						ArrayList<PrivateMessageData> pmd = Server.getUnseenPMs(viewingUser.key);
						ArrayList<Integer> names = new ArrayList<Integer>();
						if(pmd.size()>0){
							js.key("newPM").object();
							for(PrivateMessageData data : pmd){
								if(!names.contains(data.from)){
									names.add(data.from);
									UserData user = Server.getUserData(data.from);
									js.key(user.getName("")).value(true);
								}
								Server.setPMSeen(data);
							}
							js.endObject();
						}
						if(args.containsKey("who")){
							js.key("who").object();
							synchronized(lock){
								for(ServerData sd : serverList){
									js.key(sd.connectionName).object();
									//String s = sd.connectionColour.toInternal()+sd.connectionName+" : ", sep = "";
									js.key("col").value(sd.connectionColour.toInternal());
									js.key("users").array();
									for(String user : sd.userList){
										js.value(user);
									}
									js.endArray();
									js.endObject();
								}
							}
							js.endObject();

						}
						if(args.containsKey("msg") && args.containsKey("msgtmpid")){
							js.key("msgtmpid").value(args.get("msgtmpid"));
							ChatPacket cp = new ChatPacket();

							cp.name = Server.getFullName(viewingUser,viewingUser.getName(""));
							cp.message = args.get("msg");
							cp.sourceColour = this.connectionColour;
							returnPacket(cp);
							
						}
						if(args.containsKey("pmsg") && args.containsKey("pmsgtmpid") && args.containsKey("pmsgto")){
							if(args.get("pmsgto").length()<=25){
								js.key("msgtmpid").value(args.get("msgtmpid"));
								PrivateMessagePacket pmp = new PrivateMessagePacket();
								pmp.fromI = viewingUser.key;
								pmp.toI = Integer.parseInt(args.get("pmsgto"));
								pmp.message = args.get("pmsg");
								returnPacket(pmp);
							}
							
						}
						ArrayList<String> updateLatest = new ArrayList<String>();
						if(args.containsKey("updateLatest")){
							for(String name: args.get("updateLatest").split(",")){
								updateLatest.add(name);
							}
						}
						if(args.containsKey("getChunk")){
							String chunkS = args.get("getChunk");
							//long chunk = -1;
							try{
								js.key("chunkData").object();//.key("chunkLoc").value(chunk);
								for(String pair : chunkS.split("\\|")){
									String key = pair.split(",")[0], val = pair.split(",")[1];
									if(!updateLatest.contains(key)){ updateLatest.add(key); }
									long l = Long.parseLong(val);
									if(key.equalsIgnoreCase("main")){
										// Special Case for non-pm

										js.key("c-main-"+l).object();
										for(MessageData md : Server.getMessageChunk(l)){
											js.key("m_"+md.id).object();
											js.key("timeStamp").value(md.timeSent.getTime());
											js.key("type").value(md.type);
											js.key("sender").value(md.sender);
											js.key("message").value(md.message);
											js.key("colour").value(md.colour);
											js.endObject();
										}
										js.endObject();
									}else{
										js.key("c-"+key.toLowerCase()+"-"+l).object();
										ArrayList<PrivateMessageData> list = Server.getPMChunk(UserData.getUserFromName(key), viewingUser.key, l);
										for(PrivateMessageData md : list){
											js.key("m_"+md.id).object();
											js.key("timeStamp").value(md.timeSent.getTime());
											UserData from = Server.getUserData(md.from);
											js.key("from").value(from.getName(""));
											UserData to = Server.getUserData(md.to);
											js.key("to").value(to.getName(""));
											js.key("message").value(md.message);
											js.key("type").value("P");
											js.key("seen").value(md.seen? 1 : 0);
											//js.key("colour").value(md.colour);
											if(md.to==viewingUser.key){
												Server.setPMSeen(md);
											}
											js.endObject();
										}
										js.endObject();

									}
								}
								js.endObject();
							}catch (Exception e){
								e.printStackTrace();
								// Silently ignore badly written requests
							}

						}
						js.key("latestChunk").object();
						for(String name : updateLatest){
							if(name.equalsIgnoreCase("main")){
								// Special, again
								long l = Server.getLatestChunk();
								js.key("main").value(l);
							}else{
								long l = Server.getLatestPMChunk(Server.getIDForMCName(name), viewingUser.key);
								js.key(name).value(l);
							}
						}
						js.endObject();

						js.endObject();
					}else{
						js.object().key("Error").value("Invalid Username or Password").endObject();
					}
				}
			}
			return js.toString();
		}
		return null;
	}
	
	public void returnPacket(Packet p){
        boolean found=false;
        for(SocketCallbacks scb : callbacks){
            if(scb.packetRecieved(this, p)){ found=true; break; }
        }
        if(!found){
            Logger.getLogger(Connector.class.getName()).log(Level.SEVERE, "Packet "+p.getClass()+" dropped!");
        }
	}
	
    public ArrayList<SocketCallbacks> callbacks = new ArrayList<SocketCallbacks>();
	
	@Override
    public void addCallback(SocketCallbacks callback) {
		callbacks.add(callback);
	}
	
	@Override
	public void removeCallback(SocketCallbacks callback){
		callbacks.remove(callback);
	}

	public String getJSONForPost(String value) {
		JSONObject jsin=null;
		System.out.println(value);
		try{
			jsin = new JSONObject(value);
		}catch(JSONException ex){
			return null;
		}
		JSONStringer js = new JSONStringer();
		if(!jsin.has("id") || !jsin.has("pass")){
		    // No Identification code.
			js.object().key("Error").value("Did not attempt to identify").endObject();
		}else{
			// 	Check ID
			UserData viewingUser = Server.getUserData(jsin.getInt("id"));
			if(viewingUser!=null && viewingUser.chatPass!=null && viewingUser.chatPass.equals(jsin.getString("pass"))){
				// Logged in successfully.
				js.object();
				js.key("iam").value(viewingUser.getName(""));
				// Even if not asked for, push any new Private messages.
				ArrayList<PrivateMessageData> pmd = Server.getUnseenPMs(viewingUser.key);
				ArrayList<Integer> names = new ArrayList<Integer>();
				if(pmd.size()>0){
					js.key("newPM").object();
					for(PrivateMessageData data : pmd){
						if(!names.contains(data.from)){
							names.add(data.from);
							UserData user = Server.getUserData(data.from);
							js.key(user.getName("")).value(true);
						}
						Server.setPMSeen(data);
					}
					js.endObject();
				}
				if(jsin.has("who")){
					js.key("who").object();
					synchronized(lock){
						//for(ServerData sd : serverList){
						//	js.key(sd.connectionName).object();
						//	//String s = sd.connectionColour.toInternal()+sd.connectionName+" : ", sep = "";
						//	js.key("col").value(sd.connectionColour.toInternal());
						//	js.key("users").array();
						//	for(String user : sd.userList){
						//		js.value(user);
						//	}
						//	js.endArray();
						//	js.endObject();
						//}
					}
					js.endObject();
					}
				if(jsin.has("msg")){
					ChatPacket cp = new ChatPacket();
					cp.name = Server.getFullName(viewingUser,viewingUser.getName(""));
					cp.message = jsin.getString("msg");
					cp.sourceColour = this.connectionColour;
					returnPacket(cp);
							
				}
				if(jsin.has("pmsg") && jsin.has("pmsgto")){
					if(jsin.getString("pmsgto").length()<=25){
						PrivateMessagePacket pmp = new PrivateMessagePacket();
						pmp.fromI = viewingUser.key;
						pmp.toI = jsin.getInt("pmsgto");
						pmp.message = jsin.getString("pmsg");
						returnPacket(pmp);
					}
				}
				ArrayList<String> updateLatest = new ArrayList<String>();
				if(jsin.has("updateLatest")){
					JSONArray latest = jsin.getJSONArray("updateLatest");
					for(int index=0; index < latest.length(); index++){
						// Am I blind or does this Piece of Shit code really not have any kind of iterator for Array?
						updateLatest.add(latest.getString(index));
					}
					//for(String name: jsin.getJSONArray("updateLatest")){
				//		updateLatest.add(name);
					//}
				}
				if(jsin.has("getChunk")){
					JSONArray chunks = jsin.getJSONArray("getChunk");
					// Why was this not correctly typed to String? I must be doing something wrong?
					js.key("chunkData").object();
					for(int index = 0; index<chunks.length(); index++){
						JSONObject obj = chunks.getJSONObject(index);
						long l  = obj.getLong("l");
						String key = obj.getString("key");
						if(!key.matches("-?\\d+")){
							// Special Case for non-pm
							js.key("c-main-"+l).object();
							for(MessageData md : Server.getMessageChunk(l)){
								js.key("m_"+md.id).object();
								js.key("timeStamp").value(md.timeSent.getTime());
								js.key("type").value(md.type);
								js.key("sender").value(md.sender);
								js.key("message").value(md.message);
								js.key("colour").value(md.colour);
								js.endObject();
							}
							js.endObject();
						}else{
							int thatUser = Integer.parseInt(key);
							js.key("c-"+thatUser+"-"+l).object();
							ArrayList<PrivateMessageData> list = Server.getPMChunk(thatUser, viewingUser.key, l);
							for(PrivateMessageData md : list){
								js.key("m_"+md.id).object();
								js.key("timeStamp").value(md.timeSent.getTime());
								UserData from = Server.getUserData(md.from);
								js.key("from").value(from.getName(""));
								UserData to = Server.getUserData(md.to);
								js.key("to").value(to.getName(""));
								js.key("message").value(md.message);
								js.key("type").value("P");
								js.key("seen").value(md.seen? 1 : 0);
								//js.key("colour").value(md.colour);
								if(md.to==viewingUser.key){
									Server.setPMSeen(md);
								}
								js.endObject();
							}
							js.endObject();
						}
					}
					js.endObject();
				}
				js.key("latestChunk").object();
				for(String name : updateLatest){
					if(name.equalsIgnoreCase("main")){
						// Special, again
						long l = Server.getLatestChunk();
						js.key("main").value(l);
					}else{
						long l = Server.getLatestPMChunk(Server.getIDForMCName(name), viewingUser.key);
						js.key(name).value(l);
					}
				}
				js.endObject();

				js.endObject();
			}else{
				js.object().key("Error").value("Invalid Username or Password").endObject();
			}
		}
		return js.toString();
	}
}
