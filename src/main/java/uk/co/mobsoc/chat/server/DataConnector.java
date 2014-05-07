package uk.co.mobsoc.chat.server;

import java.net.Socket;
import java.util.ArrayList;

import org.json.JSONObject;

import uk.co.mobsoc.chat.common.Colour;
import uk.co.mobsoc.chat.common.Connector;
import uk.co.mobsoc.chat.common.packet.Packet;

/**
 * A Connector with extra data about the client on the other side
 * @author triggerhapp
 *
 */
public class DataConnector extends Connector{
	
	public Colour connectionColour=new Colour("axxxxxx");
	public String name = "";
	public boolean usable = false;
	public boolean isHost = false;
	public ArrayList<String> userList = new ArrayList<String>();
	public boolean destroy = false;
	public long latency;
	public boolean hideLogout=false;
	public boolean isConstantConnection=false;
	public boolean isCorrectVersion=false;
	public boolean hostIsMCNames=true;
	public String mcName;

	public DataConnector(Socket Socket) {
		super(Socket, "Generic Socket");
	}
	
	public DataConnector(){
	}
	
	/**
	 * Change stored data based on any relavent data in the server config
	 * @param config
	 */
	public void setConfig(JSONObject config) {
		String col = "a"+config.get("Colour");
		System.out.println("Setting Colour "+col);
		connectionColour = new Colour(col);
	}

	@Override
	public void send(Packet packet){
		if(socketSend==null){
			System.out.println(this+" has no send socket");
			return;
		}
		if(!usable){
			return;
		}
		super.send(packet);
	}

	public String getIP() {
		return socket.getInetAddress().toString();
	}
	
	public void mainThreadTick(){
	}

}
