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
				"-src", "source\\One_Installer\\CA.Continuous.Delivery.Automation*.zip",
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
	public void testGetFileFromPD01_ShouldSuccess() throws Exception {
		GetFiles test = new GetFiles();
		test.initialize();
		String[] args = new String[] {
				"-prc", "SMB",
				"-h", "192.168.40.112",
				"-p", "139",
				"-u", "sbb01\\admin",
				"-pwd", "",
				"-src", "stg\\qa\\EM\\1.0.0\\+\\*.zip",
				"-tgt", "C:\\test\\One_Installer",
				"-o", "YES",
				"-to", "5000",
				"-prv", "YES",
				"-r", "YES",
				"-smbDN", ""};
		long start = System.currentTimeMillis();
		int retCode = test.run(args);
		long end = System.currentTimeMillis();
		System.out.println("DEBUG: Get file from pd01 took " + (end - start)/1000 + " seconds");
		assertEquals(0, retCode);
	}
}
