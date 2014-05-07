package uk.co.mobsoc.chat.server.json;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;
import java.util.Iterator;

import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Code borrowed and altered from turtlemeat.com, Unknown license, but stated as "free for all ... copy and modify it as you like..."
 * @author triggerhapp
 *
 */
public class JSONServer extends Thread{
	private KeyStore keyStore;
	private int port=-1;
	private JSONConnector connector=null;

	public JSONServer(JSONConnector connector, int port) {
		this.port = port;
		this.connector = connector;

		this.start();
	}
	
	private void debug(String s) {
		System.out.println(s);
	}
	
	public void run() {
		ServerSocket serverSocket = null;
	
		try {
			serverSocket = new ServerSocket(4241);
		} catch (IOException e) {
			e.printStackTrace();
		}
		debug("HTTPS Server listening on port "+port );
		while(true) { // Fix this up?
			try {
				Socket connectionSocket = serverSocket.accept(); // Await next connection
				new JSONHandler(connectionSocket, connector);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				
			}
		}
	}
	

  
}
