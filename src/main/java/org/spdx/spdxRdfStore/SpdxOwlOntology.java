/**
 * SPDX-FileCopyrightText: Copyright (c) 2020 Source Auditor Inc.
 * SPDX-FileType: SOURCE
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.jena.ontology.DatatypeProperty;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;

/**
 * Singleton class to manage the OWL ontology
 * 
 * @author Gary O'Neall
 */
@SuppressWarnings("LoggingSimilarMessage")
public class SpdxOwlOntology {
	
	static final Logger logger = LoggerFactory.getLogger(SpdxOwlOntology.class);
	
	static SpdxOwlOntology myself = null;
	
	static final String ONTOLOGY_PATH = "/resources/spdx-2-3-revision-2-ontology.owl.xml";
	
	private final OntModel model;
	
	Property PROP_MIN_CARDINALITY;
	Property PROP_MIN_QUAL_CARDINALITY;
	Property PROP_MAX_CARDINALITY;
	Property PROP_MAX_QUAL_CARDINALITY;
	Property PROP_CARDINALITY;
	Property PROP_QUAL_CARDINALITY;
	Property ON_PROPERTY_PROPERTY;
	Property RANGE_PROPERTY;
	Property ON_CLASS_PROPERTY;
	Property ON_DATA_RANGE_PROPERTY;
	
	static final Map<String, Class<?>> DATA_TYPE_TO_CLASS;
	
