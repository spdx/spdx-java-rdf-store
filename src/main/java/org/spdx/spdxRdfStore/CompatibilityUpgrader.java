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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.spdx.library.SpdxConstants;

/**
 * Updates the RDF model for compatibility with the current version of the spec
 * @author Gary O'Neall
 *
 */
public class CompatibilityUpgrader {
	
	static final Map<String, Map<String, String>> TYPE_PROPERTY_MAP;
	
	static {
		Map<String, Map<String, String>> mutableTypePropertyMap = new HashMap<>();
		Map<String, String> documentMap = new HashMap<>();
		documentMap.put(SpdxConstants.SPDX_NAMESPACE + SpdxConstants.PROP_SPDX_VERSION, 
				SpdxConstants.SPDX_NAMESPACE + SpdxConstants.PROP_SPDX_SPDX_VERSION);
		mutableTypePropertyMap.put(SpdxConstants.CLASS_SPDX_DOCUMENT, Collections.unmodifiableMap(documentMap));
		
		TYPE_PROPERTY_MAP = Collections.unmodifiableMap(mutableTypePropertyMap);
	}

	/**
	 * Upgrade the properties in the model to the current version of the spec
	 * @param model
	 */
	public static void upgrade(Model model) throws SpdxRdfException {
		model.enterCriticalSection(false);
		try {
			for (Entry<String, Map<String, String>> entry:TYPE_PROPERTY_MAP.entrySet()) {
				String query = "SELECT ?s ?type  WHERE { ?s  <" + 
						RdfSpdxDocumentModelManager.RDF_TYPE + "> <" +
						SpdxConstants.SPDX_NAMESPACE + entry.getKey() + "> }";
				QueryExecution qe = QueryExecutionFactory.create(query, model);
				ResultSet result = qe.execSelect();
				while (result.hasNext()) {
					Resource subject = result.next().get("s").asResource();
					for (Entry<String, String> propEntry:entry.getValue().entrySet()) {
						Property incompatibleProperty = model.createProperty(propEntry.getKey());
						if (subject.hasProperty(incompatibleProperty)) {
							Property compatibleProperty = model.createProperty(propEntry.getValue());
							NodeIterator iter = model.listObjectsOfProperty(subject, incompatibleProperty);
							while (iter.hasNext()) {
								RDFNode object = iter.next();
								subject.addProperty(compatibleProperty, object);
							}
							subject.removeAll(incompatibleProperty);
						}
					}
				}
			}
		} finally {
			model.leaveCriticalSection();
		}
	}

	
}
