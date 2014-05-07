package uk.co.mobsoc.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;


import uk.co.mobsoc.chat.common.Colour;
import uk.co.mobsoc.chat.common.Connector;
import uk.co.mobsoc.chat.common.packet.ActionPacket;
import uk.co.mobsoc.chat.common.packet.ChatPacket;
import uk.co.mobsoc.chat.common.packet.Packet;
import uk.co.mobsoc.chat.common.packet.PrivateMessagePacket;
import uk.co.mobsoc.chat.common.packet.ServerData;
import uk.co.mobsoc.chat.common.packet.ServerInfoPacket;
import uk.co.mobsoc.chat.common.packet.UserListUpdatePacket;
import uk.co.mobsoc.chat.server.irc.IRCConnector;
/**
 * Core chat server - awaits connections on given port (4242 by default). Also used as a central access point for any DB access - Maybe I should move these?
 * @author triggerhapp
 *
 */
public class Server implements Runnable {
	public static String server="", dataBase="", userName="", passWord="";
	public static int port = 4242;
	public static Connection conn;
	private static PreparedStatement keepAlive, getMessagesSince, addMessage, getTimeNow, getMessageChunk, getLatestChunk, getLastPM, getLatestPMID, addPrivateMessage, getPMChunk, setPMSeen, checkServerInfo, insertServerInfo,updateServerInfo, serverStarted, setPlayerRank;
	private static PreparedStatement getUserData, getUserIdRealName, getUserMCDataforUUID, getMCNamesForUser,addUserMCName,getUserIDForMCName;
	private static PreparedStatement getServerIdent;
	//private static PreparedStatement getPlayerFBData, getPlayerFBDataFromMC, keepAlive, getMessagesSince, addMessage, getTimeNow, getMessageChunk, getLatestChunk, getUnseenPMs, getLatestPMID, addPrivateMessage, getPMChunk, setPMSeen, checkServerInfo, insertServerInfo,updateServerInfo, serverStarted, setPlayerRank;
	public static Server main;
	//
	public void run() {
		main = this;
        ServerSocket serverSocket;
        System.out.println("Preparing MySQL");
        System.out.println("Opening main host port "+port);
        try{

            Class.forName("com.mysql.jdbc.Driver").newInstance(); 
			conn = DriverManager.getConnection("jdbc:mysql://"+server+"/"+dataBase+"?user="+userName+"&password="+passWord);
        	Statement stat = conn.createStatement();
        	
			stat.execute("CREATE TABLE IF NOT EXISTS `Messages` (`id` bigint(20) NOT NULL AUTO_INCREMENT, `timeSent` datetime , `type` varchar(1), `sender` text, `message` text, `colour` varchar(10), PRIMARY KEY(`id`));");
			stat.execute("CREATE TABLE IF NOT EXISTS `PrivateMessages` (`id` bigint(20) NOT NULL, `timeSent` datetime, `from` text, `to` text, `message` text, `colour` varchar(10), `seen` tinyint(1))");
			stat.execute("CREATE TABLE IF NOT EXISTS `FacebookData` (`key` varchar(30), `realName` text, `mcName` varchar(16), `bangorID` text, `bangorNumber` text, `email` text, `rank` text, `email_key` text, `email_confirm` text, `legit` int(11), `ChatPass` varchar(60), `androidID` varchar(400))");
			stat.execute("CREATE TABLE IF NOT EXISTS `ServerIdent` (`id` text, `pass` text, `name` text, `colour` text)");
			
        	//getPlayerFBData = conn.prepareStatement("SELECT `key`, `realName`, `mcName`, `bangorID`, `bangorNumber`, `email`, `rank`, `email_key`, `email_confirm`, `ChatPass`, `legit`, `androidID` FROM `FacebookData` WHERE `key` = ?");
        	//getPlayerFBDataFromMC = conn.prepareStatement("SELECT `key`, `realName`, `mcName`, `bangorID`, `bangorNumber`, `email`, `rank`, `email_key`, `email_confirm`, `ChatPass`, `legit`, `androidID` FROM `FacebookData` WHERE `mcName` = ?");
			getServerIdent = conn.prepareStatement("SELECT `id`, `pass`, `name`, `colour` FROM `ServerIdent` WHERE `id` = ?");
			getUserData = conn.prepareStatement("SELECT `id`, `realName`, `rank`, `emailKey`, `emailConfirm`, `chatPass`, `faveName`,`chatPrefix` FROM `User` where `id` = ?");
			getUserIdRealName = conn.prepareStatement("SELECT `id` FROM `User` where `realName` = ?");
			getUserMCDataforUUID = conn.prepareStatement("SELECT `id`,`mcName`, `uuid` FROM `MCUser` WHERE `uuid` =  ?");
			getUserIDForMCName = conn.prepareStatement("SELECT `id` from `MCUser` where `mcName` = ?");
			getMCNamesForUser = conn.prepareStatement("SELECT `mcName`, `rank`,`uuid` FROM `MCUser` WHERE `id` = ?");
			addUserMCName = conn.prepareStatement("INSERT INTO `MCUser` (`id`, `mcName`, `uuid`) VALUES ( ? , ?,?)");
			
        	getMessageChunk = conn.prepareStatement("SELECT `id`, `timeSent`, `type`, `sender`, `message`, `colour` FROM `Messages` WHERE `id`>=? AND `id`<=? ORDER BY `id` ASC" );
        	getLatestChunk = conn.prepareStatement("SELECT MAX(`id`) AS `id` FROM `Messages`");
        	
        	getPMChunk = conn.prepareStatement("SELECT `id`, `timeSent`, `from`, `to`, `message`, `colour`, `seen` FROM `PrivateMessages` WHERE `id`>=? AND `id`<=? AND ((`from` = ? AND `to` = ?) OR  (`to` = ? AND `from` = ?)) ORDER BY `id` ASC" );        	
        	getLastPM = conn.prepareStatement("SELECT `id`, `timeSent`, `from`, `to`, `message`, `colour`, `seen` FROM `PrivateMessages` WHERE `seen` = 0 AND `to` = ?  ORDER BY `id` ASC" );
        	setPMSeen = conn.prepareStatement("UPDATE `PrivateMessages` SET `seen` = '1' WHERE `to` = ? AND `from` = ? AND `id` = ?");
        	getLatestPMID = conn.prepareStatement("SELECT MAX(`id`) AS `id` FROM `PrivateMessages` WHERE (`From` = ? AND `To` = ?) OR (`To` = ? AND `From` = ?)");
        	addPrivateMessage = conn.prepareStatement("INSERT INTO `PrivateMessages` (`id`, `timeSent`, `from`, `to`, `message`, `colour`, `seen`) VALUES ( ? , NOW() , ? , ?, ? , ?, 0)");

        	keepAlive = conn.prepareStatement("SELECT * FROM `User`");
        	getMessagesSince = conn.prepareStatement("SELECT `timeSent`, `type`, `sender`, `message`, `colour` FROM `Messages` WHERE `timeSent` > ? ORDER BY `timeSent` ASC");
        	addMessage = conn.prepareStatement("INSERT INTO `Messages` (`timeSent`, `type`, `sender`, `message`, `colour`) VALUES ( NOW() , ? , ? , ? , ?)");
        	checkServerInfo = conn.prepareStatement("SELECT * FROM `Server` WHERE `host` = ? AND `port` = ?");
        	insertServerInfo = conn.prepareStatement("INSERT INTO `Server` (`host`, `port`, `mapport`, `name`, `users`, `lastupdate` ) VALUES ( ? , ? , ? , ? , ? , NOW() )");
        	updateServerInfo = conn.prepareStatement("UPDATE `Server` set `mapport` = ?, `name` = ?, `users` = ?, `lastupdate` = NOW() WHERE `host`= ? AND `port` = ?");
        	serverStarted = conn.prepareStatement("UPDATE `Server` set `laststart` = NOW() where `host` = ? AND `port` = ?");
        	getTimeNow = conn.prepareStatement("SELECT NOW()");
        	setPlayerRank = conn.prepareStatement("UPDATE `User` SET `rank` = ? WHERE `id` = ?");
		}catch (SQLException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        // Fake IRC connection
        /*DataConnector iconn = new IRCConnector("irc.esper.net", null, "MOBS", "zehex", "#mobs","IRC", new Colour("aff00ff"));
        iconn.addCallback(new ServerCallbacks(iconn));
        Main.clients.add(iconn);

        DataConnector iconn2 = new IRCConnector("irc.twitch.tv", "oauth:efnn9s90kpg15ovkzn8fy97v6cj8fbd", "bangormobs1", "oauth:efnn9s90kpg15ovkzn8fy97v6cj8fbd", "#bangormobs1","Twitch", new Colour("affff00"));
        iconn2.addCallback(new ServerCallbacks(iconn2));
        ((IRCConnector)iconn2).stripFormat = true;
        Main.clients.add(iconn2);

        // Fake connection for Android users
        aconn = new AndroidConnector();
        aconn.addCallback(new ServerCallbacks(aconn));
        Main.clients.add(aconn);*/
        

        
        while(! Main.isStopped()){
            Socket clientSocket = null;
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                if(Main.isStopped()) {
                    System.out.println("Server Stopped.") ;
                    return;
                }
                throw new RuntimeException(
                    "Error accepting client connection", e);
            }
            DataConnector conn = new DataConnector(clientSocket);
            conn.addCallback(new ServerCallbacks(conn));
            synchronized(Main.clients){
                Main.clients.add(conn);
            }

        }
        try {
			serverSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
	
	public static int getUserIdRealName(String name){
		if(getUserIdRealName == null){ return -1; }
		synchronized(getUserIdRealName){
			try {
				getUserIdRealName.setString(1, name);
				getUserIdRealName.execute();
				ResultSet rs = getUserIdRealName.getResultSet();
				if(rs.next()){
					return rs.getInt(1);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return -1;
	}
	
	public static UserData getUserData(int id){
		UserData user = new UserData();
		if(getUserData == null){ return null; }
		synchronized(getUserData){
			try {
				getUserData.setInt(1, id);
				getUserData.execute();
				ResultSet rs = getUserData.getResultSet();
				if(rs.next()){
					user.setFromSql(rs);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return user;
	}
	
	public static long getLatestChunk(){
		long l = -1;
		synchronized(getLatestChunk){
			try {
				getLatestChunk.execute();
				ResultSet rs = getLatestChunk.getResultSet();
				if(rs.next()){
					return (long)(rs.getLong(1)/10);
				}
			} catch (SQLException e) {
		 		// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return l;
	}
	
	public static ArrayList<MessageData> getMessageChunk(long location){
		ArrayList<MessageData> msgs = new ArrayList<MessageData>();
		synchronized(getMessageChunk){
			try{
				getMessageChunk.setLong(1, location*10);
				getMessageChunk.setLong(2, (location*10)+9);
				getMessageChunk.execute();
				ResultSet rs = getMessageChunk.getResultSet();
				while(rs.next()){
					MessageData md = new MessageData();
					md.setFromSql(rs);
					msgs.add(md);
				}
			}catch (SQLException e){
				e.printStackTrace();
			}
		}
		return msgs;
	}
	
	public static ServerIdent getServerIdent(String id){
		ServerIdent ident = new ServerIdent();
		synchronized(getServerIdent){
			try{
				getServerIdent.setString(1, id);
				getServerIdent.execute();
				ResultSet rs = getServerIdent.getResultSet();
				if(rs.next()){
					ident.set(rs);
				}
			}catch(SQLException e){
			}
		}
		return ident;
	}
	
	public static ArrayList<PrivateMessageData> getPMChunk(Integer name1, Integer name2, long location){
		ArrayList<PrivateMessageData> msgs = new ArrayList<PrivateMessageData>();
		synchronized(getPMChunk){
			try{
				getPMChunk.setLong(1, location*10);
				getPMChunk.setLong(2, (location*10)+9); // Location
				
				getPMChunk.setInt(3, name1);
				getPMChunk.setInt(4, name2);
				getPMChunk.setInt(5, name1);
				getPMChunk.setInt(6, name2);
				getPMChunk.execute();
				ResultSet rs = getPMChunk.getResultSet();
				while(rs.next()){
					
					PrivateMessageData md = new PrivateMessageData();
					md.setFromSql(rs);
					msgs.add(md);
				}
			}catch (SQLException e){
				e.printStackTrace();
			}
		}
		return msgs;
	}
	
	public static ArrayList<MCData> getMCNames(int id){
		ArrayList<MCData> names = new ArrayList<MCData>();
		synchronized(getMCNamesForUser){
			try {
				getMCNamesForUser.setInt(1, id);
				getMCNamesForUser.execute();
				ResultSet rs = getMCNamesForUser.getResultSet();
				while(rs.next()){
					MCData mc = new MCData();
					mc.mcName = rs.getString(1);
					mc.rank = rs.getString(2);
					mc.uuid = SUtils.bytesToUuid(rs.getBytes(3));
					names.add(mc);
					checkMCData(mc);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}

		}
		return names;
	}
	
	private static void checkMCData(MCData mc) {
		if(mc.mcName!=null){
			if(mc.uuid == null || (mc.uuid.getMostSignificantBits() == 0l && mc.uuid.getLeastSignificantBits()==0l)){
				// TODO No UUID given - once-off lookup!
			}
		}
	}

	public static UserData getUserDataMC(UUID uuid){
		return getUserData(getMCData(uuid).id);
	}
	
	public static int getIDForMCName(String name){
		try {
			getUserIDForMCName.setString(1, name);
			getUserIDForMCName.execute();
			ResultSet rs = getUserIDForMCName.getResultSet();
			if(rs.next()){
				return rs.getInt(1);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return -1;
	}
	
	public static MCData getMCData(UUID uuid){
		MCData mc = new MCData();
		synchronized(getUserMCDataforUUID){
			try {
				getUserMCDataforUUID.setBytes(1, SUtils.uuidToBytes(uuid));
				getUserMCDataforUUID.execute();
				ResultSet rs = getUserMCDataforUUID.getResultSet();
				if(rs.next()){
					mc.id = rs.getInt(1); 
					mc.mcName = rs.getString(2);
					mc.uuid = SUtils.bytesToUuid(rs.getBytes(3));
				}
			} catch (SQLException e) {

				e.printStackTrace();
			}
		}
		checkMCData(mc);
		return mc;
	}

	/**
	 * Called regularly to keep the MySQL connection alive
	 */
	public static void mysqlTick() {
		try {
			keepAlive.execute();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void updateAllServerLists() {
		ServerData main = new ServerData();
		main.connectionColour = new Colour("axxxxxx");
		main.connectionName = "Chat";
		UserListUpdatePacket ulup = new UserListUpdatePacket();
		ulup.servers.add(main);
		synchronized(Main.clients){

			for(Connector c : Main.clients){
				DataConnector dC = (DataConnector) c;
				if(!dC.usable || dC.destroy){ continue; }
				if(dC.isHost){
					ServerData sd = new ServerData();
					sd.connectionColour = dC.connectionColour;
					sd.connectionName = dC.name;
					for(String s : dC.userList){
						sd.userList.add(s);
					}
					ulup.servers.add(sd);
				}else{
					main.userList.add(dC.name);
				}
			}
			sendAll(null, ulup);
		}		
	}

	public static void sendAll(DataConnector dConn, Packet p) {
		sendAll(dConn, p, false);
	}

	public static void sendAll(DataConnector dConn, Packet p,
			boolean allowLoopback) {
		ArrayList<Connector> copyList;
		if(p instanceof ChatPacket){
			Server.addMessage((ChatPacket)p);
		}
		if(p instanceof ActionPacket){
			Server.addMessage((ActionPacket)p);
		}
		if(p instanceof PrivateMessagePacket){
			Server.addMessage((PrivateMessagePacket) p);
		}
		synchronized(Main.clients){
			copyList = new ArrayList<Connector>(Main.clients);
		}
		// All connected devices
		for(Connector c : copyList){
			if(allowLoopback==false && c.loopback == false && c == dConn){ c.debug("Loopback off and same conn"); continue; }
			if(c instanceof DataConnector){
				if(((DataConnector)c).isConstantConnection==false){
					c.debug("Not a constant connection - skipping");
					continue;
				}
			}
			c.debug(p+" sent");
			c.send(p);
		}
	}



	public static Timestamp getTimeNow(){
		try {
			getTimeNow.execute();
			ResultSet rs = getTimeNow.getResultSet();
			if(rs.next()){
				return rs.getTimestamp(1);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public static ArrayList<PrivateMessageData> getUnseenPMs(Integer key){
		ArrayList<PrivateMessageData> list = new ArrayList<PrivateMessageData>();
		synchronized(getLastPM){
			try {
				getLastPM.setInt(1, key);
				getLastPM.execute();
				ResultSet rs = getLastPM.getResultSet();
				while(rs.next()){
					PrivateMessageData data = new PrivateMessageData();
					data.setFromSql(rs);
					list.add(data);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}

		}
		return list;
	}
	
	public static long getLatestPMChunk(Integer from, Integer to){
		return (getPrivateMessageID(from, to)/10l);	
	}
	
	
	private static long getPrivateMessageID(Integer from, Integer to) {
		long l = 0;
		synchronized(getLatestPMID){
			try {
				getLatestPMID.setInt(1, from);
				getLatestPMID.setInt(2, to);
				getLatestPMID.setInt(3, from);
				getLatestPMID.setInt(4, to);
				getLatestPMID.execute();
				ResultSet rs = getLatestPMID.getResultSet();
				if(rs.next()){
					l = rs.getLong(1);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}

		}
		return l;
	}

	private static void addMessage(PrivateMessagePacket p) {
		//(`id`, `timeSent`, `from`, `to`, `message`, `colour`, `seen`)
		long unqid = getPrivateMessageID(p.fromI, p.toI)+1;
		synchronized(addPrivateMessage){
			try {
				addPrivateMessage.setLong(1 , unqid);
				addPrivateMessage.setInt(2, p.fromI);
				addPrivateMessage.setInt(3, p.toI);
				addPrivateMessage.setString(4, p.message);
				addPrivateMessage.setString(5, "axxxxxx");
				addPrivateMessage.execute();
			} catch (SQLException e) {
				e.printStackTrace();
			}

		}
	}

	public static void addMessage(ChatPacket ap) {
		synchronized(addMessage){
			try {
				addMessage.setString(1, "M");
				addMessage.setString(2, ap.name);
				addMessage.setString(3, ap.message);
				addMessage.setString(4, ap.sourceColour.toStream());
				addMessage.execute();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public static void addMessage(ActionPacket ap) {
		synchronized(addMessage){
			try {
				addMessage.setString(1, "A");
				addMessage.setString(2, "");
				addMessage.setString(3, ap.action);
				addMessage.setString(4, ap.sourceColour.toStream());
				addMessage.execute();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public static String getFullName(UserData user, String name){
		String rankPrefix = "";
		String namePrefix = user.prefix;
		if(namePrefix==null){ namePrefix=""; }
		if(user.rank.equalsIgnoreCase("a")){
			if(namePrefix.equals("")){ namePrefix="A"; }
			rankPrefix = "§aff00ff("+namePrefix+")";
		}else if(user.rank.equalsIgnoreCase("c")){
			if(namePrefix.equals("")){ namePrefix="C"; }
			rankPrefix = "§a00ff00("+namePrefix+")";
		}else if(user.rank.equalsIgnoreCase("p")){
			if(namePrefix.equals("")){ namePrefix="P"; }
			rankPrefix = "§a00aaaa("+namePrefix+")";			
		}else if(user.rank.equalsIgnoreCase("s")){
			if(namePrefix.equals("")){ namePrefix="S"; }
			rankPrefix = "§affaa00("+namePrefix+")";			
		}else if(user.rank.equalsIgnoreCase("t")){
			if(namePrefix.equals("")){ namePrefix="T"; }
			rankPrefix = "§affff00("+namePrefix+")";
		}else if(user.rank.equalsIgnoreCase("u")){
			if(namePrefix.equals("")){ namePrefix="U"; }
			rankPrefix = "§affffff("+namePrefix+")";
		}else if(user.rank.equalsIgnoreCase("b")){
			if(namePrefix.equals("")){ namePrefix="B"; }
			rankPrefix = "§aff0000("+namePrefix+")";
		}else{
			if(namePrefix.equals("")){ namePrefix="V"; }
			rankPrefix = "§adddddd("+namePrefix+")";
		}
		return rankPrefix+Colour.none.toInternal()+name;
	}


	public static ArrayList<Packet> getAllMessagesSince(Timestamp t) {
		ArrayList<Packet> pList = new ArrayList<Packet>();
		try {
			getMessagesSince.setTimestamp(1, t);
			getMessagesSince.execute();
			ResultSet rs = getMessagesSince.getResultSet();
			while(rs.next()){
				String type = rs.getString(2);
				if(type.equalsIgnoreCase("M")){
					ChatPacket cp = new ChatPacket();
					cp.name = rs.getString(3);
					cp.message = rs.getString(4);
					cp.sourceColour = new Colour(rs.getString(5));
					pList.add(cp);
				}else if(type.equalsIgnoreCase("A")){
					ActionPacket ap = new ActionPacket();
					ap.action = rs.getString(4);
					ap.sourceColour = new Colour(rs.getString(5));
					pList.add(ap);
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return pList;
		
	}


	public static void setPMSeen(PrivateMessageData md) {
		synchronized(setPMSeen){
			try {
				setPMSeen.setInt(1, md.to);
				setPMSeen.setInt(2, md.from);
				setPMSeen.setLong(3, md.id);
				setPMSeen.execute();
			} catch (SQLException e) {
				e.printStackTrace();
			}

		}
	}

	public static void updateServerInfo(ServerInfoPacket sip) {
		// `host`, `port`, `mapport`, `name`, `users`, `lastupdate`
		try {
			checkServerInfo.setString(1, sip.hostname);
			checkServerInfo.setInt(2 , sip.port);
			checkServerInfo.execute();
			if(!checkServerInfo.getResultSet().next()){
				insertServerInfo.setString(1, sip.hostname);
				insertServerInfo.setInt(2, sip.port);
				insertServerInfo.setInt(3, sip.mapport);
				insertServerInfo.setString(4, sip.name);
				insertServerInfo.setString(5, sip.userlist);
				insertServerInfo.execute();
			}else{
				updateServerInfo.setString(4, sip.hostname);
				updateServerInfo.setInt(5, sip.port);
				updateServerInfo.setInt(1, sip.mapport);
				updateServerInfo.setString(2, sip.name);
				updateServerInfo.setString(3, sip.userlist);
				updateServerInfo.execute();
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(sip.isConnect){
			try {
				serverStarted.setString(1, sip.hostname);
				serverStarted.setInt(2, sip.port);
				serverStarted.execute();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}	
	}

	public static void addUserMCName(Integer key, String mcName, UUID uuid) {
		if(key==null || key.equals("")){
			return;
		}
		synchronized(addUserMCName){
			try {
				addUserMCName.setInt(1, key);
				addUserMCName.setString(2, mcName );
				addUserMCName.setBytes(3, SUtils.uuidToBytes(uuid));
				addUserMCName.execute();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public static String getFullName(String mcName) {
		UserData ud = getUserData(getIDForMCName(mcName));
		return getFullName(ud, mcName);
	}
}

