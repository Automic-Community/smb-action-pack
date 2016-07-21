/* 
 * (c) 2012 Michael Schwartz e.U. 
 * All Rights Reserved.
 * 
 * This program is not a free software. The owner of the copyright
 * can license the software for you. You may not use this file except in
 * compliance with the License. In case of questions please
 * do not hesitate to contact us at idx@mschwartz.eu.
 * 
 * Filename: ScpException.java
 * Created: 15.05.2012
 * 
 * Author: $LastChangedBy$ 
 * Date: $LastChangedDate$ 
 * Revision: $LastChangedRevision$ 
 */
package com.uc4.ara.feature.utils;

/**
 * The Class ScpException is used by the {@link ScpWrapper} to throw a
 * non-recoverable exception.
 */
public class ScpException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6689892764798562223L;

	public ScpException(String message) {
		super(message);
	}
}
