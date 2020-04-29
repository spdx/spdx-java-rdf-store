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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.library.SpdxConstants;

/**
 * Factory to create specific SPDX resources
 */
public class SpdxResourceFactory {
	
	static final Logger logger = LoggerFactory.getLogger(SpdxResourceFactory.class.getName());
	
	// RDF_NAMESPACE
	public static final Set<String> RDF_PROPERTIES = Collections.unmodifiableSet(
			new HashSet<String>(Arrays.asList(SpdxConstants.RDF_PROPERTIES)));
	
	// RDFS_NAMESPACE
	public static final Set<String> RDFS_PROPERTIES = Collections.unmodifiableSet(
			new HashSet<String>(Arrays.asList(SpdxConstants.RDFS_PROPERTIES)));
	
	// DOAP_NAMESPACE
	public static final Set<String> DOAP_TYPES = Collections.unmodifiableSet(
			new HashSet<String>(Arrays.asList(SpdxConstants.DOAP_CLASSES)));
	public static final Set<String> DOAP_PROPERTIES = Collections.unmodifiableSet(
			new HashSet<String>(Arrays.asList(SpdxConstants.DOAP_PROPERTIES)));
	
	// OWL_NAMESPACE
	public static final Set<String> OWL_PROPERTIES = Collections.unmodifiableSet(
			new HashSet<String>(Arrays.asList(SpdxConstants.OWL_PROPERTIES)));
	
	// RDF_POINTER_NAMESPACE
	public static final Set<String> POINTER_TYPES = Collections.unmodifiableSet(
			new HashSet<String>(Arrays.asList(SpdxConstants.POINTER_CLASSES)));
	public static final Set<String> POINTER_PROPERTIES = Collections.unmodifiableSet(
			new HashSet<String>(Arrays.asList(SpdxConstants.POINTER_PROPERTIES)));
	
	/**
	 * Create a Resource based on an SPDX class or type
	 * @param type type of SPDX object
	 * @return RDF Type representation of the string type
	 */
	public static Resource typeToResource(String type) {
		Objects.requireNonNull(type);
		if (DOAP_TYPES.contains(type)) {
			return ResourceFactory.createResource(SpdxConstants.DOAP_NAMESPACE + type);
		} else if (POINTER_TYPES.contains(type)) {
			return ResourceFactory.createResource(SpdxConstants.RDF_POINTER_NAMESPACE + type);
		} else {
			return ResourceFactory.createResource(SpdxConstants.SPDX_NAMESPACE + type); 
		}
	}
	
	/**
	 * @param className
	 * @return URI for the type or className
	 */
	public static String classNameToUri(String className) {
		Objects.requireNonNull(className);
		if (DOAP_TYPES.contains(className)) {
			return SpdxConstants.DOAP_NAMESPACE + className;
		} else if (POINTER_TYPES.contains(className)) {
			return SpdxConstants.RDF_POINTER_NAMESPACE + className;
		} else {
			return SpdxConstants.SPDX_NAMESPACE + className; 
		}
	}
	
	/**
	 * Create a Resource based on a standard SPDX property name
	 * @param propertyName name of the property
	 * @return Resource representing the property
	 */
	public static String propertyNameToUri(String propertyName) {
		Objects.requireNonNull(propertyName);
		if (RDF_PROPERTIES.contains(propertyName)) {
			return SpdxConstants.RDF_NAMESPACE + propertyName;
		} else if (RDFS_PROPERTIES.contains(propertyName)) {
			return SpdxConstants.RDFS_NAMESPACE + propertyName;
		} else if (DOAP_PROPERTIES.contains(propertyName)) {
			return SpdxConstants.DOAP_NAMESPACE + propertyName;
		} else if (OWL_PROPERTIES.contains(propertyName)) {
			return SpdxConstants.OWL_NAMESPACE + propertyName;
		} else if (POINTER_PROPERTIES.contains(propertyName)) {
			return SpdxConstants.RDF_POINTER_NAMESPACE + propertyName;
		} else {
			return SpdxOwlOntology.checkGetOwlUriFromRenamed(SpdxConstants.SPDX_NAMESPACE + propertyName); 
		}
	}

	/**
	 * Convert a type resource to the SPDX class or type
	 * @param type Resource representing the type 
	 * @return the SPDX class or type string, null if none of the class names match
	 */
	public static Optional<String> resourceToSpdxType(Resource type) {
		if (!type.isURIResource()) {
			return Optional.empty();
		}
		String typeUri = type.getURI();
		if (typeUri == null) {
			return Optional.empty();
		}
		if (typeUri.startsWith(SpdxConstants.DOAP_NAMESPACE)) {
			return Optional.of(typeUri.substring(SpdxConstants.DOAP_NAMESPACE.length()));
		}
		if (typeUri.startsWith(SpdxConstants.RDF_POINTER_NAMESPACE)) {
			return Optional.of(typeUri.substring(SpdxConstants.RDF_POINTER_NAMESPACE.length()));
		}
		if (typeUri.startsWith(SpdxConstants.SPDX_NAMESPACE)) {
			return Optional.of(typeUri.substring(SpdxConstants.SPDX_NAMESPACE.length()));
		} else {
			logger.warn("Unknown resource type: "+typeUri);
			return Optional.empty();
		}
	}
}
