package uk.co.mobsoc.chat.server.irc;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;

import uk.co.mobsoc.chat.common.Colour;
import uk.co.mobsoc.chat.common.Connector;
import uk.co.mobsoc.chat.common.SocketCallbacks;
import uk.co.mobsoc.chat.common.Util;
import uk.co.mobsoc.chat.common.packet.ActionPacket;
import uk.co.mobsoc.chat.common.packet.ChatPacket;
import uk.co.mobsoc.chat.common.packet.Packet;
import uk.co.mobsoc.chat.common.packet.ServerData;
import uk.co.mobsoc.chat.common.packet.UserListUpdatePacket;
import uk.co.mobsoc.chat.server.DataConnector;
import uk.co.mobsoc.chat.server.SUtils;

/**
 * A Pseudo-Client. Runs internally to the server and passes messages back and forth between IRC and Core by pretending to be a connected client, and using IRCBot class
 * @author triggerhapp
 *
 */
public class IRCConnector extends DataConnector{
	public IRCBot bot;
 	public static String code = "\u0003";
	public static ArrayList<Colour> colourList = new ArrayList<Colour>();
	public String Channel=null;
	public String Password=null;
	public String NickName=null;
	public String ServerName=null;
	public String ServerPass=null;
	public boolean stripFormat = false;
	public ArrayList<ServerData> serverList;
	
	public IRCConnector() {
	}

	@Override
	public void setConfig(JSONObject config){
		super.setConfig(config);
		ServerName = config.getString("host");
		if(!config.getString("serverpass").equalsIgnoreCase("None")){
			ServerPass = config.getString("serverpass");			
		}
		NickName = config.getString("nick");
		Password = config.getString("nickservpass");
		Channel = config.getString("chan");
		stripFormat = (config.getString("allowcolours").equals("false"));
		bot = new IRCBot(this);
		colourList.add(new Colour(code+"00",255,255,255));
		colourList.add(new Colour(code+"01",  0,  0,  0));
		colourList.add(new Colour(code+"02", 0, 0,170));
		colourList.add(new Colour(code+"03", 0,170, 0));
		colourList.add(new Colour(code+"04",170, 0, 0));
		colourList.add(new Colour(code+"05",102,54,31));
		colourList.add(new Colour(code+"06",170, 0,170));
		colourList.add(new Colour(code+"07",255, 170, 0));
		colourList.add(new Colour(code+"08",255,255, 85));
		colourList.add(new Colour(code+"09",85,255, 85));
		colourList.add(new Colour(code+"0",255,255,255));
		Colour.dC = new Colour("\u000f",  0,  0,  0);
		colourList.add(Colour.dC);
		colourList.add(new Colour(code+"1", 0, 0,0));
		colourList.add(new Colour(code+"2", 0, 0,170));
		colourList.add(new Colour(code+"3", 0,170, 0));
		colourList.add(new Colour(code+"4",170, 0, 0));
		colourList.add(new Colour(code+"5",102,54,31));
		colourList.add(new Colour(code+"6",170, 0,170));
		colourList.add(new Colour(code+"7",255, 170, 0));
		colourList.add(new Colour(code+"8",255,255, 85));
		colourList.add(new Colour(code+"9",85,255, 85));
		colourList.add(new Colour(code+"10", 85, 85, 255));
		colourList.add(new Colour(code+"11", 85,255,255));
		colourList.add(new Colour(code+"12", 0, 0,170));
		colourList.add(new Colour(code+"13",255, 85,255));
		colourList.add(new Colour(code+"14", 85, 85, 85));
		colourList.add(new Colour(code+"15",170,170,170));
		colourList.add(new Colour(code+"16",255,255,255));
		this.isHost = true;
		this.usable = true;
		this.loopback = false;
		this.isConstantConnection = true;
		this.isCorrectVersion = true;
		this.hostIsMCNames = false;
	}
	
