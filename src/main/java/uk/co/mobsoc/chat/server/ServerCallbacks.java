package uk.co.mobsoc.chat.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.json.JSONObject;

import uk.co.mobsoc.chat.common.Colour;
import uk.co.mobsoc.chat.common.Connector;
import uk.co.mobsoc.chat.common.SocketCallbacks;
import uk.co.mobsoc.chat.common.Util;
import uk.co.mobsoc.chat.common.packet.AccountAddPacket;
import uk.co.mobsoc.chat.common.packet.ActionPacket;
import uk.co.mobsoc.chat.common.packet.ChatPacket;
import uk.co.mobsoc.chat.common.packet.ConnectionPacket;
import uk.co.mobsoc.chat.common.packet.Packet;
import uk.co.mobsoc.chat.common.packet.PingPacket;
import uk.co.mobsoc.chat.common.packet.PlayerJoinedPacket;
import uk.co.mobsoc.chat.common.packet.PresetActionPacket;
import uk.co.mobsoc.chat.common.packet.PrivateMessagePacket;
import uk.co.mobsoc.chat.common.packet.HostUserListUpdatePacket;
import uk.co.mobsoc.chat.common.packet.ServerInfoPacket;
import uk.co.mobsoc.chat.common.packet.SetRankPacket;
import uk.co.mobsoc.chat.common.packet.UpdatePacket;
import uk.co.mobsoc.chat.common.packet.VersionPacket;
/**
 * Callback class - each packet sent to Server passes through here
 * @author triggerhapp
 *
 */
public class ServerCallbacks implements SocketCallbacks {

	public ServerCallbacks(Connector conn) {
	}

