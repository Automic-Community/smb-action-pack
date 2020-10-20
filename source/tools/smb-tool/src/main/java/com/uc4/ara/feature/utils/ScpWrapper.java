
package com.uc4.ara.feature.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedList;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import com.uc4.ara.feature.FeatureUtil;

/**
 * The Class ScpWrapper is a wrapper for jcraft and offers methods to access a
 * remote machine via ssh or scp.
 *
 * @see http://www.jcraft.com/jsch/.
 */
public class ScpWrapper {

	/**
	 * The remote host.
	 */
	private String remoteHost = null;

	/**
	 * The remote port.
	 */
	private int remotePort = 22;

	/**
	 * The remote username.
	 */
	private String remoteUsername = "root";

	/**
	 * The remote passwd.
	 */
	private String remotePasswd = "";


	private String certificate = "";
	/**
	 * The session to the remote machine. Each session supports several
	 * channels.
	 */
	private Session session;

	private long timeout;
	/**
	 * Instantiates a new scp wrapper.
	 */

	public static final int ALL = 0;
	public static final int DIRECTORY = 1;
	public static final int FILE = 2;

	public ScpWrapper() {
	}

	/**
	 * Instantiates a new scp wrapper with convenient parameter settings.
	 *
	 * @param remoteHost
	 *            the remote host
	 * @param remotePort
	 *            the remote port
	 * @param remoteUsername
	 *            the remote username
	 * @param remotePasswd
	 *            the remote passwd
	 */
	public ScpWrapper(String remoteHost, int remotePort, String remoteUsername,
			String remotePasswd) {
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
		this.remoteUsername = remoteUsername;
		this.remotePasswd = remotePasswd;
	}

	public ScpWrapper(String remoteHost, int remotePort, String remoteUsername,
			String remotePasswd, String certificate) {
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
		this.remoteUsername = remoteUsername;
		this.remotePasswd = remotePasswd;
		this.certificate = certificate;
	}

	/**
	 * Gets the remote username.
	 *
	 * @return the remote username
	 */
	public String getRemoteUsername() {
		return remoteUsername;
	}

	/**
	 * Sets the remote username.
	 *
	 * @param username
	 *            the new remote username
	 */
	public void setRemoteUsername(String username) {
		this.remoteUsername = username;
	}

	/**
	 * Gets the remote port.
	 *
	 * @return the remote port
	 */
	public int getRemotePort() {
		return remotePort;
	}

	/**
	 * Sets the remote port.
	 *
	 * @param port
	 *            the new remote port
	 */
	public void setRemotePort(int port) {
		this.remotePort = port;
	}



	/**
	 * Gets the remote passwd.
	 *
	 * @return the remote passwd
	 */
	public String getRemotePasswd() {
		return remotePasswd;
	}

	/**
	 * Sets the remote passwd.
	 *
	 * @param passwd
	 *            the new remote passwd
	 */
	public void setRemotePasswd(String passwd) {
		this.remotePasswd = passwd;
	}

	/**
	 * Gets the remote host.
	 *
	 * @return the remote host
	 */
	public String getRemoteHost() {
		return remoteHost;
	}

	/**
	 * Sets the remote host.
	 *
	 * @param host
	 *            the new remote host
	 */
	public void setRemoteHost(String host) {
		this.remoteHost = host;
	}

	public void setTimeOut(long timeout){
		this.timeout = timeout;
	}
	/**
	 * Opens a session to the remote machine. Always call this method before
	 * executing any command on the remote machine. Do not forget to close the
	 * session after finishing the job.
	 *
	 * @return the session
	 * @throws com.uc4.ara.feature.utils.ScpException
	 *             the scp exception
	 */
	public Session openSession() throws ScpException {
		if (session != null)
			return session;

		try {
			JSch jsch = new JSch();

			if(certificate.length() > 0)
				jsch.addIdentity(certificate);

			session = jsch.getSession(remoteUsername, remoteHost, remotePort);
                        session.setConfig("PreferredAuthentications","publickey,keyboard-interactive,password");
			MyUserInfo ui = new MyUserInfo();

			if(remotePasswd.length() > 0)
				ui.setPassword(remotePasswd);

			session.setUserInfo(ui);
			session.setTimeout((int)timeout);
			session.connect();

			return session;
		} catch (JSchException e) {
			session = null;
			throw new ScpException(e.getLocalizedMessage());
		}
	}

