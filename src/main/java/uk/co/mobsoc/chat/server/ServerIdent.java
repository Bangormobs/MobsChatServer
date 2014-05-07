package uk.co.mobsoc.chat.server;

import java.sql.ResultSet;
import java.sql.SQLException;
/**
 * Information from DB about a Servers Identity
 * @author triggerhapp
 *
 */
public class ServerIdent {
	public String id=null, pass, name, colour;
	
	public void set(ResultSet rs){
		try {
			id = rs.getString(1);
			pass = rs.getString(2);
			name = rs.getString(3);
			colour = rs.getString(4);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
