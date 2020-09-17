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

import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.listeners.ObjectListener;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.SpdxConstants;
import org.spdx.library.SpdxInvalidIdException;
import org.spdx.library.model.DuplicateSpdxIdException;
import org.spdx.library.model.IndividualUriValue;
import org.spdx.library.model.SimpleUriValue;
import org.spdx.library.model.SpdxInvalidTypeException;
import org.spdx.library.model.SpdxModelFactory;
import org.spdx.library.model.TypedValue;
import org.spdx.library.model.enumerations.SpdxEnumFactory;
import org.spdx.library.model.license.ListedLicenses;
import org.spdx.library.referencetype.ListedReferenceTypes;
import org.spdx.storage.IModelStore.IdType;
import org.spdx.storage.IModelStore.IModelStoreLock;

/**
 * Manages the reads/write/updates for a specific Jena model associated with a document
 * 
 * Since the ID's are not fully qualified with the URI, there is some complexity in this implementation.
 * 
 * It is assumed that all ID's are subjects that are either Anonymous or URI types.
 * If a URI type, the ID namespace is either the listed license namespace or the document URI.
 * 
 * @author Gary O'Neall
 *
 */
public class RdfSpdxDocumentModelManager implements IModelStoreLock {
	
	static final Logger logger = LoggerFactory.getLogger(RdfSpdxDocumentModelManager.class.getName());
	
	static final String RDF_TYPE = SpdxConstants.RDF_NAMESPACE + SpdxConstants.RDF_PROP_TYPE;
	
