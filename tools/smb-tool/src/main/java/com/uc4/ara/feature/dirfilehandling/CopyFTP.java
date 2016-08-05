package com.uc4.ara.feature.dirfilehandling;

import it.sauronsoftware.ftp4j.FTPAbortedException;
import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPConnector;
import it.sauronsoftware.ftp4j.FTPDataTransferException;
import it.sauronsoftware.ftp4j.FTPException;
import it.sauronsoftware.ftp4j.FTPFile;
import it.sauronsoftware.ftp4j.FTPIllegalReplyException;
import it.sauronsoftware.ftp4j.FTPListParseException;
import it.sauronsoftware.ftp4j.connectors.DirectConnector;
import it.sauronsoftware.ftp4j.connectors.HTTPTunnelConnector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.uc4.ara.feature.FeatureUtil;
import com.uc4.ara.feature.globalcodes.ErrorCodes;
import com.uc4.ara.feature.utils.FileUtil;

public class CopyFTP extends AbstractCopy {

	private final boolean secure;
	private String path;

	public CopyFTP(String host, int port, String username, String password,
			String from, boolean recursive, String to, boolean overwrite,
			long timeout, boolean preserve,
			String proxyHost, int proxyPort, String proxyUser,
			String proxyPassword, String transferMode, boolean secure) {

		super(host, port, username, password, from, recursive, to, overwrite,
				timeout, preserve, proxyHost, proxyPort, proxyUser, proxyPassword, transferMode, null);
		this.secure = secure;
	}

	@Override
	public int retrieve() throws Exception{

		from = from.replaceAll("\\\\", "/");
		from = FileUtil.normalize(from);
		if(! from.startsWith("/")) from = "/" + from;

		if (port == -1 && secure)
			port = 990;
		if (port == -1 && !secure)
			port = 21;

		TrustManager[] trustManager = new TrustManager[] { new X509TrustManager() {
			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
			@Override
			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}
			@Override
			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} };
		SSLContext sslContext = null;

		sslContext = SSLContext.getInstance("SSL");
		sslContext.init(null, trustManager, new SecureRandom());

		SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
		FTPClient client = new FTPClient();
		client.setSSLSocketFactory(sslSocketFactory);

		FTPConnector connector = null;

		if (proxyHost != null && proxyHost.length() > 0) {
			connector = new HTTPTunnelConnector(proxyHost, proxyPort, proxyUser, proxyPassword);
		}else
			connector = new DirectConnector();

		connector.setConnectionTimeout((int)timeout/1000);
		connector.setReadTimeout((int)timeout/1000);

		if(secure)
			client.setSecurity(FTPClient.SECURITY_FTPS);
		// client.setSecurity(FTPClient.SECURITY_FTPES);

		if (transferMode.equalsIgnoreCase("Binary")){
			client.setType(FTPClient.TYPE_BINARY);
		}else if(transferMode.equalsIgnoreCase("Text")){
			client.setType(FTPClient.TYPE_TEXTUAL);
		}else
			client.setType(FTPClient.TYPE_AUTO);

		path = "ftp" + (secure ? "s" : "") + "://" + host + ":" + port  ;

		try {
			client.setConnector(connector);
			FeatureUtil.logMsg("Connecting to " + path + " ...");
			try{
				client.connect(host, port);
			} catch(IOException e){
				FeatureUtil.logMsg("The machine at " + host +
						" can not be connected. Please check the host name, port or proxy setting again. Aborting ...");
				return ErrorCodes.EXCEPTION;
			}

			try{
				client.login(username, password);
			}catch (IOException e) {
				FeatureUtil.logMsg("The machine at " + client.getHost() +
						" is connected but can not authenticate. Please check the username or password again. Aborting ...");
				return ErrorCodes.EXCEPTION;
			}

			client.setPassive(true);

			File localFile = new File(to);
			createParentDir(localFile);

			if(from.contains("*") || from.contains("?"))
				deepRetrieveWildCard(client, from, localFile);

			else if (isPathDirectory(client, from))
				deepRetrieve(client, from, localFile);

			else if(isPathFile(client, from)) {

				if(from.endsWith("/"))
					from = from.substring(0, from.length() - 1 );
				if(localFile.isDirectory())
					localFile = new File(localFile, from.substring(from.lastIndexOf("/"), from.length()));

				singleFileRetrieve(client, from, localFile);

			} else{
				FeatureUtil.logMsg(path + from + " does not exist. Aborting ...");
				return ErrorCodes.ERROR;
			}

		} catch (FTPException e) {
			FeatureUtil.logMsg("Server return code: " + e.getCode() + ", message: " + e.getMessage());
			return ErrorCodes.EXCEPTION;

		} catch (FTPDataTransferException e) {
			FeatureUtil.logMsg("The machine at " + host +
					" is connected but data transfer connection failed!. Aborting ...");
			return ErrorCodes.EXCEPTION;

		}catch (UserException e) {
			FeatureUtil.logMsg(e.getMessage() + ". Aborting ...");
			return ErrorCodes.ERROR;

		}finally {

			if(client != null && client.isConnected())
				client.disconnect(false);
		}

