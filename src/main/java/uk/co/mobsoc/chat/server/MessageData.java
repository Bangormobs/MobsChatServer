package uk.co.mobsoc.chat.server;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
/**
 * Information from DB about a Message in Core Chat
 * @author triggerhapp
 *
 */
public class MessageData {
	public long id = -1;
	public Timestamp timeSent = null;
	public String type, sender, message, colour;
	
	//`timeSent`, `type`, `sender`, `message`, `colour`
	public void setFromSql(ResultSet rs) {
		try {
			id = rs.getLong(1);
			timeSent = rs.getTimestamp(2);
			type = rs.getString(3);
			sender = rs.getString(4);
			message = rs.getString(5);
			colour = rs.getString(6);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