	static {
		// Note: We only use the types supported by the storage manager Integer, Boolean, String

        DATA_TYPE_TO_CLASS = Map.ofEntries(Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "string", String.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "hexBinary", String.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "anyURI", String.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "dateTime", String.class), Map.entry(SpdxConstantsCompatV2.RDFS_NAMESPACE + "Literal", String.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "int", Integer.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "integer", Integer.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "short", Integer.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "byte", Integer.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "unsignedShort", Integer.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "unsignedInt", Integer.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "unsignedByte", Integer.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "positiveInteger", Integer.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "nonNegativeInteger", Integer.class), Map.entry(SpdxConstantsCompatV2.XML_SCHEMA_NAMESPACE + "boolean", Boolean.class));
	}
	
	/**
	 * Map of the properties renamed due to spec inconsistencies between the RDF format and other formats
	 */
	public static final Map<String, String> RENAMED_PROPERTY_TO_OWL_PROPERTY;
	public static final Map<String, String> OWL_PROPERTY_TO_RENAMED_PROPERTY;
	static {
		Map<String, String> renamedToOwl = new HashMap<>();
		Map<String, String> owlToRenamed = new HashMap<>();
		renamedToOwl.put(SpdxConstantsCompatV2.PROP_SPDX_SPEC_VERSION.getName(), SpdxConstantsCompatV2.PROP_SPDX_VERSION.getName());
		owlToRenamed.put(SpdxConstantsCompatV2.PROP_SPDX_VERSION.getName(), SpdxConstantsCompatV2.PROP_SPDX_SPEC_VERSION.getName());
		RENAMED_PROPERTY_TO_OWL_PROPERTY = Collections.unmodifiableMap(renamedToOwl);
		OWL_PROPERTY_TO_RENAMED_PROPERTY = Collections.unmodifiableMap(owlToRenamed);
	}

	
	public static synchronized SpdxOwlOntology getSpdxOwlOntology() {
		if (Objects.isNull(myself)) {
			myself = new SpdxOwlOntology();
		}
		return myself;
	}

	/**
	 * 
	 */
	private SpdxOwlOntology() {
		try (InputStream is = SpdxOwlOntology.class.getResourceAsStream(ONTOLOGY_PATH)) {
			if (Objects.isNull(is)) {
				throw new RuntimeException("Can not open SPDX OWL ontology file");
			}
			model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
			model.read(is, "RDF/XML");
			PROP_MIN_CARDINALITY = model.createProperty("http://www.w3.org/2002/07/owl#minCardinality");
			PROP_MIN_QUAL_CARDINALITY = model.createProperty("http://www.w3.org/2002/07/owl#minQualifiedCardinality");
			PROP_MAX_CARDINALITY = model.createProperty("http://www.w3.org/2002/07/owl#maxCardinality");
			PROP_MAX_QUAL_CARDINALITY = model.createProperty("http://www.w3.org/2002/07/owl#maxQualifiedCardinality");
			PROP_CARDINALITY = model.createProperty("http://www.w3.org/2002/07/owl#cardinality");
			ON_PROPERTY_PROPERTY = model.createProperty("http://www.w3.org/2002/07/owl#onProperty");
			RANGE_PROPERTY = model.getProperty("http://www.w3.org/2000/01/rdf-schema#range");
			PROP_QUAL_CARDINALITY = model.getProperty("http://www.w3.org/2002/07/owl#qualifiedCardinality");
			ON_CLASS_PROPERTY = model.getProperty("http://www.w3.org/2002/07/owl#onClass");
			ON_DATA_RANGE_PROPERTY = model.getProperty("http://www.w3.org/2002/07/owl#onDataRange");
		} catch (IOException e) {
			throw new RuntimeException("I/O error in the SPDX OWL ontology file",e);
		}
	}
	
	/**
	 * Checks to see if a property name has been renamed from the OWL property name and returns the OWL compliant name
	 * @param renamedPropertyUri URI of the property
	 * @return the OWL compliant name
	 */
	public static String checkGetOwlUriFromRenamed(String renamedPropertyUri) {
		if (renamedPropertyUri.startsWith(SpdxConstantsCompatV2.SPDX_NAMESPACE) && 
				RENAMED_PROPERTY_TO_OWL_PROPERTY.containsKey(renamedPropertyUri.substring(SpdxConstantsCompatV2.SPDX_NAMESPACE.length()))) {
			return SpdxConstantsCompatV2.SPDX_NAMESPACE + 
					RENAMED_PROPERTY_TO_OWL_PROPERTY.get(renamedPropertyUri.substring(SpdxConstantsCompatV2.SPDX_NAMESPACE.length()));
		} else {
			return renamedPropertyUri;
		}
	}
	
	/**
	 * Checks to see if a property name has been renamed from the OWL property URI and returns the renamed URI
	 * @param owlPropertyUri URI for the OWL compliant property
	 * @return if renamed, return the renamed URI
	 */
	public static String checkGetRenamedUri(String owlPropertyUri) {
		if (owlPropertyUri.startsWith(SpdxConstantsCompatV2.SPDX_NAMESPACE) && 
				OWL_PROPERTY_TO_RENAMED_PROPERTY.containsKey(owlPropertyUri.substring(SpdxConstantsCompatV2.SPDX_NAMESPACE.length()))) {
			return SpdxConstantsCompatV2.SPDX_NAMESPACE + 
					OWL_PROPERTY_TO_RENAMED_PROPERTY.get(owlPropertyUri.substring(SpdxConstantsCompatV2.SPDX_NAMESPACE.length()));
		} else {
			return owlPropertyUri;
		}
	}
	
	/**
	 * Search the ontology range for a property and return the Java class that best matches the property type
	 * @param p property to search for the class range
	 * @return the Java class that best matches the property type
	 */
	public Optional<Class<?>> getPropertyClass(Property p) {
		if (!p.isURIResource()) {
			return Optional.empty();
		}
		String propertyUri = checkGetOwlUriFromRenamed(p.getURI());
		
		DatatypeProperty dataProperty = this.model.getDatatypeProperty(propertyUri);
		if (Objects.isNull(dataProperty)) {
			return Optional.empty();
		}
		ExtendedIterator<? extends OntResource> rangeIter = dataProperty.listRange();
		while (rangeIter.hasNext()) {
			OntResource range = rangeIter.next();
			if (range.isURIResource()) {
				Class<?> retval = DATA_TYPE_TO_CLASS.get(range.getURI());
				if (Objects.nonNull(retval)) {
					return Optional.of(retval);
				} else {
                    logger.warn("Unknown data type: {}", range);
				}
			}
		}
		return Optional.empty();
	}
	
	public OntModel getModel() {
		return this.model;
	}
	
	/**
	 * @param classUri URI for the class
	 * @param propertyUri URI for the property
	 * @return any class restrictions for the values of the property in the class
	 * @throws SpdxRdfException on RDF related errors
	 */
	public List<String> getClassUriRestrictions(String classUri, String propertyUri) throws SpdxRdfException {
		Objects.requireNonNull(classUri, "Missing class URI");
		Objects.requireNonNull(propertyUri, "Missing property URI");
		OntClass ontClass = model.getOntClass(classUri);
		if (Objects.isNull(ontClass)) {
			if (classUri.endsWith("GenericSpdxElement")) {
				ontClass = model.getOntClass(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_SPDX_ELEMENT);
			} else {
                logger.warn("{} is not an SPDX class", classUri);
				throw new SpdxRdfException(classUri + " is not an SPDX class");
			}
		}
		OntProperty property = model.getOntProperty(checkGetOwlUriFromRenamed(propertyUri));
		if (Objects.isNull(property)) {
            logger.warn("{} is not an SPDX property", propertyUri);
			throw new MissingDataTypeAndClassRestriction(propertyUri + " is not an SPDX property");
		}
		List<Statement> propertyRestrictions = new ArrayList<>();
		addPropertyRestrictions(ontClass, property, propertyRestrictions);
		List<String> retval = new ArrayList<>();
		for (Statement stmt:propertyRestrictions) {
			if (stmt.getPredicate().equals(ON_CLASS_PROPERTY) && stmt.getObject().asResource().isURIResource()) {
				retval.add(stmt.getObject().asResource().getURI());
			}
		}
		return retval;
	}
	
	/**
	 * @param classUri URI for the class
	 * @param propertyUri URI for the property
	 * @return any data restrictions for the values of the property in the class
	 * @throws SpdxRdfException on RDF related errors
	 */
	public List<String> getDataUriRestrictions(String classUri, String propertyUri) throws SpdxRdfException {
		Objects.requireNonNull(classUri, "Missing class URI");
		Objects.requireNonNull(propertyUri, "Missing property URI");
		OntClass ontClass = model.getOntClass(classUri);
		if (Objects.isNull(ontClass)) {
			if (classUri.endsWith("GenericSpdxElement")) {
				ontClass = model.getOntClass(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_SPDX_ELEMENT);
			} else {
                logger.warn("{} is not an SPDX class", classUri);
				throw new SpdxRdfException(classUri + " is not an SPDX class");
			}
		}
		OntProperty property = model.getOntProperty(checkGetOwlUriFromRenamed(propertyUri));
		if (Objects.isNull(property)) {
            logger.warn("{} is not an SPDX property", propertyUri);
			throw new SpdxRdfException(propertyUri + " is not an SPDX property");
		}
		List<Statement> propertyRestrictions = new ArrayList<>();
		addPropertyRestrictions(ontClass, property, propertyRestrictions);
		List<String> retval = new ArrayList<>();
		for (Statement stmt:propertyRestrictions) {
			if (stmt.getPredicate().equals(ON_DATA_RANGE_PROPERTY) && stmt.getObject().asResource().isURIResource()) {
				retval.add(stmt.getObject().asResource().getURI());
			}
		}
		return retval;
	}

	/**
	 * @param classUri URI for the class containing the property
	 * @param propertyUri URI for the property with the (possible) cardinality restrictions
	 * @return true if the property has a max cardinality greater than 1 or does not have a max cardinality
	 * @throws SpdxRdfException on RDF related errors
	 */
	public boolean isList(String classUri, String propertyUri) throws SpdxRdfException {
		Objects.requireNonNull(classUri, "Missing class URI");
		Objects.requireNonNull(propertyUri, "Missing property URI");
		OntClass ontClass = model.getOntClass(classUri);
		if (Objects.isNull(ontClass)) {
			if (classUri.endsWith("GenericSpdxElement")) {
				ontClass = model.getOntClass(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_SPDX_ELEMENT);
			} else {
                logger.warn("{} is not an SPDX class", classUri);
				throw new SpdxRdfException(classUri + " is not an SPDX class");
			}
		}
		OntProperty property = model.getOntProperty(checkGetOwlUriFromRenamed(propertyUri));
		if (Objects.isNull(property)) {
            logger.warn("{} is not an SPDX property", propertyUri);
			throw new SpdxRdfException(propertyUri + " is not an SPDX property");
		}
		List<Statement> propertyRestrictions = new ArrayList<>();
		addPropertyRestrictions(ontClass, property, propertyRestrictions);
		if (propertyRestrictions.isEmpty()) {
			throw new SpdxRdfException(propertyUri + " was not found related to class "+classUri);
		}
		int minCardinality = -1;
		int maxCardinality = -1;
		int exactCardinality = -1;
		for (Statement stmt:propertyRestrictions) {
			if (stmt.getPredicate().equals(PROP_MIN_CARDINALITY) ||
					stmt.getPredicate().equals(PROP_MIN_QUAL_CARDINALITY)) {
				if (stmt.getObject().asLiteral().getInt() > minCardinality) {
					minCardinality = stmt.getObject().asLiteral().getInt();
				}
			} else if (stmt.getPredicate().equals(PROP_MAX_CARDINALITY) ||
					stmt.getPredicate().equals(PROP_MAX_QUAL_CARDINALITY)) {
				if (stmt.getObject().asLiteral().getInt() > maxCardinality) {
					maxCardinality = stmt.getObject().asLiteral().getInt();
				}
			} else if (stmt.getPredicate().equals(PROP_CARDINALITY) ||
					stmt.getPredicate().equals(PROP_QUAL_CARDINALITY)) {
				exactCardinality = stmt.getObject().asLiteral().getInt();
			}
		}
		return (exactCardinality == -1 && (maxCardinality == -1) || (maxCardinality > 1)) || exactCardinality > 1;
	}

	/**
	 * Adds restriction statement to the propertyRestrictions list
	 * @param ontClass class related to the property - all superclasses will be searched
	 * @param property property on which the restriction occurs
	 * @param propertyRestrictions list of all statements containing a restriction on the property within the class and all superclasses (including transitive superclasses)
	 */
	private void addPropertyRestrictions(OntClass ontClass, OntProperty property, List<Statement> propertyRestrictions) {	
		if (ontClass.isRestriction()) {
			if (model.listStatements(ontClass, ON_PROPERTY_PROPERTY, property).hasNext()) {
				// matches the property
				ontClass.listProperties().forEachRemaining(propertyRestrictions::add);
			}
		} else if (ontClass.isUnionClass()) {
			ontClass.asUnionClass().listOperands().forEachRemaining((OntClass operand) -> addPropertyRestrictions(operand, property, propertyRestrictions));
		} else {
			 ontClass.listSuperClasses().forEachRemaining((OntClass superClass) -> addPropertyRestrictions(superClass, property, propertyRestrictions));
		}
	}

}
