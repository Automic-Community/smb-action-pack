package com.uc4.ara.feature.dirfilehandling;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.jcraft.jsch.SftpException;
import com.uc4.ara.feature.FeatureUtil;
import com.uc4.ara.feature.globalcodes.ErrorCodes;
import com.uc4.ara.feature.utils.FileUtil;
import com.uc4.ara.feature.utils.ScpException;
import com.uc4.ara.feature.utils.SftpWrapper;

public class CopySFTP extends AbstractCopy {

	private String path;

	public CopySFTP(String host, int port, String username, String password,
			String from, boolean recursive, String to, boolean overwrite,
			long timeout, boolean preserve) {
		super(host, port, username, password, from, recursive, to, overwrite,
				timeout, preserve,  null, -1, null, null, null, null);
	}

	@Override
	public int retrieve() {

		from = from.replaceAll("\\\\", "/");
		from = FileUtil.normalize(from);
		if (port == -1) port = 22;

		SftpWrapper sftpWrapper = new SftpWrapper(host, port, username, password);
		sftpWrapper.setTimeOut(timeout);

		path = sftpWrapper.getRemoteUsername() + "@"+ sftpWrapper.getRemoteHost() + ":";
		FeatureUtil.logMsg("Connecting to " + path + sftpWrapper.getRemotePort() + " ...");

		try {
			sftpWrapper.openSession();
			sftpWrapper.openSftpChannel();
		} catch (ScpException e) {
			FeatureUtil.logMsg("Message: " + e.getMessage()
					+ ". Session can not be opened. Maybe the host, port, username or password is incorrect. Aborting ... ");
			return ErrorCodes.EXCEPTION;
		}

		File localFile = new File(to);

		try {
			createParentDir(localFile);

			if(from.contains("*") || from.contains("?"))
				deepRetrieveWildCard(sftpWrapper, localFile);

			else if (sftpWrapper.isDirectory(from))
				deepRetrieve(sftpWrapper, from + "/", localFile);

			else if(sftpWrapper.isFile(from)) {
				if(from.endsWith("/"))
					from = from.substring(0, from.length() - 1 );
				if(localFile.isDirectory())
					localFile = new File(localFile, from.substring(from.lastIndexOf("/"), from.length()));

				singleFileRetrieve(sftpWrapper, from, localFile);

			} else {
				FeatureUtil.logMsg("'" + path + FileUtil.normalize(from) + "' does not exist. Aborting ...");
				errorCode = ErrorCodes.ERROR;
			}

		} catch (SftpException e){
			FeatureUtil.logMsg(e.getMessage() + ". Error with the SFTP connection. Aborting ...");
			return ErrorCodes.EXCEPTION;

		} catch (IOException e){
			FeatureUtil.logMsg(e.getMessage() + ". Connection can not continue. Aborting ...");
			return ErrorCodes.EXCEPTION;

		}catch (UserException e) {
			FeatureUtil.logMsg(e.getMessage() + ". Aborting ...");
			return ErrorCodes.ERROR;

		}finally {
			sftpWrapper.closeSftpChannel();
			if (sftpWrapper != null)
				sftpWrapper.closeSession();
		}

		return errorCode;
	}

	private void deepRetrieve(SftpWrapper sftpWrapper, String from, File localFile) throws IOException, SftpException {

		List<String> files = new ArrayList<String>();

		if(localFile.isFile() ) {
			if(overwrite) {
				FeatureUtil.logMsg("'" + localFile.getAbsolutePath() + "' already exists. Deleting ...");
				localFile.delete();
			} else {
				FeatureUtil.logMsg("CONFLICT:'" + localFile.getAbsolutePath() + "' is a FILE <=> '" +
						path + FileUtil.normalize(from ) +"' is a DIRECTORY ( overwrite is NO ). Skipping ...");
				return;
			}
		}

		if(!localFile.exists() ) localFile.mkdir();

		files = sftpWrapper.listFile(from);

		for(String file : files) {
			if(sftpWrapper.isDirectory(from + file)) {
				if (recursive) {
					deepRetrieve(sftpWrapper, from + file + "/", new File(
							localFile, file));
				}
			} else
				singleFileRetrieve(sftpWrapper, from + file, new File(localFile, file));
		}

	}

	private void deepRetrieveWildCard(SftpWrapper sftpWrapper, File localFile)
			throws IOException, SftpException, UserException {
		String srcRemote = FileUtil.getSourceWildCard(from);

		List<String> files = getFilesWildCard(sftpWrapper,"", from);

		for (String file : files) {
			File currentLocal =  new File(localFile, file.substring(srcRemote.length()));
			createParentDir(currentLocal);

			if ( sftpWrapper.isDirectory(file)) {
				if (recursive) {
					deepRetrieve(sftpWrapper, file + "/", currentLocal);
				}
			} else
				singleFileRetrieve(sftpWrapper, file, currentLocal);
		}
	}

