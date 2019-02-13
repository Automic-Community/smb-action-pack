package com.uc4.ara.feature.dirfilehandling;

import static org.junit.Assert.*;

import org.junit.Test;

public class GetFilesTest {

	@Test
	public void testGetLargeFile_ShouldSuccess() throws Exception {
		GetFiles test = new GetFiles();
		test.initialize();
		String[] args = new String[] {
				"-prc", "SMB",
				"-h", "VVNSATSTNDRD01",
				"-p", "139",
				"-u", "Administrator",
				"-pwd", "S3rv3r@Aut",
				"-src", "automic/smb/JDK/jdk-11u13",
				"-tgt", "C:\\test\\One_Installer",
				"-o", "YES",
				"-to", "5000",
				"-prv", "YES",
				"-r", "YES",
				"-smbDN", ""};
		long start = System.currentTimeMillis();
		int retCode = test.run(args);
		long end = System.currentTimeMillis();
		System.out.println("DEBUG: Get large file took " + (end - start)/1000 + " seconds");
		assertEquals(0, retCode);
	}
	
	@Test
	public void testGetMultiFiles_ShouldSuccess() throws Exception {
		GetFiles test = new GetFiles();
		test.initialize();
		String[] args = new String[] {
				"-prc", "SMB",
				"-h", "VVNSATSTNDRD01",
				"-p", "139",
				"-u", "Administrator",
				"-pwd", "S3rv3r@Aut",
				"-src", "automic/smb/APP_SRV/apache-tomcat-9.0.14",
				"-tgt", "C:/test/One_Installer/APP_SRV/apache-tomcat-9.0.14",
				"-o", "YES",
				"-to", "5000",
				"-prv", "YES",
				"-r", "YES",
				"-smbDN", ""};
		long start = System.currentTimeMillis();
		int retCode = test.run(args);
		long end = System.currentTimeMillis();
		System.out.println("DEBUG: Get large file took " + (end - start)/1000 + " seconds");
		assertEquals(0, retCode);
	}

	@Test
	public void testGetFileFromPD01_ShouldSuccess() throws Exception {
		GetFiles test = new GetFiles();
		test.initialize();
		String[] args = new String[] {
				"-prc", "SMB",
				"-h", "192.168.40.112",
				"-p", "139",
				"-u", "admin",
				"-pwd", "password",
				"-src", "stg\\qa\\EM\\1.0.0\\+\\*.zip",
				"-tgt", "C:\\test\\One_Installer",
				"-o", "YES",
				"-to", "5000",
				"-prv", "YES",
				"-r", "YES",
				"-smbDN", "sbb01"};
		long start = System.currentTimeMillis();
		int retCode = test.run(args);
		long end = System.currentTimeMillis();
		System.out.println("DEBUG: Get file from pd01 took " + (end - start)/1000 + " seconds");
		assertEquals(0, retCode);
	}
	
	@Test
	public void testGetFileFromFsu_ShouldSuccess() throws Exception {
		GetFiles test = new GetFiles();
		test.initialize();
		String[] args = new String[] {
				"-prc", "SMB",
				"-h", "10.243.44.232",
				"-p", "139",
				"-u", "ucprod",
				"-pwd", "--109B26FB0810557FB8DB6827BAD224CF84",
				"-src", "UC100T/DB/jdbc/sqljdbc42.jar",
				"-tgt", "C:\\test\\One_Installer\\sqljdbc42.jar",
				"-o", "YES",
				"-to", "5000",
				"-prv", "YES",
				"-r", "YES",
				"-smbDN", "SBB01"};
		long start = System.currentTimeMillis();
		int retCode = test.run(args);
		long end = System.currentTimeMillis();
		System.out.println("DEBUG: Get file from pd01 took " + (end - start)/1000 + " seconds");
		assertEquals(0, retCode);
	}
}
