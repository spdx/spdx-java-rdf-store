/**
 * Copyright (c) 2020 Source Auditor Inc.
 * <p>
 * SPDX-License-Identifier: Apache-2.0
 * <p>
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 * <p>
 *       http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.spdx.spdxRdfStore;

/**
 * Formats supported for serializing RDF
 * 
 * 
 * @author Gary O'Neall
 */
public enum OutputFormat {
	// outputFormat must be one of RDF/XML-ABBREV (default), RDF/XML, N-TRIPLET, TURTLE, JSON-LD
	XML_ABBREV("RDF/XML-ABBREV"),
	XML("RDF/XML"),
	N_TRIPLET("NTRIPLE"),
	TURTLE("TURTLE"),
	JSON_LD("JSON-LD");
	
	private final String type;
	OutputFormat(String type) {
		this.type = type;
	}
	String getType() { return type; }
}

