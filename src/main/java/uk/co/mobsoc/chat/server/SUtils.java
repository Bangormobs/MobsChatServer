package uk.co.mobsoc.chat.server;

import java.util.HashMap;
import java.util.UUID;

import uk.co.mobsoc.chat.common.Colour;
import uk.co.mobsoc.chat.common.Util;
/**
 * A collection of Static Util functions
 * @author triggerhapp
 *
 */
public class SUtils {
	public static HashMap<String, String> customMessages = new HashMap<String, String>();
	
	public static String getMessageFormat(String key, String msg1){
		return getMessageFormat(key,msg1,"");
	}
	
	public static void setMessageFormat(String key, String value){
		if(key==null || key.equals("") || value==null || value.equals("")){
			return;
		}
		customMessages.put(key, value);
	}

	public static String getMessageFormat(String key, String msg1, String msg2){
		key=key.toLowerCase();
		if(customMessages.containsKey(key)){
			String message = customMessages.get(key);
			message = Util.replace(Util.replace(message, "%1", msg1), "%2", msg2);
			return message;
		}
		return "Unknown custom message : ("+key+", "+msg1+", "+msg2+")";
		
	}

	public static String simplifyColours(String message) {
		String sep = "§";
		String f = "";
		Colour lastCol = Colour.none;
		boolean first = true;
		for(String s : message.split(sep)){
			if(first){
				f = s;
				first = false;
			}else{
				String single = s.substring(0,7);
				String rest = s.substring(7,s.length());
				Colour col = new Colour(single);
				if(rest.length()>=1){
					if(col.equals(lastCol)){
						f = f + rest;
					}else{
						f = f + sep + single + rest;
						lastCol=col;
					}
				}
			}
		}
		return f;
	}

	public static String strip(String message) {
		return message.replaceAll("§.......", "");
	}
	
    public static byte[] uuidToBytes(UUID uuid){
    	long msb = uuid.getMostSignificantBits(), lsb = uuid.getLeastSignificantBits();
        byte[] buffer = new byte[16];

        for (int i = 0; i < 8; i++) {
                buffer[i] = (byte) (msb >>> 8 * (7 - i));
        }
        for (int i = 8; i < 16; i++) {
                buffer[i] = (byte) (lsb >>> 8 * (7 - i));
        }
        return buffer;
    }

	public static UUID bytesToUuid(byte[] uuid) {
        long msb = 0;
        long lsb = 0;
        if(uuid!=null && uuid.length == 16){
        	for (int i=0; i<8; i++)
                	msb = (msb << 8) | (uuid[i] & 0xff);
        	for (int i=8; i<16; i++)
                	lsb = (lsb << 8) | (uuid[i] & 0xff);
        }
        UUID u = new UUID(msb, lsb);
		return u;
	}


}
