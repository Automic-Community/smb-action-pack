package com.uc4.ara.feature.dirfilehandling;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.uc4.ara.feature.FeatureUtil;
import com.uc4.ara.feature.globalcodes.ErrorCodes;
import com.uc4.ara.feature.utils.FileUtil;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

public class CopySMB extends AbstractCopy {

	private static final String FILENAME_SPLITTER = "=::=";

	public CopySMB(String host, int port, String username, String password,
			String from, boolean recursive, String to, boolean overwrite, long timeout, boolean preserve, String smbDomainName) {
		super(host, port, username, password, from, recursive, to, overwrite,
				timeout, preserve, null, -1, null, null, null, smbDomainName);
	}

	@Override
	public int retrieve() throws IOException {

		String path = host;
		if (!path.toLowerCase().startsWith("smb://"))
			path = "smb://" + path;
		if (port != -1)
			path = path + ":" + port;

		from = from.replaceAll("\\\\", "/");
		from = FileUtil.normalize(from);
		if(!from.startsWith("/") ) from = "/" + from;

		if (from.length() > 3 && from.substring(1, 3).equals(":\\")) {
			path = path + from.substring(0, 1) + "$/" + from.substring(3);
		} else {
			path = path + from;
		}

		FeatureUtil.logMsg("Connecting to " + path + " ...");
		NtlmPasswordAuthentication ntlmPasswordAuthentication;

		// this trick to bypass the strange way jcifs.smb handles anonymous user, that it distinguishes between blank string ("") and null passed to username/password
		if (StringUtils.isBlank(username)) {
		    username = null;
		    password = null;
		}

		jcifs.Config.setProperty("jcifs.smb.client.connTimeout", Long.toString(timeout));
		jcifs.Config.setProperty("jcifs.resolveOrder", "DNS");
		//jcifs.Config.setProperty("jcifs.smb.client.dfs.disabled", "false");
		//jcifs.Config.setProperty("jcifs.util.loglevel", "3");
		ntlmPasswordAuthentication = new NtlmPasswordAuthentication(smbDomainName, username, password);

		try {
			File localFile = new File(to);
			createParentDir(localFile);

			SmbFile smbFrom = new SmbFile(path, ntlmPasswordAuthentication);
			if(from.contains("*") || from.contains("?")){
				deepRetrieveWildCard(path, localFile, ntlmPasswordAuthentication);
			} else if (smbFrom.isDirectory()) {
				if(!path.endsWith("/") )
					smbFrom = new SmbFile(path + "/", ntlmPasswordAuthentication);
				deepRetrieve(smbFrom, localFile);
			} else if(smbFrom.isFile()) {
				if(from.endsWith("/"))
					from = from.substring(0, from.length() - 1 );
				if(localFile.isDirectory())
					localFile = new File(localFile, from.substring(from.lastIndexOf("/")));

				singleFileRetrieve(smbFrom, localFile);
			}else{
				FeatureUtil.logMsg( FileUtil.normalize(path) + " does not exist. Please check the path again. Aborting ...");
				errorCode = ErrorCodes.ERROR;
			}
		} catch (SmbException e){
			FeatureUtil.logMsg(e.getMessage() + " Please check the host, port, username, password or domain name again. Aborting ...");
			return ErrorCodes.EXCEPTION;
		} catch (UserException e) {
			FeatureUtil.logMsg(e.getMessage() + " Aborting ...");
			return ErrorCodes.ERROR;
		}

		return errorCode;
	}

	private void deepRetrieveWildCard(String path, File localFile, NtlmPasswordAuthentication ntlmPasswordAuthentication )
			throws UserException, IOException {

		String srcRemote = FileUtil.getSourceWildCard(path);
		SmbFile smbFrom = new SmbFile(srcRemote, ntlmPasswordAuthentication);

		List<SmbFile> files = getFilesWildCard(smbFrom, path.substring(srcRemote.length() - 1), ntlmPasswordAuthentication);
		for (SmbFile file : files) {
			File currentLocal =  new File(localFile, file.getPath().substring(srcRemote.length()));
			createParentDir(currentLocal);

			if (file.isDirectory() && recursive) {
				deepRetrieve(file, currentLocal);
			} else if (file.isFile()){
				singleFileRetrieve(file, currentLocal);
			}
		}
	}