	/**
	 * Closes the session to the remote machine. Do not forget to call this
	 * method in case of errors or when finishing the job.
	 */
	public void closeSession() {
		session.disconnect();
		session = null;
	}


	/**
	 * Reads a file from a remote machine and copies it to a file on the local
	 * machine.
	 *
	 * @param remoteFile
	 *            the remote file
	 * @param localFile
	 *            the local file
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public void readFile(String remoteFile, String localFile)
			throws IOException {
		try {
			readFileInternal(remoteFile, localFile);
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}


	/**
	 * Reads a remote file and copies it to the local machine.
	 *
	 * @param remoteFile
	 *            the remote file
	 * @param localFile
	 *            the local file
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws com.jcraft.jsch.JSchException
	 *             the j sch exception
	 */
	private void readFileInternal(String remoteFile, String localFile)
			throws IOException, JSchException {

		// add -p to preserve meta-data (modification time)
		String command = "scp -f " + remoteFile;
		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand(command);
		// get I/O streams for remote scp
		OutputStream out = channel.getOutputStream();
		InputStream in = channel.getInputStream();

		channel.connect();

		byte[] buf = new byte[1024];

		// send '\0'
		buf[0] = 0;
		out.write(buf, 0, 1);
		out.flush();

		while (true) {
			int c = checkAck(in);
			if (c != 'C') {
				break;
			}

			// read '0644 '
			int sum = 0;
			while (true) {
				int read = in.read(buf, 0, 5);
				if (read == -1) // error
					break;
				sum += read;
				if (sum == 5)
					break;
			}

			long filesize = 0L;
			while (true) {
				if (in.read(buf, 0, 1) < 0) {
					// error
					break;
				}
				if (buf[0] == ' ')
					break;
				filesize = filesize * 10L + buf[0] - '0';
			}

			String file = null;
			for (int i = 0;; i++) {
				while (true) {
					int read = in.read(buf, i, 1);
					if (read != 0)
						break;
				}
				if (buf[i] == (byte) 0x0a) {
					file = new String(buf, 0, i);
					break;
				}
			}

			//FeatureUtil.logMsg("filesize=" + filesize + ", file=" + file);

			// send '\0'
			buf[0] = 0;
			out.write(buf, 0, 1);
			out.flush();

			// read a content of lfile
			FileOutputStream fos = new FileOutputStream(localFile);
			int foo;
			while (true) {
				if (buf.length < filesize)
					foo = buf.length;
				else
					foo = (int) filesize;
				foo = in.read(buf, 0, foo);
				if (foo < 0) {
					// error
					break;
				}
				fos.write(buf, 0, foo);
				filesize -= foo;
				if (filesize == 0L)
					break;
			}
			fos.close();
			fos = null;

			if (checkAck(in) != 0) {
				throw new ScpException("Check ack failed");
			}

			// send '\0'
			buf[0] = 0;
			out.write(buf, 0, 1);
			out.flush();
		}

		channel.disconnect();
	}

