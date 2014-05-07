package uk.co.mobsoc.chat.server;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import uk.co.mobsoc.chat.common.Colour;
import uk.co.mobsoc.chat.common.packet.ActionPacket;
import uk.co.mobsoc.chat.common.packet.SetRankPacket;
/**
 * Information from DB about a User.
 * @author triggerhapp
 *
 */
public class UserData {
	public static HashMap<UUID, String> lastRanks = new HashMap<UUID, String>();
	public ArrayList<MCData> mcNames = new ArrayList<MCData>();
	public String realName, rank="",emailKey,emailConfirm,chatPass=null, faveName,prefix;
	public Integer key=0;
	public boolean assumed = false;
	public String getName(String chosenName){
		if(chosenName.equals("")){
			return faveName;
		}
		// Is it their real name?
		if(chosenName.equalsIgnoreCase(realName)){
			return chosenName;
		}
		// Is it one of their MC names?
		for(MCData mc : mcNames){
			if(mc.mcName.equalsIgnoreCase(chosenName)){
				return chosenName;
			}
		}
		// Ok so we failed. We need a name for this user.
		for(MCData mc: mcNames){
			return mc.mcName;
		}
		// So many fails. Now we pray they've given a real name.
		return realName;
	}
	public UserData(){

	}
	public void setFromSql(ResultSet rs) {
		//`id`, `realName`, `rank`, `emailKey`, `emailConfirm`, `chatPass`
		try {
			key = rs.getInt(1);
			realName = rs.getString(2);
			rank = rs.getString(3);
			emailKey = rs.getString(4);
			emailConfirm = rs.getString(5);
			chatPass = rs.getString(6);
			faveName = rs.getString(7);
			prefix = rs.getString(8);
			if(faveName == null || faveName.equalsIgnoreCase("")){
				faveName=realName;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		if(rank == null || rank.equals("")){
			assumed = true;
			rank="U";
			//if(bangorID.length()==6){
			//	int i = Integer.parseInt(bangorNumber);
			//	if(i<501000000 && i>499999999){
			if(emailKey == null || emailConfirm==null){ 
				
			}else{
				if(emailKey.length()>10 && emailKey.equalsIgnoreCase(emailConfirm)){
					rank="S";
				}
			}
			//	}
			//}
		}
		// Get all linked MC Accounts
		mcNames = Server.getMCNames(key);
		
		for(MCData mc : mcNames){
			if(lastRanks.containsKey(mc.uuid)){
				if(rank.equalsIgnoreCase(lastRanks.get(mc.uuid))){
				// 	All is well
				}else{
					//System.out.println("User '"+mc.mcName+"' changed from rank "+lastRanks.get(mc.mcName.toLowerCase())+"' to '"+rank+"'");
					SetRankPacket srp = new SetRankPacket();
					srp.uuid = mc.uuid;
					srp.rank = rank;
					//ActionPacket ap = new ActionPacket();
					//ap.sourceColour= Colour.none;
					//ap.action=mc.mcName+" has changed from rank '"+lastRanks.get(mc.mcName.toLowerCase())+"' to '"+rank+"'";
					//Server.sendAll(null, ap);
					Server.sendAll(null, srp);
					lastRanks.put(mc.uuid, rank);
				}
			}else{
				System.out.println("User '"+mc.mcName+"' set to '"+rank+"'");
				SetRankPacket srp = new SetRankPacket();
				srp.uuid = mc.uuid;
				srp.rank = rank;
				Server.sendAll(null, srp);
				lastRanks.put(mc.uuid, rank);
			}
		}
		//lastRanks.put(mc.uuid, rank);
	}
	
	public static Integer getUserFromName(String name) {
		if(name.contains(" ")){
			// Hey It's a full name!
			return Server.getUserIdRealName(name);
		}
		return Server.getIDForMCName(name);
	}
}
