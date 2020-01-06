/**
 * 
 */
package org.spdx.spdxRdfStore;

/**
 * Exceptions related to missing restrictions in the SPDX OWL ontology
 * 
 * @author Gary O'Neall
 *
 */
public class MissingDataTypeAndClassRestriction extends SpdxRdfException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * @param msg
	 */
	public MissingDataTypeAndClassRestriction(String msg) {
		super(msg);
	}

	/**
	 * @param msg
	 * @param inner
	 */
	public MissingDataTypeAndClassRestriction(String msg, Throwable inner) {
		super(msg, inner);
	}

}
