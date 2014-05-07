package uk.co.mobsoc.chat.server;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
/**
 * Information from DB about a private message
 * @author triggerhapp
 *
 */
public class PrivateMessageData {
	public long id = -1;
	public Timestamp timeSent = null;
	public Integer from, to;
	public String message, colour;
	public boolean seen=false;
	
	//`timeSent`, `type`, `sender`, `message`, `colour`
	public void setFromSql(ResultSet rs) {
		try {
			id = rs.getLong(1);
			timeSent = rs.getTimestamp(2);
			from = rs.getInt(3);
			to = rs.getInt(4);
			message = rs.getString(5);
			colour = rs.getString(6);
			seen = rs.getBoolean(7);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
}