	static final Set<String> LISTED_LICENSE_CLASSES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(SpdxConstants.LISTED_LICENSE_URI_CLASSES)));
	
	public class RdfListIterator implements Iterator<Object> {
		
		NodeIterator listIterator;
		private Property property;

		public RdfListIterator(Resource idResource, Property property) {
			Objects.requireNonNull(idResource, "ID resource can not be null");
			Objects.requireNonNull(property, "Property resource can not be null");
			listIterator = model.listObjectsOfProperty(idResource, property);
			this.property = property;
		}

		@Override
		public boolean hasNext() {
			return listIterator.hasNext();
		}

		@Override
		public Object next() {
			RDFNode node = listIterator.next();
			if (Objects.isNull(node)) {
				return null;
			}
			try {
				Optional<Object> value = valueNodeToObject(node, property);
				if (value.isPresent()) {
					return value.get();
				} else {
					return null;
				}
			} catch (InvalidSPDXAnalysisException e) {
				throw new RuntimeException(e);
			}
		}
		
	}
	
	/**
	 * Listen for any new resources being created to make sure we update the next ID numbers
	 *
	 */
	class NextIdListener extends ObjectListener {
		@Override
		public void added(Object x) {
			if (x instanceof RDFNode) {
				checkAddNewId((RDFNode)x);
			} else if (x instanceof Statement) {
				Statement st = (Statement)x;
				if (Objects.nonNull(st.getSubject())) {
					checkAddNewId(st.getSubject());
				}
			}
		}
		
		@Override
		public void removed(Object x) {
			if (x instanceof RDFNode) {
				checkRemoveId((RDFNode)x);
			} else if (x instanceof Statement) {
				Statement st = (Statement)x;
				if (Objects.nonNull(st.getSubject())) {
					checkRemoveId(st.getSubject());
				}
			}
		}
		
		
	}
	
	private ReadWriteLock counterLock = new ReentrantReadWriteLock();
	private NextIdListener nextIdListener = new NextIdListener();
	
	private String documentUri;
	private Model model;
	/**
	 * Map of a lower case ID to the case sensitive ID
	 */
	private Map<String, String> idCaseSensitiveMap = new HashMap<String, String>();

	private int nextNextSpdxId = 1;

	private int nextNextDocumentId = 1;

	private int nextNextLicenseId = 1;

	private Property typeProperty;

	private String documentNamespace;

	/**
	 * @param documentUri Unique URI for this document
	 * @param model Model used to store this document
	 */
	public RdfSpdxDocumentModelManager(String documentUri, Model model) {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(model, "Missing required model");
		this.documentUri = documentUri;
		this.documentNamespace = documentUri + "#";
		this.model = model;
		typeProperty = model.createProperty(RDF_TYPE);
		model.register(nextIdListener);
		updateCounters();
	}
	
	/**
	 * Compare the next ID to the ID in the matcher.  Update if the matcher ID is greater than the current ID
	 * @param spdxRefMatcher Matcher containing the match to the SPDX ID
	 */
	private synchronized void checkUpdateNextSpdxId(Matcher spdxRefMatcher) {
		counterLock.writeLock().lock();
		try {
			String strNum = spdxRefMatcher.group(1);
			int num = Integer.parseInt(strNum);
			if (num >= this.nextNextSpdxId) {
				this.nextNextSpdxId = num + 1;
			}
		} finally {
			counterLock.writeLock().unlock();
		}
	}
	
	/**
	 * @return current SPDX ID and increments the ID
	 */
	private int getNextSpdxId() {
		counterLock.writeLock().lock();
		try {
			return this.nextNextSpdxId++;
		} finally {
			counterLock.writeLock().unlock();
		}
	}

	/**
	 * Compare the next ID to the ID in the matcher.  Update if the matcher ID is greater than the current ID
	 * @param documentRefMatcher
	 */
	private synchronized void checkUpdateNextDocumentId(Matcher documentRefMatcher) {
		counterLock.writeLock().lock();
		try {
			String strNum = documentRefMatcher.group(1);
			int num = Integer.parseInt(strNum);
			if (num >= this.nextNextDocumentId) {
				this.nextNextDocumentId = num + 1;
			}
		} finally {
			counterLock.writeLock().unlock();
		}
	}
	
	/**
	 * @return the current document ID and update the counter
	 */
	private int getNextDocumentId() {
		counterLock.writeLock().lock();
		try {
			return this.nextNextDocumentId++;
		} finally {
			counterLock.writeLock().unlock();
		}
	}

	/**
	 * Compare the next ID to the ID in the matcher.  Update if the matcher ID is greater than the current ID
	 * @param licenseRefMatcher
	 */
	private synchronized void checkUpdateLicenseId(Matcher licenseRefMatcher) {
		counterLock.writeLock().lock();
		try {
			String strNum = licenseRefMatcher.group(1);
			int num = Integer.parseInt(strNum);
			if (num >= this.nextNextLicenseId) {
				this.nextNextLicenseId = num + 1;
			}
		} finally {
			counterLock.writeLock().unlock();
		}
	}
	
	/**
	 * @return the current license ID and update the counter
	 */
	private int getNextLicenseId() {
		counterLock.writeLock().lock();
		try {
			return this.nextNextLicenseId++;
		} finally {
			counterLock.writeLock().unlock();
		}
	}
	
	/**
	 * Checks to see if all instances of an ID has been removed and removes the entry
	 * from the case insensitive map if there are no more instances
	 * @param node
	 */
	private synchronized void checkRemoveId(RDFNode node) {
		Objects.requireNonNull(node);
		if (node.isResource() && !model.containsResource(node) && !node.isAnon()) {
			String id = node.asResource().getLocalName();
			if (id.startsWith(SpdxConstants.EXTERNAL_DOC_REF_PRENUM) || id.startsWith(SpdxConstants.NON_STD_LICENSE_ID_PRENUM) ||
					id.startsWith(SpdxConstants.SPDX_ELEMENT_REF_PRENUM)) {
				this.idCaseSensitiveMap.remove(id.toLowerCase());
			}
		}
	}
	
	
	/**
	 * Checks for a new ID updating the next ID and map of case insensitive to case sensitive ID's
	 * @param node
	 */
	private void checkAddNewId(RDFNode node) {
		Objects.requireNonNull(node);
		if (node.isResource()) {
			if (node.isAnon()) {
				return;
			}
			String id = node.asResource().getLocalName();
			if (Objects.isNull(id)) {
				return;
			}
			if (id.startsWith(SpdxConstants.EXTERNAL_DOC_REF_PRENUM) || id.startsWith(SpdxConstants.NON_STD_LICENSE_ID_PRENUM) ||
					id.startsWith(SpdxConstants.SPDX_ELEMENT_REF_PRENUM)) {
				String previous = idCaseSensitiveMap.put(id.toLowerCase(), id);
				if (Objects.nonNull(previous)) {
					logger.warn("Possibly ambiguous ID being introduced.  "+previous+" is being raplaced by "+id);
				}
			}
			Matcher licenseRefMatcher = SpdxConstants.LICENSE_ID_PATTERN_NUMERIC.matcher(id);
			if (licenseRefMatcher.matches()) {
				checkUpdateLicenseId(licenseRefMatcher);
				return;
			}
			Matcher documentIdMatcher = RdfStore.DOCUMENT_ID_PATTERN_NUMERIC.matcher(id);
			if (documentIdMatcher.matches()) {
				checkUpdateNextDocumentId(documentIdMatcher);
				return;
			}
			Matcher spdxIdMatcher = RdfStore.SPDX_ID_PATTERN_NUMERIC.matcher(id);
			if (spdxIdMatcher.matches()) {
				checkUpdateNextSpdxId(spdxIdMatcher);
				return;
			}
		}
	}

	/**
	 * Read all ID's within this model and update all of the counters to be greater than the highest counter values found
	 */
	private void updateCounters() {
		model.enterCriticalSection(true);
		try {
			ResIterator iter = model.listSubjects();
			while (iter.hasNext()) {
				checkAddNewId(iter.next());
			}
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * @param id ID of a resource in the model
	 * @return true if the resource represented by the ID is present
	 */
	public boolean exists(String id) {
		Objects.requireNonNull(id, "Missing required ID");
		RDFNode resource;
		model.enterCriticalSection(true);
		try {
			if (isAnonId(id)) {
				try {
					resource = model.createResource(idToAnonId(id));
				} catch (SpdxInvalidIdException e) {
					logger.error("Error getting anonomous ID",e);
					throw new RuntimeException(e);
				}
			} else {
				// first try local to the document
				resource = ResourceFactory.createResource(idToUriInDocument(id));
				if (!model.containsResource(resource)) {
					// Try listed license URL
					resource = ResourceFactory.createResource(idToListedLicenseUri(id));
				}
			}
			Statement statement = model.getProperty(resource.asResource(), typeProperty);
			return Objects.nonNull(statement) && Objects.nonNull(statement.getObject());
		} finally {
			model.leaveCriticalSection();
		}
	}
	
	/**
	 * idToResource without type checking
	 * @param id
	 * @return Resource based on the ID
	 * @throws SpdxInvalidIdException
	*/
	private Resource idToResource(String id) throws SpdxInvalidIdException {
		Objects.requireNonNull(id, "Missing required ID");
		Resource resource;
		Optional<String> existingType = Optional.empty();
		if (isAnonId(id)) {
			resource = model.createResource(idToAnonId(id));
		} else {
			// first try local to the document
			resource = model.createResource(idToUriInDocument(id));
			if (!model.containsResource(resource)) {
				// Try listed license URL
				resource = model.createResource(idToListedLicenseUri(id));
				if (!model.containsResource(resource)) {
					if (ListedLicenses.getListedLicenses().isSpdxListedExceptionId(id)) {
						existingType = Optional.of(SpdxResourceFactory.typeToResource(
								SpdxConstants.CLASS_SPDX_LICENSE_EXCEPTION).getURI());
						// add exception type
//						resource.addProperty(typeProperty, SpdxResourceFactory.typeToResource(
//								SpdxConstants.CLASS_SPDX_LICENSE_EXCEPTION));
					} else if (ListedLicenses.getListedLicenses().isSpdxListedLicenseId(id)) {
						existingType = Optional.of(SpdxResourceFactory.typeToResource(
								SpdxConstants.CLASS_SPDX_LISTED_LICENSE).getURI());
						// add listed license type
//						resource.addProperty(typeProperty, SpdxResourceFactory.typeToResource(
//								SpdxConstants.CLASS_SPDX_LISTED_LICENSE));
					} else
						try {
							if (ListedReferenceTypes.getListedReferenceTypes().isListedReferenceType(new URI(SpdxConstants.SPDX_LISTED_REFERENCE_TYPES_PREFIX + id))) {
								existingType = Optional.of(SpdxResourceFactory.typeToResource(
										SpdxConstants.CLASS_SPDX_REFERENCE_TYPE).getURI());
//								resource.addProperty(typeProperty, SpdxResourceFactory.typeToResource(
//										SpdxConstants.CLASS_SPDX_REFERENCE_TYPE));
							} else {
								logger.error("ID "+id+" does not exist in the model.");
								throw new SpdxInvalidIdException("ID "+id+" does not exist in the model.");
							}
						} catch (URISyntaxException e) {
							logger.error("ID "+id+" does not exist in the model.");
							throw new SpdxInvalidIdException("ID "+id+" does not exist in the model.");
						}
				}
			}
		}
		if (!existingType.isPresent()) {
			Statement statement = model.getProperty(resource, typeProperty);
			if (statement == null || !statement.getObject().isResource()) {
				logger.error("ID "+id+" does not have a type.");
				throw new SpdxInvalidIdException("ID "+id+" does not have a type.");
			}
			existingType = SpdxResourceFactory.resourceToSpdxType(statement.getObject().asResource());
			if (!existingType.isPresent()) {
				logger.error("ID "+id+" does not have a type.");
				throw new SpdxInvalidIdException("ID "+id+" does not have a type.");
			}
		}
		return resource;
	}
	
	/**
	 * Convert an ID to a URI reference within the SPDX document
	 * @param documentUri
	 * @param id
	 * @return URI for the ID within the document
	 */
	private String idToUriInDocument(String id) {
		return documentUri + "#" + id;
	}
	
	/**
	 * Convert an ID string for an Anonymous type into an AnonId
	 * @param id
	 * @return
	 * @throws SpdxInvalidIdException 
	 */
	private AnonId idToAnonId(String id) throws SpdxInvalidIdException {
		Matcher matcher = RdfStore.ANON_ID_PATTERN.matcher(id);
		if (!matcher.matches()) {
			logger.error(id + " is not a valid Anonomous ID");
			throw new SpdxInvalidIdException(id + " is not a valid Anonomous ID");
		}
		String anon = matcher.group(1);
		return new AnonId(anon);
	}
	
	/**
	 * @param id
	 * @return true if the ID is an anonomous ID
	 */
	private boolean isAnonId(String id) {
		return RdfStore.ANON_ID_PATTERN.matcher(id).matches();
	}
	
	/**
	 * Convert a listed license to the full listed license URI
	 * @param licenseId
	 * @return listed license URI for the license ID
	 */
	private String idToListedLicenseUri(String licenseId) {
		return SpdxConstants.LISTED_LICENSE_NAMESPACE_PREFIX + licenseId;
	}

	/**
	 * Gets an existing or creates a new resource with and ID and type
	 * @param id ID used in the SPDX model
	 * @param type SPDX Type
	 * @return the resource
	 * @throws SpdxInvalidIdException
	 * @throws DuplicateSpdxIdException 
	 */
	protected Resource getOrCreate(String id, String type) throws SpdxInvalidIdException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(type, "Missing required type");
		Resource rdfType = SpdxResourceFactory.typeToResource(type);
		model.enterCriticalSection(false);
		try {
			if (LISTED_LICENSE_CLASSES.contains(type)) {
				return model.createResource(idToListedLicenseUri(id), rdfType);
			} else if (isAnonId(id)) {
				Resource retval = model.createResource(idToAnonId(id));
				retval.addProperty(typeProperty, rdfType);
				return retval;
			} else {
				return model.createResource(idToUriInDocument(id), rdfType);
			}
		} finally {
			model.leaveCriticalSection();
		}	
	}

	/**
	 * @param id
	 * @return all property names associated with the ID
	 * @throws SpdxInvalidIdException
	 */
	public List<String> getPropertyValueNames(String id) throws SpdxInvalidIdException {
		Objects.requireNonNull(id, "Missing required ID");
		Set<String> retval = new HashSet<String>();	// store unique values
		model.enterCriticalSection(true);
		try {
			Resource idResource = idToResource(id);
			
			idResource.listProperties().forEachRemaining(action -> {
				try {
					if (Objects.nonNull(action.getPredicate()) && !RDF_TYPE.equals(action.getPredicate().getURI())) {
						retval.add(resourceToPropertyName(action.getPredicate()));
					}
				} catch (SpdxRdfException e) {
					logger.warn("Skipping invalid property "+action.getObject().toString(),e);
				}
			});
			return Collections.unmodifiableList(new ArrayList<>(retval));
		} finally {
			model.leaveCriticalSection();
		}
	}
	
	/**
	 * Convert an RDFNode to a property name
	 * @param node
	 * @return
	 * @throws SpdxRdfException 
	 */
	protected static String resourceToPropertyName(RDFNode node) throws SpdxRdfException {
		Objects.requireNonNull(node, "Missing required node");
		if (node.isAnon()) {
			logger.error("Attempting to convert an anonomous node to a property name");
			throw new SpdxRdfException("Can not convert an anonomous node into a property name.");
		}
		if (node.isLiteral()) {
			logger.error("Attempting to convert an literal node to a property name: "+node.toString());
			throw new SpdxRdfException("Can not convert a literal node into a property name.");
		}
		String localName = node.asResource().getLocalName();
		if (SpdxOwlOntology.OWL_PROPERTY_TO_RENAMED_PROPERTY.containsKey(localName)) {
			return SpdxOwlOntology.OWL_PROPERTY_TO_RENAMED_PROPERTY.get(localName);
		} else {
			return node.asResource().getLocalName();
		}
	}

	/**
	 * Sets a property for an ID to a value
	 * @param id
	 * @param propertyName
	 * @param value
	 * @throws InvalidSPDXAnalysisException
	 */
	public void setValue(String id, String propertyName, Object value) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(value, "Missing required value");
		model.enterCriticalSection(false);
		try {
			if (SpdxConstants.PROP_DOCUMENT_NAMESPACE.equals(propertyName)) {
				// this is the namespace for the model itself
				setDefaultNsPrefix(value);
			} else {
				Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
				Resource idResource = idToResource(id);
				idResource.removeAll(property);
				idResource.addProperty(property, valueToNode(value));
			}
		} finally {
			model.leaveCriticalSection();
		}
	}
	
	/**
	 * Sets the default namespace prefix for the model
	 * @param uri
	 * @throws SpdxRdfException 
	 */
	private void setDefaultNsPrefix(Object oUri) throws SpdxRdfException {
		Objects.requireNonNull(oUri, "Can not set NS prefix to null");
		if (oUri instanceof URI) {
			model.setNsPrefix("", ((URI)oUri).toString());
		} else if (oUri instanceof String) {
			try {
				URI uri = new URI((String)oUri);
				model.setNsPrefix("", uri.toString());
			} catch (Exception ex) {
				logger.error("Invalid URI provided for model default namespace.", ex);
				throw new SpdxRdfException("Invalid URI provided for model default namespace.", ex);
			}
		} else {
			logger.error("Invalid type for URI provided for model default namespace: "+oUri.getClass().toString());
			throw new SpdxRdfException("Invalid type for URI provided for model default namespace: "+oUri.getClass().toString());			
		}
	}

	/**
	 * Converts a to an RDFNode based on the object type
	 * @param value
	 * @return
	 * @throws InvalidSPDXAnalysisException
	 */
	private RDFNode valueToNode(Object value) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(value, "Missing required value");
		if (value instanceof Boolean || value instanceof String || value instanceof Integer) {
			return model.createTypedLiteral(value);
		} else if (value instanceof TypedValue) {
			TypedValue tv = (TypedValue)value;
			return getOrCreate(tv.getId(), tv.getType());
		} else if (value instanceof IndividualUriValue) {
			return model.createResource(((IndividualUriValue)value).getIndividualURI());
		} else {
			logger.error("Value type "+value.getClass().getName()+" not supported.");
			throw new SpdxInvalidTypeException("Value type "+value.getClass().getName()+" not supported.");
		}
	}

	/**
	 * Get the value associated with the property associated with the ID
	 * @param id
	 * @param propertyName
	 * @return Optional value
	 * @throws InvalidSPDXAnalysisException
	 */
	public Optional<Object> getPropertyValue(String id, String propertyName) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		model.enterCriticalSection(true);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			NodeIterator iter = model.listObjectsOfProperty(idResource, property);
			if (!iter.hasNext()) {
				return Optional.empty();
			}
			Optional<Object> result = valueNodeToObject(iter.next(), property);
			if (iter.hasNext()) {
				logger.error("Error getting single value.  Multiple values for property "+propertyName+" ID "+id+".");
				throw new SpdxRdfException("Error getting single value.  Multiple values for property "+propertyName+" ID "+id+".");
			}
			return result;
		} finally {
			model.leaveCriticalSection();
		}
	}
	
	/**
	 * Convert a node in the RDF graph to a Java object
	 * @param propertyValue node containing the value
	 * @param property property which references the value
	 * @return
	 * @throws InvalidSPDXAnalysisException
	 */
	private Optional<Object> valueNodeToObject(RDFNode propertyValue, Property property) throws InvalidSPDXAnalysisException {
		if (Objects.isNull(propertyValue)) {
			return Optional.empty();
		}
		if (propertyValue.isLiteral()) {
			Object retval = propertyValue.asLiteral().getValue();
			if (retval instanceof String) {
				// need to check type and convert to boolean or integer
				Optional<Class<? extends Object>> propertyClass = SpdxOwlOntology.getSpdxOwlOntology().getPropertyClass(property);
				if (propertyClass.isPresent()) {
					if (Integer.class.equals(propertyClass.get())) {
						try {
							retval = Integer.parseInt((String)retval);
						} catch(NumberFormatException ex) {
							throw new InvalidSPDXAnalysisException("Invalid integer format for property "+property.toString(), ex);
						}
					} else if (Boolean.class.equals(propertyClass.get())) {
						try {
							retval = Boolean.valueOf((String)retval);
						} catch(Exception ex) {
							throw new InvalidSPDXAnalysisException("Invalid boolean format for property "+property.toString(), ex);
						}
					}
				}
			}
			return Optional.of(retval);
		}
		Resource valueType = propertyValue.asResource().getPropertyResourceValue(RDF.type);
		Optional<String> sValueType;
		if (Objects.nonNull(valueType)) {
			sValueType = SpdxResourceFactory.resourceToSpdxType(valueType);
		} else {
			sValueType = Optional.empty();
		}
		if (sValueType.isPresent()) {
			if (propertyValue.isURIResource() &&
					(SpdxConstants.CLASS_SPDX_REFERENCE_TYPE.equals(sValueType.get()) ||
					!this.documentNamespace.equals(propertyValue.asResource().getNameSpace()) &&
					SpdxConstants.EXTERNAL_SPDX_ELEMENT_URI_PATTERN.matcher(propertyValue.asResource().getURI()).matches())) {
				// External document referenced element
				return Optional.of(new SimpleUriValue(propertyValue.asResource().getURI()));
			} else {
				if (SpdxConstants.CLASS_SPDX_LICENSE.equals(sValueType.get())) {
					// change to a concrete class - right now, listed licenses are the only concrete class
					sValueType = Optional.of(SpdxConstants.CLASS_SPDX_LISTED_LICENSE);
				}
				return Optional.of(new TypedValue(resourceToId(propertyValue.asResource()), sValueType.get()));
			}
		} else {
			if (propertyValue.isURIResource()) {
				// Assume this is an individual value
				final String propertyUri = propertyValue.asResource().getURI();
				IndividualUriValue iv = new IndividualUriValue() {

					@Override
					public String getIndividualURI() {
						return propertyUri;
					}
					
				};
				return Optional.of(iv);
			} else {
				logger.error("Invalid resource type for value.  Must be a typed value, literal value or a URI Resource");
				throw new SpdxRdfException("Invalid resource type for value.  Must be a typed value, literal value or a URI Resource");
			}
		}
	}
