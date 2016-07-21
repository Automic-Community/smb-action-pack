package com.uc4.ara.feature.dirfilehandling;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.jcraft.jsch.SftpException;
import com.uc4.ara.feature.FeatureUtil;
import com.uc4.ara.feature.globalcodes.ErrorCodes;
import com.uc4.ara.feature.utils.FileUtil;
import com.uc4.ara.feature.utils.ScpException;
import com.uc4.ara.feature.utils.ScpWrapper;

public class CopySCP extends AbstractCopy {

	private String path;

	public CopySCP(String host, int port, String username, String password,
			String from, boolean recursive, String to, boolean overwrite,
			long timeout, boolean preserve) {
		super(host, port, username, password, from, recursive, to, overwrite,
				timeout, preserve,  null, -1, null, null, null, null);
	}

	@Override
	public int retrieve() {

		from = from.trim();
		from = from.replaceAll("\\\\", "/");
		from = FileUtil.normalize(from);
		if (port == -1) port = 22;

		ScpWrapper scpWrapper = new ScpWrapper(host, port, username, password);
		scpWrapper.setTimeOut(timeout);

		path = scpWrapper.getRemoteUsername() + "@"+ scpWrapper.getRemoteHost() + ":";
		FeatureUtil.logMsg("Connecting to " + path + scpWrapper.getRemotePort() + " ...");

		try {
			scpWrapper.openSession();
		} catch (ScpException e) {
			FeatureUtil.logMsg("Message: " + e.getMessage()
					+ ". Session can not be opened. Maybe the host, port, username or password is incorrect. Aborting ... ");
			return ErrorCodes.EXCEPTION;
		}

		File localFile = new File(to);

		try {
			createParentDir(localFile);

			if(from.contains("*") || from.contains("?")) {
				String srcRemote = FileUtil.getSourceWildCard(from);
				if ( scpWrapper.isExisted(srcRemote) ) {
					deepRetrieve(scpWrapper, from , localFile);
				} else {
					FeatureUtil.logMsg("'" + path + FileUtil.normalize(from) + "' does not exist. Aborting ...");
					errorCode = ErrorCodes.ERROR;
				}

			} else if (scpWrapper.isDirectory(from)) {
				deepRetrieve(scpWrapper, from + "/", localFile);

			} else if(scpWrapper.isFile(from)) {
				if(from.endsWith("/"))
					from = from.substring(0, from.length() - 1 );
				if(localFile.isDirectory())
					localFile = new File(localFile, from.substring(from.lastIndexOf("/"), from.length()));

				singleFileRetrieve(scpWrapper, from, localFile);

			} else {
				FeatureUtil.logMsg("'" + path + FileUtil.normalize(from) + "' does not exist. Aborting ...");
				errorCode = ErrorCodes.ERROR;
			}

		} catch (IOException e){
			FeatureUtil.logMsg(e.getMessage() + ". Connection can not continue. Aborting ...");
			return ErrorCodes.EXCEPTION;
		} catch (UserException e) {
			FeatureUtil.logMsg(e.getMessage() + ". Aborting ...");
			return ErrorCodes.ERROR;
		}

		finally {

			if (scpWrapper != null)
				scpWrapper.closeSession();
		}

		return errorCode;
	}

	private void deepRetrieve(ScpWrapper scpWrapper, String from, File localFile) throws IOException {

		if(!localFile.exists() ) localFile.mkdir();

		List<String> excludedItems = new ArrayList<String>();
		Set<String> dirs = new HashSet<String>(scpWrapper.listFileRecursive(from,  ScpWrapper.DIRECTORY));
		for (String dir : dirs){
			File currentDir = new File(localFile, dir);

			if(currentDir.isFile() ) {
				if(overwrite) {
					FeatureUtil.logMsg("'" + currentDir.getAbsolutePath() + "' already exists. Deleting ...");
					currentDir.delete();
				} else {
					FeatureUtil.logMsg("CONFLICT:'" + currentDir.getAbsolutePath() + "' is FILE <=> '" +
							path + FileUtil.normalize(from + dir) +"' is DIRECTORY ( overwrite is NO ). Skipping ...");
					excludedItems.add(currentDir.getCanonicalPath());
					continue;
				}
			}

			if(!currentDir.exists() && recursive )
				currentDir.mkdirs();
		}

		String srcRemote = FileUtil.getSourceWildCard(from);
		Set<String> files;
		if (recursive) {
			files = new HashSet<String>(scpWrapper.listFileRecursive(from,  ScpWrapper.FILE));
		} else {
			files = new HashSet<String>(scpWrapper.listFiles(from, ScpWrapper.FILE));
		}
		loop:for (String file : files){

			for(String item : excludedItems)
				if(file.startsWith(item)) continue loop;

			singleFileRetrieve(scpWrapper, srcRemote + file, new File(localFile, file));
		}

	}