	private void deepRetrieve(SmbFile smbFrom, File localFile) throws IOException {

		if(localFile.isFile() ) {
			if(overwrite) {
				FeatureUtil.logMsg(localFile.getAbsolutePath() + " already exists!. Deleting ...");
				localFile.delete();
			} else {
				FeatureUtil.logMsg("CONFLICT:'" + localFile.getAbsolutePath() + "' is a FILE <=> '" +
						smbFrom.getCanonicalPath()  +"' is a DIRECTORY ( overwrite is NO ). Skipping ...");
				return;
			}
		}

		if(!localFile.exists() ) localFile.mkdir();

		SmbFile[] files = smbFrom.listFiles();
		for (SmbFile file : files) {

			File child = new File(localFile,file.getName());

			if (file.isDirectory() && recursive)
				deepRetrieve(file, child);

			else if (file.isFile())
				singleFileRetrieve(file, child);
		}
	}

	private void singleFileRetrieve(SmbFile file, File f) throws IOException {

		if(f.exists()){
			if(overwrite){
				FeatureUtil.logMsg(f.getCanonicalPath() + " already exists!. Deleting ...");
				if(f.isDirectory()) FileUtil.deleteDirectory(f);
				else f.delete();
			}else {
				FeatureUtil.logMsg(f.getCanonicalPath() + " already exists ( overwrite is NO ). Skipping ...");
				return;
			}
		}

		BufferedInputStream is = new BufferedInputStream(new SmbFileInputStream(file));
		BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(f));

