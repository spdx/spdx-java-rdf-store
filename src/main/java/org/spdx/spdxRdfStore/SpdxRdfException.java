/**
 * 
 */
package org.spdx.spdxRdfStore;

import org.spdx.library.InvalidSPDXAnalysisException;

/**
 * Exceptions related to RDF storage of SPDX documents
 * 
 * @author Gary O'Neall
 *
 */
public class SpdxRdfException extends InvalidSPDXAnalysisException {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public SpdxRdfException(String msg) {
		super(msg);
	}
	
	public SpdxRdfException(String msg, Throwable inner) {
		super(msg, inner);
	}

}
