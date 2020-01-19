/**
 * Copyright (c) 2020 Source Auditor Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 * 
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
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