		FeatureUtil.logMsg("Copying " + getFileRelativizePath(f.toPath(), new File(to).toPath()) + FILENAME_SPLITTER + "'"  + file.getPath() + "' => '" + f.getCanonicalPath() + "'");
		try {
			byte[] content = new byte[MAX_BUFFER_SIZE];
			int read, total = 0;
			long t0 = System.currentTimeMillis();
			while ((read = is.read(content)) > 0) {
				fos.write(content, 0, read);
				total += read;
			}
			fos.flush();
			long t = System.currentTimeMillis() - t0;
			if (preserve) {
				f.setLastModified(file.getLastModified());
				f.setReadable(file.canRead());
				f.setWritable(file.canWrite());

				if (file.isHidden() && System.getProperty("os.name").toLowerCase().trim().contains("windows"))
					Runtime.getRuntime().exec("attrib +H " + f.getCanonicalPath());
			}
		} finally {
			is.close();
			fos.close();
		}
	}

	private String getFileRelativizePath(Path file, Path destination) {
		return destination.relativize(file).toString().replace('\\', '/');
	}

	/**Get all files match the wildcard from smb server
	 * @param smbFrom
	 * @param path
	 * 			Path contains wildcard
	 * @param ntlmPasswordAuthentication
	 * @return
	 * @throws SmbException
	 * @throws MalformedURLException
	 */
	private List<SmbFile> getFilesWildCard(SmbFile smbFrom, String path, NtlmPasswordAuthentication ntlmPasswordAuthentication )
			throws SmbException, MalformedURLException {
		String[] dirs = path.split("/+");
		List<SmbFile> listFiles = new ArrayList<SmbFile>();

		for (String dir : dirs){
			if(!dir.equals("")){
				listFiles.addAll(Arrays.asList(smbFrom.listFiles(dir)));
				break;
			}
		}

		return listFiles;
	}


	@Override
	public int store() throws Exception {
		NtlmPasswordAuthentication ntlmPasswordAuthentication = new NtlmPasswordAuthentication(
				smbDomainName, username, password);

		String path = host;
		if (!path.toLowerCase().startsWith("smb://"))
			path = "smb://" + path;
		if (port != -1)
			path = path + ":" + port;

		to = to.replaceAll("\\\\", "/");
		to = FileUtil.normalize(to);
		if(! to.startsWith("/") ) to = "/" + to;
		if (to.length() > 3 && to.substring(1, 3).equals(":\\")) {
			path = path + to.substring(0, 1) + "$/" + to.substring(3);
		} else
			path = path + to;

		FeatureUtil.logMsg("Connecting to " + path + " ...");
		jcifs.Config.setProperty("jcifs.smb.client.connTimeout", Long.toString(timeout));

		File localFile = new File(from);

		try {
			SmbFile remoteFile = new SmbFile(path, ntlmPasswordAuthentication);

			//Make sure the parent directory exists
			SmbFile prFile = new SmbFile(remoteFile.getParent(), ntlmPasswordAuthentication);
			if(!prFile.exists() ) prFile.mkdirs();
			if(! prFile.isDirectory())
				FeatureUtil.logMsg("Remote Directory " + prFile.getCanonicalPath()  + " can't be created. Please check the path again. Aborting ...");

			if (localFile.isDirectory())
				deepStore(ntlmPasswordAuthentication, localFile, remoteFile);

			else if(localFile.isFile()) {
				if(remoteFile.isDirectory())
					remoteFile = new SmbFile(path + "/" + localFile.getName(), ntlmPasswordAuthentication);

				singleFileStore(localFile, remoteFile);
			} else {
				FeatureUtil.logMsg( localFile.getCanonicalPath() + " does not exist. Aborting ...");
				errorCode = ErrorCodes.ERROR;
			}

		} catch (SmbException e){
			FeatureUtil.logMsg(e.getMessage() + " Please check the host, port, username or password or domain name again. Aborting ...");
			return ErrorCodes.EXCEPTION;
		}

		return ErrorCodes.OK;
	}

	private void deepStore(NtlmPasswordAuthentication ntlmPasswordAuthentication, File localFile, SmbFile rFile) throws IOException{

		if(rFile.isFile()) {
			if(overwrite) {
				FeatureUtil.logMsg(rFile.getCanonicalPath() + " already exists!. Deleting ...");
				rFile.delete();
			} else {
				FeatureUtil.logMsg("CONFLICT:'" + rFile.getCanonicalPath() + "' is a FILE <=> '" +
						localFile.getAbsolutePath()  +"' is a DIRECTORY ( overwrite is NO ). Skipping ...");
				return;
			}
		}

		if(!rFile.exists() ) rFile.mkdir();

		for (File file : localFile.listFiles()) {
			SmbFile child = new SmbFile(rFile.getCanonicalPath() + "/" + file.getName(), ntlmPasswordAuthentication );

			if (file.isDirectory() && recursive)
				deepStore(ntlmPasswordAuthentication, file, child );

			else if(file.isFile())
				singleFileStore(file, child);
		}
	}

	private void singleFileStore(File localFile, SmbFile toFile) throws IOException {

		if(toFile.exists()){
			if(overwrite){
				FeatureUtil.logMsg(toFile.getCanonicalPath() + " already exists!. Deleting ...");
				toFile.delete();
			}else {
				FeatureUtil.logMsg(toFile.getCanonicalPath() + " already exists ( overwrite is NO ). Skipping ...");
				return;
			}
		}

		FeatureUtil.logMsg("Copying '" + localFile.getCanonicalPath() + "' => '" + toFile.getPath() + "'");

		InputStream is = new BufferedInputStream(new FileInputStream(localFile));
		OutputStream fos = new BufferedOutputStream(new SmbFileOutputStream(toFile));

		try {
			int bufferSize = MAX_BUFFER_SIZE/8;
			byte[] content = new byte[bufferSize];
			int read;
			while ((read = is.read(content)) > 0) {
				fos.write(content, 0, read);
			}

			if (preserve)
				toFile.setLastModified(localFile.lastModified());

		} finally {
			is.close();
			fos.close();
		}
	}

}