/**
	 * Obtain an ID from a resource
	 * @param resource
	 * @return ID formatted appropriately for use outside the RdfStore
	 * @throws SpdxRdfException
	 */
	private String resourceToId(Resource resource) throws SpdxRdfException {
		Objects.requireNonNull(resource, "Mising required resource");
		if (resource.isAnon()) {
			return RdfStore.ANON_PREFIX + resource.getId();
		} else if (resource.isURIResource()) {
			return resource.getLocalName();
		} else {
			logger.error("Attempting to convert unsupported resource type to an ID: "+resource.toString());
			throw new SpdxRdfException("Only anonomous and URI resources can be converted to an ID");
		}
	}

	/**
	 * Get the next ID for the give ID type
	 * @param idType
	 * @return
	 * @throws InvalidSPDXAnalysisException
	 */
	public String getNextId(IdType idType) throws InvalidSPDXAnalysisException {
		switch (idType) {
		case Anonymous: return RdfStore.ANON_PREFIX+String.valueOf(model.createResource().getId());
		case LicenseRef: return SpdxConstants.NON_STD_LICENSE_ID_PRENUM+String.valueOf(getNextLicenseId());
		case DocumentRef: return SpdxConstants.EXTERNAL_DOC_REF_PRENUM+String.valueOf(getNextDocumentId());
		case SpdxId: return SpdxConstants.SPDX_ELEMENT_REF_PRENUM+String.valueOf(getNextSpdxId());
		case ListedLicense: {
			logger.error("Can not generate a license ID for a Listed License");
			throw new InvalidSPDXAnalysisException("Can not generate a license ID for a Listed License");
		}
		case Literal: {
			logger.error("Can not generate a license ID for a Literal");
			throw new InvalidSPDXAnalysisException("Can not generate a license ID for a Literal");
		}
		default: {
			logger.error("Unknown ID type for next ID: "+idType.toString());
			throw new InvalidSPDXAnalysisException("Unknown ID type for next ID: "+idType.toString());
		}
		}
	}

	/**
	 * Remove a property associated with a given ID and all values associated with that property
	 * @param id
	 * @param propertyName
	 * @throws InvalidSPDXAnalysisException
	 */
	public void removeProperty(String id, String propertyName) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing require ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		model.enterCriticalSection(false);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			model.removeAll(idResource, property, null);
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * Get all objects of TypedValue type from the model
	 * @param typeFilter if null, get all objects otherwise only return items that have a type equal to the filter
	 * @return Stream of all items matching the typeFilter
	 */
	public Stream<TypedValue> getAllItems(@Nullable String typeFilter) {
		String query = "SELECT ?s ?type  WHERE { ?s  <" + RDF_TYPE + ">  ?type }";
		QueryExecution qe = QueryExecutionFactory.create(query, model);
		ResultSet result = qe.execSelect();
		Stream<QuerySolution> querySolutionStream = StreamSupport
				.stream(Spliterators.spliteratorUnknownSize(result, Spliterator.ORDERED | Spliterator.NONNULL), false)
				.filter((QuerySolution qs) -> {
					RDFNode subject = qs.get("s");
					RDFNode type = qs.get("type");
					if (type.isResource() && subject.isResource()
						&& (!subject.isURIResource() || subject.asResource().getURI().startsWith(this.documentNamespace))) {
						Optional<String> sType = SpdxResourceFactory.resourceToSpdxType(type.asResource());
						if (sType.isPresent()) {
							if (Objects.isNull(typeFilter)) {
								return true;
							} else {
								return typeFilter.equals(sType.get());
							}
						}
					}
					return false;
				});
		return querySolutionStream.map((QuerySolution qs) -> {
			RDFNode subject = qs.get("s");
			RDFNode type = qs.get("type");
			try {
				return new TypedValue(resourceToId(subject.asResource()), SpdxResourceFactory.resourceToSpdxType(type.asResource()).get());
			} catch (Exception e) {
				logger.error("Unexpected exception converting to type");
				throw new RuntimeException(e);
			}
		});
	}

	/**
	 * Remove a specific value from a collection associated with an ID and property
	 * @param id
	 * @param propertyName
	 * @param value
	 * @return
	 * @throws InvalidSPDXAnalysisException
	 */
	public boolean removeValueFromCollection(String id, String propertyName, Object value) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(value, "Mising required value");
		model.enterCriticalSection(false);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			RDFNode rdfValue = valueToNode(value);
			if (model.contains(idResource, property, rdfValue)) {
				model.removeAll(idResource, property, rdfValue);
				return true;
			} else {
				return false;
			}
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * @param id
	 * @param propertyName
	 * @return the total number of objects associated with the ID and property
	 * @throws InvalidSPDXAnalysisException 
	 */
	public int collectionSize(String id, String propertyName) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		model.enterCriticalSection(true);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			return model.listObjectsOfProperty(idResource, property).toList().size();
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * @param id ID of the resource containing a collection property
	 * @param propertyName Name of the property with the collection
	 * @param value value to check
	 * @return true if the value exists in the model as the object of a property associated with the ID
	 * @throws InvalidSPDXAnalysisException
	 */
	public boolean collectionContains(String id, String propertyName, Object value) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(value, "Missing required value");
		model.enterCriticalSection(false);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			RDFNode rdfValue = valueToNode(value);
			return model.contains(idResource, property, rdfValue);
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * Clear (remove) all values assocociated with the ID and property
	 * @param id
	 * @param propertyName
	 * @throws InvalidSPDXAnalysisException
	 */
	public void clearValueCollection(String id, String propertyName) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		model.enterCriticalSection(true);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			model.removeAll(idResource, property, null);
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * Add value to the list of objects where the subject is the id and the predicate is the propertyName
	 * @param id
	 * @param propertyName
	 * @param value
	 * @return true if the collection was modified
	 * @throws InvalidSPDXAnalysisException
	 */
	public boolean addValueToCollection(String id, String propertyName, Object value) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(value, "Missing required value");
		model.enterCriticalSection(false);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			RDFNode nodeValue = valueToNode(value);
			if (model.contains(idResource, property, nodeValue)) {
				return false;
			} else {
				model.add(idResource, property, nodeValue);
				return true;
			}
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * @param id
	 * @param propertyName
	 * @return the list of values associated with id propertyName
	 * @throws InvalidSPDXAnalysisException
	 */
	public Iterator<Object> getValueList(String id, String propertyName) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Resource idResource = idToResource(id);
		Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
		return new RdfListIterator(idResource, property);
	}
	
	/**
	 * @param id
	 * @param propertyName
	 * @param clazz
	 * @return true if the OWL Ontology restrictions specifies that the class for the ID and property name allows it to be assigned to clazz
	 * @throws InvalidSPDXAnalysisException
	 */
	private boolean isAssignableTo(String id, String propertyName, Class<?> clazz) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(clazz, "Missing required class parameter");
		model.enterCriticalSection(false);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			Resource idClass = idToClass(idResource);
			List<String> classUriRestrictions = SpdxOwlOntology.getSpdxOwlOntology().getClassUriRestrictions(
					idClass.getURI(), property.getURI());
			if (!classUriRestrictions.isEmpty()) {
				for (String classUriRestriction:classUriRestrictions) {
					if (!clazz.isAssignableFrom(SpdxModelFactory.classUriToClass(classUriRestriction))) {
						return false;
					}
				}
			}
			List<String> dataUriRestrictions = SpdxOwlOntology.getSpdxOwlOntology().getDataUriRestrictions(
					idClass.getURI(), property.getURI());
			if (!dataUriRestrictions.isEmpty()) {
				for (String dataUriRestriction:dataUriRestrictions) {
					Class<?> javaClass = dataUriToClass(dataUriRestriction);
					if (Objects.isNull(javaClass) || !clazz.isAssignableFrom(dataUriToClass(dataUriRestriction))) {
						if (URI.class.equals(javaClass)) {
							return clazz.isAssignableFrom(String.class);	//TODO: support the URI class
						} else {
							return false;
						}
					}
				}
			}
			if (dataUriRestrictions.isEmpty() && classUriRestrictions.isEmpty()) {
				throw new MissingDataTypeAndClassRestriction("Missing datatype and class restrictions for class " +
							idClass.getURI()+ " and property " + property.getURI());
			} else {
				return true;
			}
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * @param dataUri
	 * @return the class associated with the data URI
	 */
	private Class<?> dataUriToClass(String dataUri) throws SpdxRdfException {
		Objects.requireNonNull(dataUri, "Missing required data URI restriction");
		int poundIndex = dataUri.lastIndexOf('#');
		if (poundIndex < 1) {
			throw new SpdxRdfException("Invalid data URI "+dataUri);
		}
		RDFDatatype dataType = TypeMapper.getInstance().getTypeByName(dataUri);
		return dataType.getJavaClass();
	}

	/**
	 * @param id
	 * @param propertyName
	 * @param clazz
	 * @return true if all collection members associated with the property of id is assignable to clazz
	 * @throws InvalidSPDXAnalysisException
	 */
	public boolean isCollectionMembersAssignableTo(String id, String propertyName, Class<?> clazz) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(clazz, "Missing required class parameter");
		try {
			return isAssignableTo(id, propertyName, clazz);
		} catch (MissingDataTypeAndClassRestriction ex) {
			logger.warn("Error determining assingability by OWL ontology.  Checking actual properties.",ex);
		}
		// NOTE: we only get here if there is an error taking the ontology approach
		model.enterCriticalSection(false);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			NodeIterator iter = model.listObjectsOfProperty(idResource, property);
			while (iter.hasNext()) {
				RDFNode node = iter.next();
				Optional<Object> value = valueNodeToObject(node, property);
				if (value.isPresent() && !clazz.isAssignableFrom(value.get().getClass())) {
					if (!value.isPresent()) {
						return false;
					}
					if (value.get() instanceof TypedValue) {
						try {
							if (!clazz.isAssignableFrom(SpdxModelFactory.typeToClass(((TypedValue)value.get()).getType()))) {
								return false;
							}
						} catch (InvalidSPDXAnalysisException e) {
							logger.error("Error converting typed value to class",e);
							return false;
						} // else continue looping through other list values
					} else if (value.get() instanceof IndividualUriValue) {
						String uri = ((IndividualUriValue)value.get()).getIndividualURI();
						Enum<?> spdxEnum = SpdxEnumFactory.uriToEnum.get(uri);
						if (Objects.nonNull(spdxEnum)) {
							if (!clazz.isAssignableFrom(spdxEnum.getClass())) {
								return false;
							}
						} else if (!(SpdxConstants.URI_VALUE_NOASSERTION.equals(uri) ||
								SpdxConstants.URI_VALUE_NONE.equals(uri))) {
							return false;
						}
					} else {
						return false;
					}
				}
			}
			return true;
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * @param id
	 * @param propertyName
	 * @param clazz
	 * @return true if there is a property value assignable to clazz
	 * @throws InvalidSPDXAnalysisException
	 */
	public boolean isPropertyValueAssignableTo(String id, String propertyName, Class<?> clazz) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		try {
			return isAssignableTo(id, propertyName, clazz);
		} catch (MissingDataTypeAndClassRestriction ex) {
			logger.warn("Error determining assingability by OWL ontology.  Checking actual properties.",ex);
		}
		// NOTE: we only get here if the OWL schema approach didn't work
		model.enterCriticalSection(false);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			NodeIterator iter = model.listObjectsOfProperty(idResource, property);
			if (!iter.hasNext()) {
				return false;	// I guess you can assign anything and be compatible?
			}
			Optional<Object> objectValue = valueNodeToObject(iter.next(), property);
			if (iter.hasNext()) {
				logger.error("Error getting single value.  Multiple values for property "+propertyName+" ID "+id+".");
				throw new SpdxRdfException("Error getting single value.  Multiple values for property "+propertyName+" ID "+id+".");
			}
			if (objectValue.isPresent()) {
				if (clazz.isAssignableFrom(objectValue.get().getClass())) {
					return true;
				}
				if (objectValue.get() instanceof TypedValue) {
					try {
						return clazz.isAssignableFrom(SpdxModelFactory.typeToClass(((TypedValue)objectValue.get()).getType()));
					} catch (InvalidSPDXAnalysisException e) {
						logger.error("Error converting typed value to class",e);
						return false;
					}
				}
			}
			if (objectValue.get() instanceof IndividualUriValue) {
				String uri = ((IndividualUriValue)objectValue.get()).getIndividualURI();
				if (SpdxConstants.URI_VALUE_NOASSERTION.equals(uri)) {
					return true;
				}
				if (SpdxConstants.URI_VALUE_NONE.equals(uri)) {
					return true;
				}
				Enum<?> spdxEnum = SpdxEnumFactory.uriToEnum.get(uri);
				if (Objects.nonNull(spdxEnum)) {
					return clazz.isAssignableFrom(spdxEnum.getClass());
				} else {
					return false;
				}
			}
			return false;
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * @param id
	 * @param propertyName
	 * @return true if the property of associated with id contains more than one object
	 * @throws InvalidSPDXAnalysisException
	 */
	public boolean isCollectionProperty(String id, String propertyName) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		model.enterCriticalSection(false);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			Resource idClass = idToClass(idResource);
			return SpdxOwlOntology.getSpdxOwlOntology().isList(idClass.getURI(), property.getURI());
		} finally {
			model.leaveCriticalSection();
		}
	}
	

	private Resource idToClass(Resource idResource) throws SpdxInvalidIdException {
		Statement statement = model.getProperty(idResource, typeProperty);
		if (statement == null || !statement.getObject().isResource()) {
			logger.error("ID "+idResource+" does not have a type.");
			throw new SpdxInvalidIdException("ID "+idResource+" does not have a type.");
		}
		return statement.getObject().asResource();
	}

	public void close() {
		this.model.unregister(nextIdListener);
	}

	public IModelStoreLock enterCriticalSection(boolean readLockRequested) {
		this.model.enterCriticalSection(readLockRequested);
		return this;
	}

	@Override
	public void unlock() {
		this.model.leaveCriticalSection();
	}

	public void serialize(OutputStream stream, OutputFormat outputFormat) {
		this.model.write(stream, outputFormat.getType());
	}

	/**
	 * Translate a case insensitive ID into a case sensitive ID
	 * @param caseInsensisitiveId
	 * @return case sensitive ID
	 */
	public Optional<String> getCasesensitiveId(String caseInsensisitiveId) {
		return Optional.ofNullable(this.idCaseSensitiveMap.get(caseInsensisitiveId.toLowerCase()));
	}

	/**
	 * Delete the entire resource and all statements
	 * @param id
	 * @throws SpdxInvalidIdException 
	 */
	public void delete(String id) throws SpdxInvalidIdException {
		Resource idResource = idToResource(id);
		model.removeAll(idResource, null, null);
	}

	/**
	 * @param id
	 * @return Type typed value for the ID if it exists and is of an SPDX type, otherwise empty
	 * @throws InvalidSPDXAnalysisException
	 */
	public Optional<TypedValue> getTypedValue(String id) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		model.enterCriticalSection(false);
		try {
			try {
				Resource idResource = idToResource(id);
				Resource idClass = idToClass(idResource);
				if (Objects.isNull(idClass)) {
					return Optional.empty();
				}
				Class<?> clazz = SpdxModelFactory.classUriToClass(idClass.getURI());
				if (Objects.isNull(clazz)) {
					return Optional.empty();
				}
				String type = SpdxModelFactory.SPDX_CLASS_TO_TYPE.get(clazz);
				if (Objects.isNull(type)) {
					return Optional.empty();
				}
				return Optional.of(new TypedValue(id, type));
			} catch(SpdxInvalidIdException ex) {
				return Optional.empty();
			}
		} finally {
			model.leaveCriticalSection();
		}	
	}
}
