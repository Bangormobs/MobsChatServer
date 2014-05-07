package uk.co.mobsoc.chat.server.json;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;

import org.json.JSONObject;

/**
 * Handle a HTTP connection
 * @author triggerhapp
 *
 */
public class JSONHandler {
	JSONConnector connector = null;
	public JSONHandler(Socket s, JSONConnector connector){
		this.connector = connector;
		InetAddress client = s.getInetAddress();
		BufferedReader input = null;
		DataOutputStream output = null;
		try {
			input = new BufferedReader(new InputStreamReader(s.getInputStream()));
			output = new DataOutputStream(s.getOutputStream());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		http_handler(input, output);
	}
	
	private void debug(String s) {
		System.out.println(s);
	}
	
	private void http_handler(BufferedReader input, DataOutputStream output) {
		int method = 0;
		boolean keepAlive = true;
		int requestCount =0;
		//String http = "", path = "", file = "", userAgent = "";
		try {
			while(keepAlive){
				method = 0;
				requestCount++;
				String tmp = input.readLine();
				System.out.println("Request Number "+requestCount+" from this connection!");
				if(tmp == null){ return; }
				String tmp2 = new String(tmp);
				tmp = tmp.toUpperCase();
				if (tmp.startsWith("GET")) {
					System.out.println("Request type GET");
					method = 1;
				}
				if (tmp.startsWith("HEAD")) {
					System.out.println("Request type HEAD");
					method = 2;
				}
				if (tmp.startsWith("POST")){
					System.out.println("Request type POST");
					method = 3;
				}
				if (method == 0) {
					try {
						output.writeBytes(construct_http_header(501,0,"application/json"));
						output.close();
					} catch (IOException e) {
					}
					return;
				}
				HashMap<String, String> headers = getHeaders(input);
				keepAlive=false;
				String contents = getBody(input, headers);
				String outputString = null;
				if(method==1){
					// 	GET 
					String path = "";
					int start = 0, end = 0;
					for (int a = 0; a < tmp2.length(); a++) {
						if (tmp2.charAt(a) == ' ' && start != 0){
							end = a;
							break;
						}
						if (tmp2.charAt(a) == ' ' && start == 0){
							start = a;
						}
					}
					path = tmp2.substring(start+2, end);

					outputString = connector.getJSONFor(path);
					
				}else if(method==3){
				// 	POST
					outputString = connector.getJSONForPost(contents);
				}

				if(outputString == null){
					output.writeBytes(construct_http_header(404,0,"application/json"));
					output.close();
					return;
				}else{
					output.writeBytes(construct_http_header(200, outputString.length(),"application/json"));
					if(method!=0){
						output.writeBytes(outputString);
					//	output.writeChars(s);
					//	output.writeUTF(s);
					}
				}
				//output.writeBytes(construct_http_header(100, 0, null));
			}
			input.close();
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private String getBody(BufferedReader input, HashMap<String, String> headers){
		StringBuilder sb = new StringBuilder();
		int length = -1;
		if(headers.containsKey("content-length")){
			try{
				length = Integer.parseInt(headers.get("content-length"));
			}catch(NumberFormatException nfe){
			}
		}
		if(length>0){
			char[] buffer = new char[length];
			int numberRead=0;
			try {
				numberRead = input.read(buffer);
			} catch (IOException e) {
				e.printStackTrace();
			}
			sb.append(buffer, 0, numberRead);
		}else{
			// No Content-Length equals No content? We will see.
			
			//String s;
			//try {
//				s = input.readLine();
				//while(s!=null){
//					sb.append(s);
					//sb.append("\n");
					//s=input.readLine();
				//}
			//} catch (IOException e) {
//				e.printStackTrace();
			//}

		}
		return sb.toString();
	}
	
	private HashMap<String, String> getHeaders(BufferedReader input){
		HashMap<String, String> headers = new HashMap<String, String>();
		String s;
		try {
			s = input.readLine();
			while(!(s==null || s.equals(""))){
				if(s.contains(": ")){
					String[] data = s.split(": ");
					headers.put(data[0].toLowerCase(), data[1]);
				}
				s=input.readLine();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return headers;
	}

	private String construct_http_header(int code, int length, String content) {
		String s = "HTTP/1.1 ";
		switch (code){
		case 100:
			s = s + "100 Continue";
			break;
		case 200:
			s = s + "200 OK";
			break;
		case 400:
			s = s + "400 Bad Request";
			break;
		case 403:
			s = s + "403 Forbidden";
			break;
		case 404:
			s = s + "404 Not Found";
			break;
		case 500:
			s = s + "500 Internal Server Error";
			break;
		case 501:
			s = s + "501 Not Implemented";
			break;
		}
		s = s + "\r\n";
		s = s + "Server: MobsChatServer v0\r\n";
		s = s + "Access-Control-Allow-Origin: *\r\n";
		if(length >-1){
			s=s+"Content-Length: "+length+"\r\n";
		}
		if(content!=null && content.length()>2){
			s = s + "Content-Type: "+content+"; charset=utf-8\r\n";
		}
		s = s + "\r\n";
		return s;
	}
}