	@Override
	public boolean packetRecieved(Connector conn, Packet packet) {
		DataConnector dConn = (DataConnector) conn;
		if(dConn.destroy){ return false; }
		dConn.debug(packet.toString()+" - Recieved");
		if(!dConn.isCorrectVersion){
			if(!(packet instanceof VersionPacket)){
				dConn.debug("No Version given");
				dConn.destroy=true;
				return false;
			}
		}
		if(packet instanceof VersionPacket){
			VersionPacket vp = (VersionPacket) packet;
			if(vp.Mv!=Util.MajorVersion || vp.mv !=Util.MinorVersion){
				dConn.debug("Wrong Version given");
				dConn.destroy=true;
			}else{
				dConn.isCorrectVersion=true;
			}
			return true;
		}else if(packet instanceof PingPacket){
			PingPacket pp = (PingPacket) packet;
			synchronized(Main.pingList){
				for(PingPacket pp2 : Main.pingList){
					if(pp2.getValue() == pp.getValue()){
						long timeDiff = System.currentTimeMillis() - pp2.timestamp;
						dConn.latency = timeDiff;
						return true;
					}
				}
			}
			dConn.latency=100000;
			return true;
		}else if(packet instanceof ConnectionPacket){
			ConnectionPacket cp = (ConnectionPacket) packet;
			dConn.isHost = cp.isHost;
			if(cp.isHost){
				dConn.usable = true;
				ServerIdent si = Server.getServerIdent(cp.connectionName);
				System.out.println(si.id+" "+cp.connectionName);
				if(si.id!=null){
					System.out.println(si.pass+" "+cp.password);
					if(si.pass.equals(cp.password) && cp.password.length()>5){
						dConn.name = si.name;
						dConn.connectionColour = new Colour("a"+si.colour);
						ActionPacket ap = new ActionPacket();
						ap.action = SUtils.getMessageFormat("serverjoin", dConn.name);
						dConn.isConstantConnection = true;
						ap.sourceColour = dConn.connectionColour;
						Server.sendAll(dConn, ap);
						Server.updateAllServerLists();
						for(UUID key : UserData.lastRanks.keySet()){
							SetRankPacket srp = new SetRankPacket();
							srp.uuid = key;
							srp.rank = UserData.lastRanks.get(key);
							dConn.send(srp);
						}
						return true;
					}	
				}
				// Well... It's not a server we have in config!
				ChatPacket Cpacket = new ChatPacket();
				Cpacket.message="Failed to identify as server '"+cp.connectionName+"'";
				Cpacket.name="SERVER";
				Cpacket.sourceColour = Colour.none;
				dConn.send(Cpacket);

				dConn.usable=false;
				dConn.destroy=true;
				return true;
			}else{
				// Direct-Connecting user
				
				// Confirm username + pass
				UserData user = Server.getUserData(cp.getUserId());
				if(user==null || user.chatPass==null){ dConn.debug("No User data for "+cp.connectionName); dConn.destroy=true; return true; }
				if(!user.chatPass.equals(cp.password) || cp.password.length()<5){
					ChatPacket cp2 = new ChatPacket();
					cp2.name = "SERVER";
					cp2.message = "Could not identify you, if you are using the launcher, please download a new one. If you are using the applet, please refresh";
					dConn.send(cp2);
					dConn.destroy=true;
					dConn.debug("Failed to prove identity '"+cp.connectionName+"'");
					return true;
				}
				dConn.name = user.getName("");
				//dConn.mcName = user.mcName;
				dConn.debug("ID is "+dConn.name);
				dConn.userList.add(dConn.name);
				dConn.usable = true;
				ActionPacket ap = new ActionPacket();
				ap.action = SUtils.getMessageFormat("login", dConn.name, "chat");
				cp.connectionName = dConn.name;
				dConn.isConstantConnection = true;
				dConn.send(cp);
				ap.sourceColour = Colour.none;
				Server.sendAll(dConn, ap);
			}
			Server.updateAllServerLists();
			return true;
		}
		if(dConn.usable){
			if(packet instanceof ActionPacket){
				ActionPacket ap = (ActionPacket) packet;
				System.out.println(ap);
				ap.sourceColour = dConn.connectionColour;
				ap.action = SUtils.simplifyColours(ap.action);
				Server.sendAll(dConn, ap);
				return true;
			}else if(packet instanceof ChatPacket){
				ChatPacket ap = (ChatPacket) packet;
				System.out.println(ap);
				ap.sourceColour = dConn.connectionColour;
				if(ap.message.length() < 1){ return true; }
				ap.message = SUtils.simplifyColours(ap.message);
				if(!dConn.isHost){
				    ap.name = dConn.name;
					if(dConn.mcName!=null){
						ap.name = Server.getFullName(dConn.mcName);
					}
				}else{
					if(dConn.hostIsMCNames){
						ap.name = Server.getFullName(ap.name);
					}
				}
				ap.message = SUtils.simplifyColours(ap.message);
				ap.name = SUtils.simplifyColours(ap.name);
				Server.sendAll(dConn, ap);
				return true;
			}else if(packet instanceof PresetActionPacket){
				PresetActionPacket pap = (PresetActionPacket) packet;
				ActionPacket ap = new ActionPacket();
				if(pap.action.equals("login") || pap.action.equals("logout")){
					Server.getUserData(Server.getIDForMCName(pap.s1));
					pap.s2=dConn.name;
				}
				ap.action = SUtils.getMessageFormat(pap.action, pap.s1, pap.s2);

				ap.sourceColour = dConn.connectionColour;
				if(!dConn.isHost){
					// Set a colour for direct connections
					ap.sourceColour = Colour.none;
				}
				System.out.println("*"+SUtils.strip(ap.action));
				//Server.addMessage(ap);
				Server.sendAll(dConn, ap);
				return true;
			}else if(packet instanceof PrivateMessagePacket){
				PrivateMessagePacket pmp = (PrivateMessagePacket) packet;
				if(pmp.fromI == null || pmp.fromI == -1){
					// If user int is not set, but UUID is...
					pmp.fromI = Server.getUserDataMC(pmp.from).key;
					
				}
				if(pmp.toI == null || pmp.toI == -1){
					// If user int is not set...
					if(pmp.toUUID ==null){
						// Lets hope they at least typed in the username!
						pmp.toI = UserData.getUserFromName(pmp.toString);
					}else{
						// UUID is set - use that
						pmp.toI = Server.getUserDataMC(pmp.toUUID).key;
					}
					
				}
				boolean fail=false;
				if(pmp.fromI==-1){
					System.out.println("Unknown username '"+pmp.from+"'");
					fail=true;
				}
				if(pmp.toI==-1){
					System.out.println("Unknown username '"+pmp.toString+"'");
					fail=true;
				}

				if(!fail){
					UserData from = Server.getUserData(pmp.fromI);
					UserData to = Server.getUserData(pmp.toI);
					if(pmp.fromChosenName==null){
						pmp.fromChosenName = from.faveName;
					}
					if(pmp.toString==null){
						pmp.toString = to.faveName;
					}
					try{
						Server.sendAll(dConn, pmp,true);
					}catch (Exception e){
						e.printStackTrace();
					}
				}
				return true;
			}else if(packet instanceof HostUserListUpdatePacket){
				if(!dConn.isHost){
					// User is attempting to updated list? possible client hax.
					dConn.destroy=true;
					dConn.usable=false;
					return true;
				}
				HostUserListUpdatePacket hulup = (HostUserListUpdatePacket) packet;
				dConn.userList.clear();
				for(String s : hulup.userList){
					dConn.userList.add(s);
				}
				Server.updateAllServerLists();
				return true;
			}else if(packet instanceof UpdatePacket){
				UpdatePacket up = (UpdatePacket) packet;
				dConn.debug("Catching up from "+up.t.toString());
				for(Packet p : Server.getAllMessagesSince(up.t)){
				//	System.out.println(p);
				//	dConn.send(p);
				}
				up.t = Server.getTimeNow();
				dConn.send(up);
				return true;
			}else if(packet instanceof ServerInfoPacket){
				ServerInfoPacket sip = (ServerInfoPacket) packet;
				if(dConn.isHost){
					sip.name = dConn.name;
					Server.updateServerInfo(sip);
				}
				return true;
			}else if(packet instanceof AccountAddPacket){
				AccountAddPacket aap = (AccountAddPacket) packet;
				if(dConn.isHost){
					UserData user = Server.getUserData(Integer.parseInt(aap.id));
					if(user.chatPass==null || user.chatPass.length() < 5){
						// No good
					}else{
						if(user.chatPass.equals(aap.code)){
							Server.addUserMCName(user.key, aap.mcName, aap.uuid);
						}
					}
				}
				return true;
			}else if(packet instanceof PlayerJoinedPacket){
				PlayerJoinedPacket pjp = (PlayerJoinedPacket) packet;
				if(dConn.isHost){
					MCData mcd = Server.getMCData(pjp.uuid);
					if(mcd.mcName!=null && !mcd.mcName.equals(pjp.name)){
						ActionPacket ap = new ActionPacket();
						ap.action = SUtils.getMessageFormat("mcnamechange", mcd.mcName, pjp.name);
						ap.sourceColour = dConn.connectionColour;
						Server.sendAll(dConn, ap);
					}
				}
			}
		}
		return false;
	}

	@Override
	public void connectionLost(Connector conn) {
		DataConnector dConn = (DataConnector) conn;
		dConn.debug("Connection closed remotely");
		dConn.usable=false;
		dConn.destroy=true;
	}

}