	/**
	 * Reads a remote file to a local file and preserves the meta-information.
	 *
	 * @param remoteFile
	 *            the remote file
	 * @param localFile
	 *            the local file
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public void readFilePreserv(String remoteFile, String localFile)
			throws IOException {
		try {
			readFilePreserveInternal(remoteFile, localFile);
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}

	private void readFilePreserveInternal(String remoteFile, String localFile)
			throws IOException, JSchException {

		String command = "scp -p -f " + remoteFile;
		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand(command);
		// get I/O streams for remote scp
		OutputStream out = channel.getOutputStream();
		InputStream in = channel.getInputStream();

		channel.connect();

		byte[] buf = new byte[1024];

		// send '\0'
		buf[0] = 0;
		out.write(buf, 0, 1);
		out.flush();

		while (true) {
			int c = checkAck(in);
			if (c != 'T') {
				break;
			}

			long modtime = 0L;
			while (true) {
				if (in.read(buf, 0, 1) < 0) {
					// error
					break;
				}
				if (buf[0] == ' ')
					break;
				modtime = modtime * 10L + buf[0] - '0';
			}

			in.read(buf, 0, 2);
			long acctime = 0L;
			while (true) {
				if (in.read(buf, 0, 1) < 0) {
					// error
					break;
				}
				if (buf[0] == ' ')
					break;
				acctime = acctime * 10L + buf[0] - '0';
			}

			// send '\0'
			buf[0] = 0;
			out.write(buf, 0, 1);
			out.flush();

			while (true) {
				c = checkAck(in);
				if (c == 'C') {
					break;
				}
			}

			// read '0644 '
			int sum = 0;
			while (true) {
				int read = in.read(buf, 0, 5);
				if (read == -1) // error
					break;
				sum += read;
				if (sum == 5)
					break;
			}

			long filesize = 0L;
			while (true) {
				if (in.read(buf, 0, 1) < 0) {
					// error
					break;
				}
				if (buf[0] == ' ')
					break;
				filesize = filesize * 10L + buf[0] - '0';
			}

			for (int i = 0;; i++) {
				while (true) {
					int read = in.read(buf, i, 1);
					if (read != 0)
						break;
				}
				if (buf[i] == (byte) 0x0a) {

					break;
				}
			}


			// send '\0'
			buf[0] = 0;
			out.write(buf, 0, 1);
			out.flush();

			// read a content of lfile
			FileOutputStream fos = new FileOutputStream(localFile);
			int foo;
			while (true) {
				if (buf.length < filesize)
					foo = buf.length;
				else
					foo = (int) filesize;
				foo = in.read(buf, 0, foo);
				if (foo < 0) {
					// error
					break;
				}
				fos.write(buf, 0, foo);
				filesize -= foo;
				if (filesize == 0L)
					break;
			}
			fos.close();
			fos = null;

			if (checkAck(in) != 0) {
				throw new ScpException("Check ack failed");
			}

			// send '\0'
			buf[0] = 0;
			out.write(buf, 0, 1);
			out.flush();

			// set the meta data
			File tempfile = new File(localFile);
			tempfile.setLastModified(modtime * 1000);

		}

		channel.disconnect();
	}



	/**
	 * Writes a file to the remote machine by reading it from the local machine.
	 *
	 * @param localFile
	 *            the local file
	 * @param remoteFile
	 *            the remote file
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public void writeFile(String localFile, String remoteFile)
			throws IOException {
		try {
			InputStream fis = new FileInputStream(localFile);
			File lfile = new File(localFile);
			String chmod = "C0644";
			if (lfile.canExecute())
				chmod = "C0755";
			long filesize = (lfile).length();
			writeFileInternal(fis, lfile.lastModified(), chmod, filesize,
					remoteFile);
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}


	/**
	 * Writes a file to the remote machine by reading the file contents from the
	 * parameter <code>inputStream</code>.
	 *
	 * @param inputStream
	 *            the input stream
	 * @param lastModified
	 *            the last modified
	 * @param chmod
	 *            the chmod
	 * @param filesize
	 *            the filesize
	 * @param remoteFile
	 *            the remote file
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public void writeFile(InputStream inputStream, long lastModified,
			String chmod, long filesize, String remoteFile) throws IOException {
		try {
			writeFileInternal(inputStream, lastModified, chmod, filesize,
					remoteFile);
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}

	/**
	 * Writes a local file to the remote machine.
	 *
	 * @param fis
	 *            the fis
	 * @param lastModified
	 *            the last modified
	 * @param chmod
	 *            the chmod
	 * @param filesize
	 *            the filesize
	 * @param remoteFile
	 *            the remote file
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws com.jcraft.jsch.JSchException
	 *             the j sch exception
	 */
	private void writeFileInternal(InputStream fis, long lastModified,
			String chmod, long filesize, String remoteFile) throws IOException,
			JSchException {

		String command = "scp -p -t " + remoteFile;
		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand(command);

		// get I/O streams for remote scp
		OutputStream out = channel.getOutputStream();
		InputStream in = channel.getInputStream();

		channel.connect();

		if (checkAck(in) != 0) {
			throw new ScpException("Check ack failed");
		}

		command = "T " + (lastModified / 1000) + " 0";
		// The access time should be sent here,
		// but it is not accessible with JavaAPI ;-<
		command += (" " + (lastModified / 1000) + " 0\n");
		out.write(command.getBytes());
		out.flush();
		if (checkAck(in) != 0) {
			throw new ScpException("Check ack failed");
		}

		// send "C0644 filesize filename", where filename should not include
		// '/'
		command = chmod + " " + filesize + " ";
		if (remoteFile.lastIndexOf('/') > 0) {
			command += remoteFile.substring(remoteFile.lastIndexOf('/') + 1);
		} else {
			command += remoteFile;
		}
		command += "\n";
		out.write(command.getBytes());
		out.flush();
		if (checkAck(in) != 0) {
			throw new ScpException("Check ack failed");
		}

		// send a content of lfile
		byte[] buf = new byte[1024];
		while (true) {
			int len = fis.read(buf, 0, buf.length);
			if (len <= 0)
				break;
			out.write(buf, 0, len); // out.flush();
		}
		fis.close();
		fis = null;
		// send '\0'
		buf[0] = 0;
		out.write(buf, 0, 1);
		out.flush();
		if (checkAck(in) != 0) {
			throw new ScpException("Check ack failed");
		}

		// StringBuffer error = new StringBuffer();
		// while (true) {
		// err.read() hangs infinitely
		// int res = err.read();
		// if (res == -1)
		// break;
		// error.append((char) res);
		// System.out.println("error: " + error.toString());
		// }

		out.close();
		channel.disconnect();
		// hmm, we do always get exitstatus != 0. Seems there is a reason why
		// jsch has not checked the exitstatus in their examples
		// int exitCode = channel.getExitStatus();
		// if (exitCode != 0)
		// throw new ScpException("Writing local file '" + localFile
		// + "' to '" + remoteHost + ":" + remoteFile + "' failed");
	}