		return errorCode;

	}

	private void deepRetrieveWildCard(FTPClient ftpClient, String from, File localFile)
			throws IllegalStateException, IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException, FTPListParseException, UserException{

		String srcRemote = FileUtil.getSourceWildCard(from);

		List<String> files = getFilesWildCard(ftpClient,"", from);
		for (String file : files) {
			File currentLocal =  new File(localFile, file.substring(srcRemote.length()));
			createParentDir(currentLocal);

			if ( recursive && isPathDirectory(ftpClient, file))
				deepRetrieve(ftpClient, file, currentLocal);

			else
				singleFileRetrieve(ftpClient, file, currentLocal);
		}

	}

	private void deepRetrieve(FTPClient ftpClient, String from, File localFile)
			throws IllegalStateException, IOException, FTPIllegalReplyException, FTPDataTransferException, FTPAbortedException, FTPListParseException, UserException{

		if(localFile.isFile() ) {
			if(overwrite) {
				FeatureUtil.logMsg(localFile.getAbsolutePath() + " already exists!. Deleting ...");
				localFile.delete();
			} else {
				FeatureUtil.logMsg("CONFLICT:'" + localFile.getAbsolutePath() + "' is a FILE <=> '" +
						path + FileUtil.normalize(from)  +"' is a DIRECTORY ( overwrite is NO ). Skipping ...");
				return;
			}
		}

		if(!localFile.exists() ) localFile.mkdir();

		FTPFile[] files = null;
		try {
			files = ftpClient.list(from);

		} catch(FTPException e){
			throw new UserException("Server return code: " + e.getCode() + ", message: " + e.getMessage()
					+ "Can not list file at path '"+ path + FileUtil.normalize(from) + "'");
		}

		for (FTPFile file : files) {
			String fName = file.getName();
			int fType = file.getType();
			if(fName.equals(".") || fName.equals("..")) continue;

			if (fType == FTPFile.TYPE_DIRECTORY && recursive)
				deepRetrieve(ftpClient, from + "/" + fName + "/", new File(localFile, fName));

			else if(fType == FTPFile.TYPE_FILE)
				singleFileRetrieve(ftpClient, from +  "/" + fName, new File(localFile, fName));

		}

	}

	private void singleFileRetrieve(FTPClient ftpClient, String from, File f)
			throws UserException, IllegalStateException, FileNotFoundException, IOException, FTPIllegalReplyException, FTPDataTransferException, FTPAbortedException {

		if(f.exists()){
			if(overwrite){
				FeatureUtil.logMsg("'" + f.getCanonicalPath() + "' already exists. Deleting ...");
				if(f.isDirectory()) FileUtil.deleteDirectory(f);
				else f.delete();
			}else {
				FeatureUtil.logMsg("'" + f.getCanonicalPath() + "' already exists ( overwrite is NO ). Skipping ...");
				return;
			}
		}

		FeatureUtil.logMsg("Copying  '" + path + FileUtil.normalize(from) + "' => '" + f.getAbsolutePath() + "'");

		try {
			ftpClient.download(from, f);
		} catch(FTPException e){
			throw new UserException("Server return code: " + e.getCode() + ", message: " + e.getMessage() +
					". Can not download file from '"+ path  + FileUtil.normalize(from) + "' => '" + f.getAbsolutePath() + "'" );
		}

	}

	private List<String> getFilesWildCard(FTPClient client, String currentPath, String nextPath)
			throws IllegalStateException, IOException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException, FTPListParseException{

		String[] dirs = nextPath.split("/+");
		List<String> listFiles = new ArrayList<String>();

		int count = 0;
		for (String dir : dirs){
			count ++;
			if(!dir.equals("")){
				nextPath = nextPath.substring(dir.length() + 1);
				if( !dir.contains("*")  && !dir.contains("?") ){
					currentPath = currentPath + "/" + dir;
					if(count == dirs.length) {
						if( isPathDirectory(client, currentPath) || isPathFile(client, currentPath) )
							listFiles.add(currentPath);
					} else if(! isPathDirectory(client, currentPath))  break;

				} else {
					for(FTPFile file : client.list(currentPath)){
						if(file.getName().matches(FileUtil.convertWildCard(dir))) {
							if(count == dirs.length)
								listFiles.add(currentPath  + "/" + file.getName());
							else if(file.getType() ==  FTPFile.TYPE_DIRECTORY)
								listFiles.addAll(getFilesWildCard(client, currentPath  + "/" + file.getName(), nextPath ));
						}
					}
					break;
				}
			}
		}
		return listFiles;
	}

	@Override
	public int store() throws Exception {

		to = to.replaceAll("\\\\", "/");
		to = FileUtil.normalize(to);
		if(! to.startsWith("/") ) to = "/" + to;

		if (port == -1 && secure)
			port = 990;
		if (port == -1 && !secure)
			port = 21;

		TrustManager[] trustManager = new TrustManager[] { new X509TrustManager() {
			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
			@Override
			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}
			@Override
			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} };
		SSLContext sslContext = null;

		sslContext = SSLContext.getInstance("SSL");
		sslContext.init(null, trustManager, new SecureRandom());


		SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
		FTPClient client = new FTPClient();
		client.setSSLSocketFactory(sslSocketFactory);
		FTPConnector connector = null;
		if (proxyHost != null && proxyHost.length() > 0) {
			connector = new HTTPTunnelConnector(proxyHost, proxyPort, proxyUser, proxyPassword);
		}else
			connector = new DirectConnector();

		connector.setConnectionTimeout((int)timeout/1000);
		connector.setReadTimeout((int)timeout/1000);

		if(secure){
			client.setSecurity(FTPClient.SECURITY_FTPS); // or client.setSecurity(FTPClient.SECURITY_FTPES);
		}

		if (transferMode.equalsIgnoreCase("Binary")){
			client.setType(FTPClient.TYPE_BINARY);
		}else if(transferMode.equalsIgnoreCase("Text")){
			client.setType(FTPClient.TYPE_TEXTUAL);
		}else
			client.setType(FTPClient.TYPE_AUTO);

		path = "ftp" + (secure ? "s" : "") + "://" + host + ":" + port  ;

		try {
			client.setConnector(connector);
			FeatureUtil.logMsg("Connecting to " + path + " ...");

			try{
				client.connect(host, port);
			} catch(IOException e){
				FeatureUtil.logMsg("The machine at " + host +
						" can not be connected. Please check the host name, port or proxy setting again. Aborting ...");
				return ErrorCodes.EXCEPTION;
			}

			try{
				client.login(username, password);
			}catch (IOException e) {
				FeatureUtil.logMsg("The machine at " + client.getHost() +
						" is connected but can not authenticate. Please check the username or password again. Aborting ...");
				return ErrorCodes.EXCEPTION;
			}

			client.setPassive(true);

			createDirectoryRecursive(client, FileUtil.normalize(to + "/../"));
			File localFile = new File(from);

			if (localFile.isDirectory())
				deepStore(client, localFile, to);

			else if (localFile.isFile()) {

				if(isPathDirectory(client, to))
					to = to + "/" + localFile.getName();

				singleFileStore(client, localFile, to);

			}else{
				FeatureUtil.logMsg(localFile.getAbsolutePath() + " does not exists. Please check the path again. Aborting ...");
				errorCode = ErrorCodes.ERROR;
			}

		} catch (FTPException e) {
			FeatureUtil.logMsg("Server return code: " + e.getCode() + ", message: " + e.getMessage());
			return ErrorCodes.EXCEPTION;
		} catch (FTPDataTransferException e) {
			FeatureUtil.logMsg("The machine at " + host + " is connected but data transfer connection failed!. Aborting ...");
			return ErrorCodes.EXCEPTION;
		} finally {
			//			if(client != null && client.isAuthenticated())
			//				client.logout();
			if(client != null && client.isConnected())
				client.disconnect(false);
		}

		return errorCode;
	}


	private void deepStore(FTPClient ftpClient, File localFile, String to)
			throws IOException, IllegalStateException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException, FTPListParseException {

		if(isPathFile(ftpClient, to) ) {
			if(overwrite) {
				FeatureUtil.logMsg("'" + path + FileUtil.normalize(to) + "' already exists!. Deleting ...");
				ftpClient.deleteFile(to);
				ftpClient.createDirectory(to);
			} else {
				FeatureUtil.logMsg("CONFLICT:'" + localFile.getAbsolutePath() + "' is DIRECTORY <=> '" +
						path + FileUtil.normalize(to)  +"' is FILE ( overwrite is NO ). Skipping ...");
				return;
			}
		}else if(! isPathDirectory(ftpClient, to))
			ftpClient.createDirectory(to);

		for (File file : localFile.listFiles()) {

			if (file.isDirectory() && recursive)
				deepStore(ftpClient, file , to + "/" + file.getName() + "/");

			else if(file.isFile())
				singleFileStore(ftpClient, file, to + "/" + file.getName());
		}
	}

	private void singleFileStore(FTPClient ftpClient, File localFile, String to)
			throws IOException, IllegalStateException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException, FTPListParseException {

		boolean isFile = isPathFile(ftpClient, to);
		boolean isDir = isPathDirectory(ftpClient, to) ;

		if(isFile || isDir) {
			if(overwrite) {
				FeatureUtil.logMsg("'" + path + FileUtil.normalize(to) + "' already exists. Deleting ...");

				if(isFile)
					ftpClient.deleteFile(to);
				else if(isDir)
					deleteDirectory(ftpClient, to);

			} else {
				FeatureUtil.logMsg("'" + path + FileUtil.normalize(to) + "' already exists ( overwrite is NO ). Skipping ...");
				return;
			}
		}

		FeatureUtil.logMsg("Copying '" + localFile.getAbsolutePath()  + "' => '" + path + FileUtil.normalize(to) + "'");
		InputStream input = new FileInputStream(localFile);

		try {
			ftpClient.upload(to, input, 0, 0, null);
		} finally {
			input.close();
		}
	}

	/**
	 * Determines whether a directory exists or not
	 * @param dirPath
	 * @return true if exists, false otherwise
	 * @throws FTPIllegalReplyException
	 * @throws IllegalStateException
	 * @throws IOException thrown if any I/O error occurred.
	 */
	private boolean isPathDirectory(FTPClient ftpClient, String dirPath) throws IllegalStateException, IOException, FTPIllegalReplyException {
		try {
			ftpClient.changeDirectory(dirPath);

		}catch (FTPException e){
			if (e.getCode() == 550)
				return false;
		};
		return true;
	}

	/**
	 * Determines whether a file exists or not
	 * @param filePath
	 * @return true if exists, false otherwise
	 * @throws IOException thrown if any I/O error occurred.
	 * @throws FTPIllegalReplyException
	 * @throws IllegalStateException
	 */
	private boolean isPathFile(FTPClient ftpClient, String filePath) throws IOException, IllegalStateException, FTPIllegalReplyException {
		try {
			ftpClient.fileSize(filePath);
		}catch (FTPException e){
			if (e.getCode() == 550)
				return false;
		};

		return true;
	}

	/**
	 * Delete a non empty directory in FTP server recursively
	 * @param ftpClient
	 * @param path
	 * @return
	 * @throws IOException
	 * @throws FTPListParseException
	 * @throws FTPAbortedException
	 * @throws FTPDataTransferException
	 * @throws FTPException
	 * @throws FTPIllegalReplyException
	 * @throws IllegalStateException
	 */
	private void deleteDirectory(FTPClient ftpClient, String path)
			throws IOException, IllegalStateException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException, FTPListParseException {
		FTPFile[] ftpFiles = ftpClient.list(path);

		for(FTPFile f : ftpFiles){
			if(f.getName().equals(".") || f.getName().equals("..")) continue;
			if(f.getType() == FTPFile.TYPE_DIRECTORY) deleteDirectory(ftpClient, path + "/" + f.getName());
			else ftpClient.deleteFile(path + "/" + f.getName());
		}
		ftpClient.deleteDirectory(path);

	}

	private boolean createDirectoryRecursive(FTPClient ftpClient, String dirPath)
			throws IOException, IllegalStateException, FTPIllegalReplyException, FTPException, FTPDataTransferException, FTPAbortedException, FTPListParseException {

		String[] dirs = dirPath.split("/+");
		String currentDir = "";
		for (String dir : dirs){
			if(!dir.equals("")){
				currentDir = currentDir + "/" + dir;
				try{
					if(isPathFile(ftpClient, currentDir )){
						FeatureUtil.logMsg("Remote directory '" + path + FileUtil.normalize(dirPath) + "' can not be created!");
						return false;
					}else if(!isPathDirectory(ftpClient, currentDir))
						ftpClient.createDirectory(currentDir );

				}catch (FTPException e){
					if (e.getCode() == 550) return false;
				};

			}
		}
		return true;

	}

}