	private void singleFileRetrieve(SftpWrapper sftpWrapper, String from, File f) throws IOException, SftpException {

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

		FeatureUtil.logMsg("Copying '" + path + FileUtil.normalize(from) + "' => '" + f.getAbsolutePath() + "'");

		sftpWrapper.readFile(from, f.getAbsolutePath(), preserve);

	}

	private List<String> getFilesWildCard(SftpWrapper sftpWrapper, String currentPath, String nextPath)
			throws SftpException {

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
						if(sftpWrapper.isExisted(currentPath))
							listFiles.add(currentPath);

					} else if(! sftpWrapper.isDirectory(currentPath)) break;

				}else {
					for(String file : sftpWrapper.listFile(currentPath + "/" + dir)) {
						if(count == dirs.length)
							listFiles.add(currentPath + "/" + file);
						else if(sftpWrapper.isDirectory(currentPath + "/" + file))
							listFiles.addAll(getFilesWildCard(sftpWrapper, currentPath + "/" + file, nextPath));
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
		if (port == -1)
			port = 22;
		SftpWrapper sftpWrapper = new SftpWrapper(host, port, username, password);

		path = sftpWrapper.getRemoteUsername() + "@"+ sftpWrapper.getRemoteHost() + ":";
		FeatureUtil.logMsg("Connecting to " + path + sftpWrapper.getRemotePort() + " ...");

		try {
			sftpWrapper.openSession();
			sftpWrapper.openSftpChannel();
		} catch (ScpException e) {
			FeatureUtil.logMsg("Message: " + e.getMessage()
					+ ". Session can not be opened. Maybe the host, port, username or password is incorrect. Aborting ... ");
			return ErrorCodes.EXCEPTION;
		}
		//Create parent directory
		String parent = FileUtil.normalize(to + "/../");
		sftpWrapper.createDirectoryRecursive(parent);

		File localFile = new File(from);

		try{
			if (localFile.isDirectory())
				deepStore(sftpWrapper, localFile, to);

			else if(localFile.isFile()) {

				if(sftpWrapper.isDirectory(to))
					to = to + "/" + localFile.getName();

				singleFileStore(sftpWrapper, localFile, to);

			} else {
				FeatureUtil.logMsg(localFile.getAbsolutePath() + " does not exists. Please check the path again. Aborting ...");
				errorCode = ErrorCodes.ERROR;
			}

		} catch (SftpException e){
			FeatureUtil.logMsg(e.getMessage() + ". Error with the SFTP connection. Aborting ...");
			errorCode = ErrorCodes.EXCEPTION;

		} finally {
			sftpWrapper.closeSftpChannel();
			if (sftpWrapper != null)
				sftpWrapper.closeSession();
		}

		return errorCode;
	}

	private void deepStore(SftpWrapper sftpWrapper, File localFile, String to) throws IOException, SftpException {

		if(sftpWrapper.isFile(to)) {
			if(overwrite) {
				FeatureUtil.logMsg("'" + path + FileUtil.normalize(to) + "' already exists!. Deleting ...");
				sftpWrapper.removeFile(to);
			} else {
				FeatureUtil.logMsg("CONFLICT:'" + path + FileUtil.normalize(to) + "' is a FILE <=> '" +
						localFile.getAbsolutePath()  +"' is a DIRECTORY ( overwrite is NO ). Skipping ...");
				return;
			}

		}
		sftpWrapper.createDirectory(to);

		for (File file : localFile.listFiles()) {

			if (file.isDirectory() && recursive)
				deepStore(sftpWrapper, file, to + "/" + file.getName() + "/");

			else if(file.isFile()) singleFileStore(sftpWrapper, file, to + "/" +file.getName());
		}

	}

	private void singleFileStore(SftpWrapper sftpWrapper, File localFile, String to)	throws IOException, SftpException {

		if(sftpWrapper.isExisted(to)){
			if(overwrite){
				FeatureUtil.logMsg("'" + path + FileUtil.normalize(to) + "' already exists. Deleting ...");
				sftpWrapper.removeFile(to);
			}else {
				FeatureUtil.logMsg("'" + path + FileUtil.normalize(to) + "' already exists ( overwrite is NO ). Skipping ...");
				return;
			}
		}

		FeatureUtil.logMsg("Copying '"+ localFile.getAbsolutePath() + "' => '" + path + FileUtil.normalize(to)  + "'");

		sftpWrapper.writeFile(localFile.getAbsolutePath(), to);

	}

}