	/**
	 * Removes a file from the remote machine.
	 *
	 * @param remoteFile
	 *            the remote file
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public void removeFile(String remoteFile) throws IOException {
		try {
			removeFileInternal(remoteFile);
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}

	/**
	 * Removes a file from the remote machine.
	 *
	 * @param remoteFile
	 *            the remote file
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws com.jcraft.jsch.JSchException
	 *             the j sch exception
	 */
	private void removeFileInternal(String remoteFile) throws IOException,
	JSchException {

		String command = "rm '" + remoteFile + "'";
		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand(command);

		// get I/O streams for remote scp
		OutputStream out = channel.getOutputStream();
		// InputStream in = channel.getInputStream();
		channel.connect();

		byte[] buf = new byte[1024];

		// send '\0'
		buf[0] = 0;
		out.write(buf, 0, 1);
		out.flush();

		int exitCode = 0;
		while (true) {
			if (channel.isClosed()) {
				exitCode = channel.getExitStatus();
				break;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException ie) {
			}
		}

		if (exitCode != 0)
			throw new ScpException("Removing remote file '" + remoteFile
					+ "' failed");

		out.close();
		channel.disconnect();
	}

	/**
	 * Removes a directory on the remote machine.
	 *
	 * @param remoteDir
	 *            the remote dir
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public void removeDir(String remoteDir) throws IOException {
		try {
			removeDirInternal(remoteDir, false);
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}

	public void removeDirRecursive(String remoteDir) throws IOException {
		try {
			removeDirInternal(remoteDir, true);
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}
	/**
	 * Removes the dir internal.
	 *
	 * @param remoteDir
	 *            the remote dir
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws com.jcraft.jsch.JSchException
	 *             the j sch exception
	 */
	private void removeDirInternal(String remoteDir, boolean recursive) throws IOException,
	JSchException {

		String command = "";
		if(!recursive)
			command = "rmdir '" + remoteDir + "'";
		else
			command = "rm -rf '" + remoteDir + "'";

		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand(command);

		// get I/O streams for remote scp
		OutputStream out = channel.getOutputStream();
		InputStream err = ((ChannelExec) channel).getErrStream();
		channel.connect();

		byte[] buf = new byte[1024];

		// send '\0'
		buf[0] = 0;
		out.write(buf, 0, 1);
		out.flush();

		StringBuffer error = new StringBuffer();
		while (true) {
			int res = err.read();
			if (res == -1)
				break;
			error.append((char) res);
		}

		out.close();
		channel.disconnect();
		int exitCode = channel.getExitStatus();
		if (exitCode != 0)
			throw new ScpException(error.toString());
	}

