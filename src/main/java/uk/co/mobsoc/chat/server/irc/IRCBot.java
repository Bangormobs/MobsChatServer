package uk.co.mobsoc.chat.server.irc;

import java.io.IOException;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;

import uk.co.mobsoc.chat.common.packet.ActionPacket;
import uk.co.mobsoc.chat.common.packet.ChatPacket;
import uk.co.mobsoc.chat.common.packet.HostUserListUpdatePacket;
import uk.co.mobsoc.chat.common.packet.PresetActionPacket;
import uk.co.mobsoc.chat.common.packet.ServerData;

/**
 * An instance of PIRC bot used to connect to a server and pass messages back and forth between the core chat.
 * @author triggerhapp
 *
 */
public class IRCBot extends PircBot {
	boolean working = false;
	IRCConnector back;


	public IRCBot(IRCConnector back){
		this.back = back;
		this.setMessageDelay(300);
		doReconnect();
		working = true;
	}
	
	public void onMessage(String channel, String sender, String login, String hostname, String message){
		if(isCommand(channel, sender, message)){ return ; }
		if(channel.equalsIgnoreCase(back.Channel)){
			ChatPacket cp = new ChatPacket();
			cp.name = IRCConnector.IRCMarkupToInternal(sender);
			cp.message = IRCConnector.IRCMarkupToInternal(message);
			back.returnPacket(cp);
		}
	}
	
	public void onAction(String sender, String login, String hostname, String target, String message){
		if(target.equalsIgnoreCase(back.Channel)){
			ActionPacket ap = new ActionPacket();
			ap.action = IRCConnector.IRCMarkupToInternal(sender+ " " + message);
			back.returnPacket(ap);
		}
	}
	
	public void onPrivateMessage(String sender, String login, String hostname, String message){
		if(isCommand(sender, sender, message)){ return ; }
	}
	
	private boolean isCommand(String channel, String sender, String message) {
		boolean isCommand = false;
		if(message.startsWith("!")){
			isCommand = true;
			message = message.substring(1,message.length());
		}
		if(!channel.startsWith("#")){
			isCommand = true;
		}
		if(isCommand){
			if(message.equalsIgnoreCase("list")){
				for(ServerData sd : back.serverList){
					String s = sd.connectionColour.toInternal()+sd.connectionName+" : ", sep = "";
					if(sd.userList.size()==0){
						s = s + "(No Users)";
					}else{
						for(String user : sd.userList){
							s = s + sep + user;
							sep = ", ";
						}
					}
					sendNotice(sender, IRCConnector.InternalMarkupToIRC(s,back.stripFormat));
				}
			}
			return true;
		}
			
		
		return false;
	}

	public void onNotice(String sourceNick, String sourceLogin, String sourceHostname, String target, String notice){
		if(sourceNick!=null && sourceNick.equalsIgnoreCase("nickserv")){
			if(notice!=null){
				String errorMessage=null;
				if(notice.toLowerCase().startsWith("Your nick isn't registered")){
					errorMessage="IRC nickname '"+this.getNick()+"' is not registered.";
				}else if(notice.toLowerCase().startsWith("password inco")){
					errorMessage="IRC nickname '"+this.getNick()+"' is registered, and an incorrect password is used.";
				}else if(notice.toLowerCase().startsWith("this nickname is registered")){
					this.identify(back.Password);
					return;
				}
				if(errorMessage!=null){
					System.out.println(errorMessage);
					return;
				}
			}
		}
	}
	
	public void onJoin(String channel, String sender, String login, String hostname){
		if(channel.equalsIgnoreCase(back.Channel)){
			this.voice(channel, sender);
			if(!sender.equalsIgnoreCase(this.getNick())){
				PresetActionPacket pap = new PresetActionPacket("login", sender);
				back.returnPacket(pap);
			}
			HostUserListUpdatePacket hulup = new HostUserListUpdatePacket();
			for(User s : getUsers(channel)){
				if(s.getNick().equalsIgnoreCase(getNick())){ continue; }
				hulup.userList.add(s.getNick());
			}
			back.returnPacket(hulup);
		}
		
	}
	
	@Override
	protected void onUserList(String channel, User[] users){
		HostUserListUpdatePacket hulup = new HostUserListUpdatePacket();
		for(User s : users){
			if(s.getNick().equalsIgnoreCase(getNick())){ continue; }
			hulup.userList.add(s.getNick());
		}
		back.returnPacket(hulup);		
	}
	
	public void onPart(String channel, String sender, String login, String hostname){
		if(channel.equalsIgnoreCase(back.Channel)){
			PresetActionPacket pap = new PresetActionPacket("logout", sender);
			back.returnPacket(pap);
			HostUserListUpdatePacket hulup = new HostUserListUpdatePacket();
			for(User s : getUsers(channel)){
				if(s.getNick().equalsIgnoreCase(getNick())){ continue; }
				hulup.userList.add(s.getNick());
			}
			back.returnPacket(hulup);
		}
	}
	
	public void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason){
		PresetActionPacket pap = new PresetActionPacket("logout", sourceNick);
		back.returnPacket(pap);
		HostUserListUpdatePacket hulup = new HostUserListUpdatePacket();
		for(User s : getUsers(back.Channel)){
			if(s.getNick().equalsIgnoreCase(getNick())){ continue; }
			hulup.userList.add(s.getNick());
		}
		back.returnPacket(hulup);
	}
	


	public void onNickChange(String oldNick, String login, String hostname, String newNick){
		if(newNick.equalsIgnoreCase(this.getNick())){
			back.NickName = newNick;
		}
		PresetActionPacket pap = new PresetActionPacket("ircchangenick", oldNick, newNick);
		back.returnPacket(pap);

	}
	
	public void onDisconnect(){
		doReconnect();
	}

	public void rename() {
		if(!getNick().equalsIgnoreCase(back.NickName)){
			changeNick(back.NickName);
		}
	}

	public void checkReconnect() {
		if(!isConnected()){
			doReconnect();
		}
	}

	public void doReconnect() {
		this.setName(back.NickName);
		this.setAutoNickChange(true);
		try{
			if(back.ServerPass==null){
				System.out.println("Connecting to : "+back.ServerName);
				this.connect(back.ServerName);
			}else{
				System.out.println("Connecting to : "+back.ServerName+" WITH PASSWORD");
				this.connect(back.ServerName, 6667, back.ServerPass);
			}
		}catch(IOException e){
			e.printStackTrace();
		}catch(NickAlreadyInUseException e){
			//doReconnect();
		}catch(IrcException e){
			e.printStackTrace();
		}
		this.joinChannel(back.Channel);
		this.setMode(back.NickName, "+B");
	}
	
}