	@Override
	/**
	 * Intercepts with a pseudo-send
	 */
	public void send(Packet packet){
		if(packet instanceof ActionPacket){
			ActionPacket ap = (ActionPacket) packet;
			String msg = InternalMarkupToIRC(ap.sourceColour.toInternal()+ap.action, stripFormat);
			bot.sendMessage(Channel, msg);
		}else if(packet instanceof ChatPacket){
			ChatPacket cp = (ChatPacket) packet;
			String msg = InternalMarkupToIRC(cp.sourceColour.toInternal()+"<"+cp.name+cp.sourceColour.toInternal()+"> "+Colour.dC.toInternal()+cp.message, stripFormat);
			bot.sendMessage(Channel, msg);
		}else if(packet instanceof UserListUpdatePacket){
			UserListUpdatePacket ulup = (UserListUpdatePacket) packet;
			serverList = ulup.servers;
		}
	}

	// Takes a Core string and turns it into an IRC string, without formatting if requested
	public static String InternalMarkupToIRC(String message, boolean stripFormat) {
		String sep = "§";
		String f = "";
		boolean first = true;
		for(String s : message.split(sep)){
			if(first){
				f=f+s;
				first = false;
			}else{
				String single = s.substring(0,7);
				String rest = s.substring(7,s.length());
				Colour c = new Colour(single);
				String formating = "";
				if(c.isBold()){
					formating = formating + "\u0002";
				}
				if(c.isItalic()){
					formating = formating + "\u0016";
				}
				if(c.isUnderline()){
					formating = formating + "\u001F";
				}
				if(c.isMagic()){
					formating = formating + "";
				}
				if(c.isStrikethrough()){
					formating = formating + "";
				}
				if(!stripFormat){
					f = f + getIRCColour(c)+formating + rest+ "\u000F";
				}else{
					f = f + rest;
				}
			}
		}
		return f;
	}
	
	public static Colour getColour(String string) {
		for(Colour c : colourList){
			if(c.getString().equalsIgnoreCase(string)){
				return c;
			}
		}
		return null;
	}
	
	public static String getIRCColour(Colour colour){
		return Util.getClosestColour(colourList, colour).getString();
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
	
	public void returnPacket(Packet p){
        boolean found=false;
        for(SocketCallbacks scb : callbacks){
            if(scb.packetRecieved(this, p)){ found=true; break; }
        }
        if(!found){
            Logger.getLogger(Connector.class.getName()).log(Level.SEVERE, "Packet "+p.getClass()+" dropped!");
        }
	}

	public static String IRCMarkupToInternal(String message) {
		//String sep = "(?=[\\u0003\\u0002\\u001F\\u0016\\u000F])";
		String f = "";
		//boolean first = true;
		Colour col = new Colour("axxxxxx");
		boolean bold = false, italic = false, under = false, strike = false, magic = false;
		int colourNum=0;
		String colour="";
		try{
		for(char c : message.toCharArray()){
			if(colourNum > 0 && c>='0' && c<='9'){
				colour = colour + Character.toString(c);
				colourNum--;
			}else{
				colourNum=0;
				if(colour.length()>0){ 
					col = getColour(code+colour).with(bold, italic, under, strike, magic);
					colour="";
					f = f + col.toInternal();
				}
				if(c == '\u0003'){
					//System.out.println("Colour");
					// It's a colour
					colourNum=2;
				}else if(c == '\u0002'){
					bold = !bold;
					col = col.with(bold, italic, under, strike, magic);
					f = f + col.toInternal();
				}else if(c == '\u001F'){
					under = !under;
					col = col.with(bold, italic, under, strike, magic);
					f = f + col.toInternal();
				}else if(c == '\u0016'){
					italic = !italic;
					col = col.with(bold, italic, under, strike, magic);
					f = f + col.toInternal();
				}else if(c == '\u000F'){
					// Reset
					col = new Colour("axxxxxx");
					bold = false; italic = false; under = false; strike = false; magic = false;
					f = f + col.toInternal();
				}else{
					f = f + Character.toString(c);
				}
			}			
		}
		}catch (Exception e){
			e.printStackTrace();
		}
		return f;
	}
	
	@Override
	public void mainThreadTick(){
		checkNick();
	}

	public void checkNick() {
		if(bot!=null && !bot.getNick().equalsIgnoreCase(NickName)){
			bot.changeNick(NickName);
		}
	}


}