	/**
	 * Creates a directory on the remote machine. This method supports the
	 * creation of recursive directories but does not throw an exception if the
	 * directory is already existing.
	 *
	 * @param remoteDir
	 *            the remote dir
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public void createDirectory(String remoteDir) throws IOException {
		try {
			createDirectoryInternal(remoteDir);
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}

	/**
	 * Creates the directory internal.
	 *
	 * @param remoteDir
	 *            the remote dir
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws com.jcraft.jsch.JSchException
	 *             the j sch exception
	 */
	private void createDirectoryInternal(String remoteDir) throws IOException,
	JSchException {

		// the -p creates the directories recursively
		// the drawback is that there is no error if the directory is existing.
		String command = "mkdir -p '" + remoteDir + "'";
		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand(command);

		// get I/O streams for remote scp
		OutputStream out = channel.getOutputStream();
		InputStream err = ((ChannelExec) channel).getErrStream();
		channel.connect();

		int exitCode = 0;
		while (true) {
			if (channel.isClosed()) {
				exitCode = channel.getExitStatus();
				break;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException ie) {
			}
		}

		byte[] buf = new byte[1024];

		// send '\0'
		buf[0] = 0;
		out.write(buf, 0, 1);
		out.flush();

		StringBuffer error = new StringBuffer();
		while (true) {
			int res = err.read();
			if (res == -1)
				break;
			error.append((char) res);
		}

		out.close();
		channel.disconnect();

		if (exitCode != 0)
			throw new ScpException(error.toString());
	}

	/**
	 * List the contents of a directory on the remote machine.
	 *
	 * @param remoteDir
	 *            the remote dir
	 * @return the list
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public Collection<String> listFiles(String remoteDir) throws IOException {
		try {
			return listFilesInternal(remoteDir, "-1");
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}

	/**
	 * List the contents of a directory on the remote machine.
	 *
	 * @param remoteDir
	 *            the remote dir
	 * @return the list
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public Collection<String> listFiles(String remoteDir, int type) throws IOException {
		try {
			return listFileRecursiveInternal(remoteDir, type, 1);
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}


	/**
	 * List contents of a directory of the remote machine.
	 *
	 * @param remoteDir
	 *            the remote dir
	 * @return the list
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws com.jcraft.jsch.JSchException
	 *             the j sch exception
	 */
	private Collection<String> listFilesInternal(String remoteDir, String option)
			throws IOException, JSchException {
		Collection<String> result = new LinkedList<String>();

		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand("ls " + option + " '" + remoteDir + "'");

		channel.connect();

		InputStream in = channel.getInputStream();
		InputStream err = ((ChannelExec) channel).getErrStream();
		channel.connect();

		int exitCode = 0;
		while (true) {
			if (channel.isClosed()) {
				exitCode = channel.getExitStatus();
				break;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException ie) {
			}
		}

		String s = "";
		StringBuffer sb = new StringBuffer();
		BufferedReader inReader = new BufferedReader(new InputStreamReader(in));
		BufferedReader errReader = new BufferedReader(
				new InputStreamReader(err));

		if (exitCode != 0) {
			while ((s = errReader.readLine()) != null) {
				sb.append(s).append("\n");
			}
		} else {
			while ((s = inReader.readLine()) != null) {
				result.add(s);
				sb.append(s).append("\n");
			}
			//FeatureUtil.logMsg(sb.toString());
		}

		errReader.close();
		inReader.close();
		channel.disconnect();

		return (result);
	}

