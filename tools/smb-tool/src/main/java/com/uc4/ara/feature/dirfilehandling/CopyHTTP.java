/*
 * (c) 2012 Michael Schwartz e.U.
 * All Rights Reserved.
 * 
 * This program is not a free software. The owner of the copyright
 * can license the software for you. You may not use this file except in
 * compliance with the License. In case of questions please
 * do not hesitate to contact us at idx@mschwartz.eu.
 * 
 * Filename: CopyHTTP.java
 * Created: 20.09.2012
 * 
 * Author: $LastChangedBy$
 * Date: $LastChangedDate$
 * Revision: $LastChangedRevision$
 */
package com.uc4.ara.feature.dirfilehandling;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import com.uc4.ara.feature.FeatureUtil;
import com.uc4.ara.feature.globalcodes.ErrorCodes;
import com.uc4.ara.feature.utils.FileUtil;


public class CopyHTTP extends AbstractCopy {


	private final boolean secure;


	public CopyHTTP(String host, int port, String username, String password,
			String from, String to, boolean overwrite, long timeout,
			String proxyHost, int proxyPort, String proxyUser, String proxyPassword, boolean secure) {

		super(host, port, username, password, from, false, to, overwrite,
				timeout, false, proxyHost, proxyPort, proxyUser, proxyPassword,null, null);
		this.secure = secure;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.uc4.ara.feature.dirfilehandling.AbstractCopy#copy()
	 */
	@Override
	public int retrieve() throws Exception {

		if(!from.startsWith("/") && !from.startsWith("\\"))
			from = "/" + from;

		from = from.replaceAll("\\\\", "/");
		from = FileUtil.normalize(from);

		if(from.endsWith("/"))
			from = from.substring(0, from.length() - 1);

		HttpHost target = new HttpHost(host, port, "http" + (secure ? "s" : ""));

		HttpParams httpParams = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(httpParams, (int) timeout);

		DefaultHttpClient httpclient = new DefaultHttpClient(httpParams);
		// circumvent the SSL authentication exception
		httpclient = new WebClientDevWrapper().wrapClient(httpclient);

		if (username != null && username.length() > 0)

			httpclient.getCredentialsProvider().setCredentials(
					new AuthScope(target),
					new UsernamePasswordCredentials(username, password)
					);

		if (proxyHost != null && proxyHost.length() > 0) {

			HttpHost proxy = new HttpHost(proxyHost, proxyPort, "http");
			httpclient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);

			if (proxyUser != null && proxyUser.length() > 0) {

				httpclient.getCredentialsProvider().setCredentials(
						new AuthScope(proxy),
						new UsernamePasswordCredentials(proxyUser, proxyPassword));
			}
		}


		HttpGet httpGet = new HttpGet(from);

		HttpResponse response;
		
		try {
		    response = httpclient.execute(target, httpGet);
		} catch (UnknownHostException ex) {
		    return ERROR_CODE_HOST_NOT_FOUND;
		}

		int statusCode = response.getStatusLine().getStatusCode();

		if (statusCode < 200 || statusCode > 300){
			FeatureUtil.logMsg("Received status code " + statusCode +
					". Message: " +response.getStatusLine() + ". Aborting ...");
			
			//4xx/5xx will be return as standard HTTP error response codes (e.g. site not found,..)
			if (statusCode > 300) {
			    return statusCode;
			}
			return ErrorCodes.ERROR;
		}

		String path = httpGet.getURI().getPath();
		InputStream is = response.getEntity().getContent();

		// Check if target file exists
		File localFile = new File(to);
		File pf = localFile.getParentFile();

		if(localFile.isDirectory())
			localFile = new File(localFile, path.substring(path.lastIndexOf("/"), path.length()));


		if(localFile.exists())
			if(overwrite){
				FeatureUtil.logMsg("'" + localFile.getAbsolutePath() + "' already exists!. Deleting ...");
				if(localFile.isDirectory()) FileUtil.deleteDirectory(localFile);
				else localFile.delete();
			}else {
				FeatureUtil.logMsg(localFile.getAbsolutePath() + " already exists and overwrite is set to NO. Aborting ...");
				return ERROR_CODE_FILE_NOT_FOUND;
			}


		if(!pf.isDirectory() && !pf.mkdirs()){
			FeatureUtil.logMsg("Local Directory " + pf.getAbsolutePath()  +
					" does not exist or can't be created. Please check the path again. Aborting ...");
			return ERROR_CODE_FILE_COULD_NOT_BE_WRITTEN;
		}

		FeatureUtil.logMsg("Copying '"  + target.toString() +  httpGet.getURI().getPath() + "' => '" + localFile.getAbsolutePath() + "'");

		OutputStream os = new FileOutputStream(localFile);
		byte[] buff = new byte[4096];
		int len;

		try {
			while (-1 != (len = is.read(buff)))
				os.write(buff, 0, len);

		} catch (IOException e){
			FeatureUtil.logMsg("IOException occured while copying file. Aborting ...");
			return ERROR_CODE_FILE_COULD_NOT_BE_WRITTEN;
		} finally {
			os.flush();
			os.close();
			is.close();
			EntityUtils.consume(response.getEntity());
			httpGet.releaseConnection();
		}


		return ErrorCodes.OK;
	}

	@Override
	public int store() throws Exception {
		throw new UnsupportedOperationException();
	}


	public class WebClientDevWrapper {

		public DefaultHttpClient wrapClient(HttpClient base)
				throws NoSuchAlgorithmException, KeyManagementException {
			SSLContext ctx = SSLContext.getInstance("TLS");
			X509TrustManager tm = new X509TrustManager() {

				@Override
				public void checkClientTrusted(X509Certificate[] xcs,
						String string) throws CertificateException {
				}

				@Override
				public void checkServerTrusted(X509Certificate[] xcs,
						String string) throws CertificateException {
				}

				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
			};
			ctx.init(null, new TrustManager[] { tm }, null);
			SSLSocketFactory ssf = new SSLSocketFactory(ctx, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			ClientConnectionManager ccm = base.getConnectionManager();
			SchemeRegistry sr = ccm.getSchemeRegistry();
			sr.register(new Scheme("https", 443, ssf));

			return new DefaultHttpClient(ccm, base.getParams());
		}
	}
}