	private void singleFileRetrieve(ScpWrapper scpWrapper, String from, File f) throws IOException {

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

		if (preserve)
			scpWrapper.readFilePreserv(from, f.getAbsolutePath());
		else
			scpWrapper.readFile(from, f.getAbsolutePath());

	}

	@Override
	public int store() throws Exception {

		to = to.replaceAll("\\\\", "/");
		to = FileUtil.normalize(to);
		if (port == -1)
			port = 22;
		ScpWrapper scpWrapper = new ScpWrapper(host, port, username, password);

		path = scpWrapper.getRemoteUsername() + "@"+ scpWrapper.getRemoteHost() + ":";
		FeatureUtil.logMsg("Connecting to " + path + scpWrapper.getRemotePort() + " ...");

		try {
			scpWrapper.openSession();
		} catch (ScpException e) {
			FeatureUtil.logMsg("Message: " + e.getMessage()
					+ ". Session can not be opened. Maybe the host, port, username or password is incorrect. Aborting ... ");
			return ErrorCodes.EXCEPTION;
		}

		File localFile = new File(from);

		//Create parent directory
		String parent = FileUtil.normalize(to + "/../");
		scpWrapper.createDirectory(parent);
		if(!scpWrapper.isDirectory(parent)){
			FeatureUtil.logMsg("Remote Directory '" + parent  +
					"' is not available or can't be created. Please check the path again. Aborting ...");
			return ErrorCodes.ERROR;
		}

		try{
			if (localFile.isDirectory())
				deepStore(scpWrapper, localFile, to);

			else if(localFile.isFile()){

				if(scpWrapper.isDirectory(to))
					to = to + "/" + localFile.getName();
				singleFileStore(scpWrapper, localFile, to);

			} else {
				FeatureUtil.logMsg(localFile.getAbsolutePath() + " does not exists. Please check the path again. Aborting ...");
				errorCode = ErrorCodes.ERROR;
			}

		} catch (SftpException e){
			FeatureUtil.logMsg(e.getMessage() + ". Error with the SFTP connection. Aborting ...");
			errorCode = ErrorCodes.EXCEPTION;

		} finally {
			if (scpWrapper != null)
				scpWrapper.closeSession();
		}

		return errorCode;
	}

	private void deepStore(ScpWrapper scpWrapper, File localFile, String to) throws IOException, SftpException {

		if(scpWrapper.isFile(to)) {
			if(overwrite) {
				FeatureUtil.logMsg("'" + path + FileUtil.normalize(to) + "' already exists. Deleting ...");
				scpWrapper.removeFile(to);
			} else {
				FeatureUtil.logMsg("CONFLICT:'" + path + FileUtil.normalize(to) + "' is FILE <=> '" +
						localFile.getAbsolutePath()  +"' is DIRECTORY ( overwrite is NO ). Skipping ...");
				return;
			}
		}

		scpWrapper.createDirectory(to);

		for (File file : localFile.listFiles()) {

			if (file.isDirectory() && recursive)
				deepStore(scpWrapper, file, to + "/" + file.getName() + "/");

			else if(file.isFile()) singleFileStore(scpWrapper, file, to + "/" +file.getName());
		}

	}

	private void singleFileStore(ScpWrapper scpWrapper, File localFile, String to)	throws IOException, SftpException {

		if(scpWrapper.isExisted(to)){
			if(overwrite){
				FeatureUtil.logMsg("'" + path + FileUtil.normalize(to) + "' already exists. Deleting ...");
				scpWrapper.removeFile(to);
			}else {
				FeatureUtil.logMsg("'" + path + FileUtil.normalize(to) + "' already exists ( overwrite is NO ). Skipping ...");
				return;
			}
		}

		FeatureUtil.logMsg("Copying '"+ localFile.getAbsolutePath() + "' => '" + path + FileUtil.normalize(to)  + "'");

		scpWrapper.writeFile(localFile.getAbsolutePath(), to);

	}

}