	public Collection<String> listDirs(String remoteDir) throws IOException {
		try {
			return listDirsInternal(remoteDir, -1);
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}

	public Collection<String> listDirs(String remoteDir, int maxDepth) throws IOException {
		try {
			return listDirsInternal(remoteDir, maxDepth);
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}

	private Collection<String> listDirsInternal(String remoteDir, int depth)
			throws IOException, JSchException {
		Collection<String> result = new LinkedList<String>();

		Channel channel = session.openChannel("exec");
		String command = "";
		if(depth >= 0)
			command ="find '" + remoteDir + "' -maxdepth " + depth +" -type d";
		else
			command = "find '" + remoteDir + "' -type d";

		((ChannelExec) channel).setCommand(command);

		channel.connect();

		InputStream in = channel.getInputStream();
		InputStream err = ((ChannelExec) channel).getErrStream();
		channel.connect();

		int exitCode = 0;
		while (true) {
			if (channel.isClosed()) {
				exitCode = channel.getExitStatus();
				break;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException ie) {
			}
		}

		String s = "";
		StringBuffer sb = new StringBuffer();
		BufferedReader inReader = new BufferedReader(new InputStreamReader(in));
		BufferedReader errReader = new BufferedReader(
				new InputStreamReader(err));

		if (exitCode != 0) {
			while ((s = errReader.readLine()) != null) {
				sb.append(s).append("\n");
			}
		} else {
			while ((s = inReader.readLine()) != null) {
				if (s.startsWith(remoteDir))
					s = s.substring(remoteDir.length());
				if (s.trim().length() == 0)
					continue;
				result.add(s);
				sb.append(s).append("\n");
			}
			//FeatureUtil.logMsg(sb.toString());
		}

		errReader.close();
		inReader.close();
		channel.disconnect();

		return (result);
	}

	/**List the content of the directory
	 * @param remoteDir
	 * @param type
	 * 		Type of the content: ALL, DIRECTORY, FILE
	 * @return
	 * @throws java.io.IOException
	 */
	public Collection<String> listFileRecursive(String remoteDir,int type )
			throws IOException {

		try {
			return listFileRecursiveInternal(remoteDir, type , -1);
		} catch (JSchException e) {
			throw new ScpException(e.getMessage());
		}

	}
	/**List the content of the directory
	 * @param remoteDir. Support wildcard *,? in path
	 * @param type
	 * 		Type of the content: ALL, DIRECTORY, FILE
	 * @param maxdepth
	 * 		How deep to list the remoteDir
	 * @return
	 * @throws java.io.IOException
	 */
	public Collection<String> listFileRecursive(String remoteDir, int type, int maxdepth)
			throws IOException {

		try {
			return listFileRecursiveInternal(remoteDir, type, maxdepth);
		} catch (JSchException e) {
			e.printStackTrace();
			throw new ScpException(e.getMessage());
		}

	}

	private Collection<String> listFileRecursiveInternal(String remoteDir, int type, int maxdepth) throws IOException, JSchException{

		Collection<String> result = new LinkedList<String>();

		String fType = " -type ";
		if(type == DIRECTORY)
			fType = fType + "d";
		else if (type == FILE)
			fType = fType + "f";
		else fType = "";

		String command = "";
		// This temp file will be placed in user's home dir. So I guess we should have no problem with write permission.
		String tmpFile = "temp" + FileUtil.generateRandomString() + ".tmp";
		remoteDir = remoteDir.replace(" ", "\\ ");
		if(maxdepth >= 0){
			command = "find " + remoteDir + " -maxdepth " + maxdepth + fType + " > " + tmpFile;
		}else
			command = "find " + remoteDir + "" + fType + " > " + tmpFile;

		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand(command);

		channel.connect();

		int exitCode = 0;
		while (true) {
			if (channel.isClosed()) {
				exitCode = channel.getExitStatus();
				break;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException ie) {
			}
		}
		channel.disconnect();

		File tmpLocalFile = new File(FileUtil.createTempDirectory(), tmpFile);
		readFileInternal(tmpFile, tmpLocalFile.getAbsolutePath());

		FileReader fR = new FileReader(tmpLocalFile);
		BufferedReader bR = new BufferedReader(fR);

		String s ="";

		String srcRemote = FileUtil.getSourceWildCard(remoteDir);
		try{
			if (exitCode == 0){
				while ((s = bR.readLine()) != null) {

					if (s.startsWith(srcRemote))
						s = s.substring(srcRemote.length());
					else
						continue;

					if (s.trim().length() == 0)
						continue;
					result.add(s);
				}
			}

		} finally {
			fR.close();
			bR.close();
			if(tmpLocalFile!=null && tmpLocalFile.exists())
				tmpLocalFile.delete();
			removeFileInternal(tmpFile);
		}

		return (result);

	}

	public boolean isDirectory(String remotePath) throws IOException{
		String result = "";

		result = executeCommand(" if [ -d '"+ remotePath + "' ] ; then echo 'Is Directory';fi", false);
		if(result.trim().equals("Is Directory")) {
			return true;
		}
		return false;
	}

	public boolean isFile(String remotePath) throws IOException{
		String result = "";

		result = executeCommand(" if [ -f '"+ remotePath + "' ] ; then echo 'Is File';fi", false);
		if(result.trim().equals("Is File")) {
			return true;
		}
		return false;
	}

	public boolean isExisted(String remotePath) throws IOException{
		String result = "";

		result = executeCommand(" if [ -e '"+ remotePath + "' ] ; then echo 'Is Existed';fi", false);
		if(result.trim().equals("Is Existed")) {
			return true;
		}
		return false;
	}


	/**
	 * Execute a command on the remote machine. The remote machine must support
	 * the desired command.
	 *
	 * @param command
	 *            the command
	 * @return the string
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public String executeCommand(String command, boolean logCommand) throws IOException {
		try {
			return executeCommandInternal(command, logCommand);
		} catch (JSchException e) {
			throw new ScpException(e.getLocalizedMessage());
		}
	}

	public String executeCommand(String command) throws IOException {
		return (executeCommand(command, false));
	}

	/**
	 * Execute command internal.
	 *
	 * @param command
	 *            the command
	 * @return the string
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws com.jcraft.jsch.JSchException
	 *             the j sch exception
	 */
	private String executeCommandInternal(String command, boolean logCommand) throws IOException,
	JSchException {
		if(logCommand)
			FeatureUtil.logMsg("Executing command '" + command + "' on remote host");

		Channel channel = session.openChannel("exec");
		((ChannelExec) channel).setCommand(command);

		InputStream in = channel.getInputStream();
		InputStream err = ((ChannelExec) channel).getErrStream();
		channel.connect();

		String s = "";
		StringBuffer sb = new StringBuffer();
		BufferedReader inReader = new BufferedReader(new InputStreamReader(in));
		BufferedReader errReader = new BufferedReader(
				new InputStreamReader(err));

		while ((s = errReader.readLine()) != null) {
			sb.append(s).append("\n");
		}

		while ((s = inReader.readLine()) != null) {
			sb.append(s).append("\n");
		}


		errReader.close();
		inReader.close();
		channel.disconnect();
		int exitCode = channel.getExitStatus();
		if (exitCode != 0)
			throw new ScpException(sb.toString());

		return sb.toString();
	}


	/**
	 * Check ack.
	 *
	 * @param in
	 *            the in
	 * @return the int
	 * @throws java.io.IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private static int checkAck(InputStream in) throws IOException {
		int b = in.read();
		// b may be 0 for success,
		// 1 for error,
		// 2 for fatal error,
		// -1
		if (b == 0)
			return b;
		if (b == -1)
			return b;

		if (b == 1 || b == 2) {
			StringBuffer sb = new StringBuffer();
			int c;
			do {
				c = in.read();
				sb.append((char) c);
			} while (c != '\n');
			if (b == 1) { // error
				System.out.print("error " + sb.toString());
			}
			if (b == 2) { // fatal error
				System.out.print("fatal " + sb.toString());
			}
		}
		return b;
	}

	// ///////////////////////////////////////////////////////////////////////

	/**
	 * The Class MyUserInfo.
	 */
	private final static class MyUserInfo implements UserInfo,
	UIKeyboardInteractive {

		/**
		 * The passwd.
		 */
		String passwd = "";

		/*
		 * (non-Javadoc)
		 *
		 * @see com.jcraft.jsch.UserInfo#getPassword()
		 */
		@Override
		public String getPassword() {
			return passwd;
		}

		/**
		 * Sets the password.
		 *
		 * @param passwd
		 *            the new password
		 */
		public void setPassword(String passwd) {
			this.passwd = passwd;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.jcraft.jsch.UserInfo#promptYesNo(java.lang.String)
		 */
		@Override
		public boolean promptYesNo(String str) {
			return true;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.jcraft.jsch.UserInfo#getPassphrase()
		 */
		@Override
		public String getPassphrase() {
			return null;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.jcraft.jsch.UserInfo#promptPassphrase(java.lang.String)
		 */
		@Override
		public boolean promptPassphrase(String message) {
			return true;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.jcraft.jsch.UserInfo#promptPassword(java.lang.String)
		 */
		@Override
		public boolean promptPassword(String message) {
			return true;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.jcraft.jsch.UserInfo#showMessage(java.lang.String)
		 */
		@Override
		public void showMessage(String message) {
			// JOptionPane.showMessageDialog(null, message);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see
		 * com.jcraft.jsch.UIKeyboardInteractive#promptKeyboardInteractive(java
		 * .lang.String, java.lang.String, java.lang.String, java.lang.String[],
		 * boolean[])
		 */
		@Override
		public String[] promptKeyboardInteractive(String destination,
				String name, String instruction, String[] prompt, boolean[] echo) {
			FeatureUtil.logMsg("promptKeyboardInteractive destination: "
					+ destination + ", name: " + name + ", instruction: "
					+ instruction);
			for (String pr : prompt)
				FeatureUtil.logMsg("  prompt: " + pr);
			for (boolean ec : echo)
				FeatureUtil.logMsg("  echo: " + ec);
			// return null == cancel job

			if (prompt.length > 0 && prompt[0].startsWith("Password")) {
				return new String[] { passwd };
			}
			return null;
		}
	}
}
