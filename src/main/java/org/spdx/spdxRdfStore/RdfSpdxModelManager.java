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

import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import org.apache.jena.rdf.listeners.StatementListener;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.core.IndividualUriValue;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.SimpleUriValue;
import org.spdx.core.SpdxIdNotFoundException;
import org.spdx.core.SpdxInvalidIdException;
import org.spdx.core.SpdxInvalidTypeException;
import org.spdx.core.TypedValue;
import org.spdx.library.ListedLicenses;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.SpdxModelFactoryCompatV2;
import org.spdx.library.model.v3_0_1.SpdxEnumFactory;
import org.spdx.library.referencetype.ListedReferenceTypes;
import org.spdx.storage.IModelStore.IdType;
import org.spdx.storage.compatv2.CompatibleModelStoreWrapper;
import org.spdx.storage.IModelStore.IModelStoreLock;

/**
 * Manages the reads/write/updates for a specific Jena model associated with a document
 * <p>
 * Since the IDs are not fully qualified with the URI, there is some complexity in this implementation.
 * <p>
 * It is assumed that all IDs are subjects that are either Anonymous or URI types.
 * If a URI type, the ID namespace is either the listed license namespace or the document URI.
 * 
 * @author Gary O'Neall
 */
@SuppressWarnings("LoggingSimilarMessage")
public class RdfSpdxModelManager implements IModelStoreLock {
	
	static final Logger logger = LoggerFactory.getLogger(RdfSpdxModelManager.class.getName());
	
	static final String RDF_TYPE = SpdxConstantsCompatV2.RDF_NAMESPACE + SpdxConstantsCompatV2.RDF_PROP_TYPE.getName();
	
	static final Set<String> LISTED_LICENSE_CLASSES = Set.of(SpdxConstantsCompatV2.LISTED_LICENSE_URI_CLASSES);

    private static final String HTTPS_LISTED_LICENSE_NAMESPACE_PREFIX = SpdxConstantsCompatV2.LISTED_LICENSE_NAMESPACE_PREFIX.replaceAll("http:", "https:");
    
    /**
     * subset of the listed license namespace to be used for matching
     */
    private static final CharSequence SPDX_LISTED_LICENSE_SUBPREFIX = "://spdx.org/licenses/";

	/**
	 * An iterator for traversing RDF list objects associated with a specific property of a resource
	 */
	public class RdfListIterator implements Iterator<Object> {

		final NodeIterator listIterator;
		private final Property property;

		/**
		 * Constructs an RdfListIterator for a given resource and property
		 *
		 * @param idResource the resource whose property values are to be iterated
		 * @param property the property whose values are to be iterated
		 * @throws NullPointerException if idResource or property is null
		 */
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
                return value.orElse(null);
			} catch (InvalidSPDXAnalysisException e) {
				throw new RuntimeException(e);
			}
		}	
	}
	
	 /**
     * Listen for any new resources being created to make sure we update the next ID numbers
     *
     */
	class NextIdListener extends StatementListener {
	    
	    @Override
	    public void addedStatement(Statement s) {
	        if (Objects.nonNull(s.getSubject())) {
                StmtIterator iter = s.getModel().listStatements(s.getSubject(), null, (RDFNode)null);
                if (iter.hasNext()) {
                    iter.next();    // we have at least one statement with this subject
                    if (!iter.hasNext()) {
                        // First added statement with this subject
                        checkAddNewId(s.getSubject());
                    }
                }
            }
	    }
	    
	    @Override
	    public void removedStatement(Statement s) {
            if (Objects.nonNull(s.getSubject())) {
                StmtIterator iter = s.getModel().listStatements(s.getSubject(), null, (RDFNode)null);
                if (!iter.hasNext()) {
                    checkRemoveId(s.getSubject());
                }
            }
	    }
	}
	
	
	private final ReadWriteLock counterLock = new ReentrantReadWriteLock();
	private final NextIdListener nextIdListener = new NextIdListener();
	
	private final String documentUri;
	final protected Model model;
	/**
	 * Map of a lower case ID to the case-sensitive ID
	 */
	private final Map<String, String> idCaseSensitiveMap = new HashMap<>();

	private int nextNextSpdxId = 1;

	private int nextNextDocumentId = 1;

	private int nextNextLicenseId = 1;

	private final Property typeProperty;

	private final String documentNamespace;
	
	private String specVersion;

	/**
	 * @param documentUri Unique URI for this document
	 * @param model Model used to store this document
	 */
	public RdfSpdxModelManager(String documentUri, Model model) {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(model, "Missing required model");
		this.documentUri = documentUri;
		this.documentNamespace = documentUri + "#";
		this.model = model;
		typeProperty = model.createProperty(RDF_TYPE);
		model.register(nextIdListener);
		updateCounters();
		if (this.exists(SpdxConstantsCompatV2.SPDX_DOCUMENT_ID)) {
			try {
				this.specVersion = (String)getPropertyValue(SpdxConstantsCompatV2.SPDX_DOCUMENT_ID, 
						SpdxConstantsCompatV2.PROP_SPDX_SPEC_VERSION.getName()).orElse(CompatibleModelStoreWrapper.LATEST_SPDX_2X_VERSION);
			} catch (InvalidSPDXAnalysisException e) {
				logger.warn("Exception getting the SPDX spec version",e);
				this.specVersion = CompatibleModelStoreWrapper.LATEST_SPDX_2X_VERSION;
			}
		} else {
			this.specVersion = CompatibleModelStoreWrapper.LATEST_SPDX_2X_VERSION;
		}
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
	 * @param documentRefMatcher matcher for matching document references
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
	 * @param licenseRefMatcher  matcher for matching license references
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
	 * from the case-insensitive map if there are no more instances
	 * @param node RDF node
	 */
	private synchronized void checkRemoveId(RDFNode node) {
		Objects.requireNonNull(node);
		if (node.isResource() && !model.containsResource(node) && !node.isAnon()) {
			String id = node.asResource().getLocalName();
			if (id.startsWith(SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM) || id.startsWith(SpdxConstantsCompatV2.NON_STD_LICENSE_ID_PRENUM) ||
					id.startsWith(SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM)) {
				this.idCaseSensitiveMap.remove(id.toLowerCase());
			}
		}
	}
	
	
	/**
	 * Checks for a new ID updating the next ID and map of case-insensitive to case-sensitive ID's
	 * @param node RDF node
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
			if (id.startsWith(SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM) || id.startsWith(SpdxConstantsCompatV2.NON_STD_LICENSE_ID_PRENUM) ||
					id.startsWith(SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM)) {
				String previous = idCaseSensitiveMap.put(id.toLowerCase(), id);
				if (Objects.nonNull(previous)) {
                    logger.warn("Possibly ambiguous ID being introduced.  {} is being replaced by {}", previous, id);
				}
			}
			Matcher licenseRefMatcher = RdfStore.LICENSE_ID_PATTERN_GENERATED.matcher(id);
			if (licenseRefMatcher.matches()) {
				checkUpdateLicenseId(licenseRefMatcher);
				return;
			}
			Matcher documentIdMatcher = RdfStore.DOCUMENT_ID_PATTERN_GENERATED.matcher(id);
			if (documentIdMatcher.matches()) {
				checkUpdateNextDocumentId(documentIdMatcher);
				return;
			}
			Matcher spdxIdMatcher = RdfStore.SPDX_ID_PATTERN_GENERATED.matcher(id);
			if (spdxIdMatcher.matches()) {
				checkUpdateNextSpdxId(spdxIdMatcher);
			}
		}
	}

	/**
	 * Read all ID's within this model and update all the counters to be greater than the highest counter values found
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
                    logger.error("Error getting anonymous ID",e);
                    throw new RuntimeException(e);
                }
            } else {
                // first try local to the document
                resource = ResourceFactory.createResource(idToUriInDocument(id));
                if (!model.containsResource(resource)) {
                    // Try listed license URL
                    resource = ResourceFactory.createResource(SpdxConstantsCompatV2.LISTED_LICENSE_NAMESPACE_PREFIX + id);
                }
                if (!model.containsResource(resource)) {
                    // Try listed license URL with HTTPS prefix - not correct, but we'll go ahead and match as a listed license
                    resource = ResourceFactory.createResource(HTTPS_LISTED_LICENSE_NAMESPACE_PREFIX + id);
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
	 * @param id SPDX ID
	 * @return Resource based on the ID
	 * @throws SpdxInvalidIdException On SPDX parsing errors
	*/
	private Resource idToResource(String id) throws SpdxInvalidIdException {
		Objects.requireNonNull(id, "Missing required ID");
		Resource resource;
		Optional<String> existingType;
		if (isAnonId(id)) {
			resource = model.createResource(idToAnonId(id));
		} else {
			// first try local to the document
			resource = model.createResource(idToUriInDocument(id));
			if (model.containsResource(resource)) {
				// Confirm that there is a type
				Statement statement = model.getProperty(resource, typeProperty);
				if (statement == null || !statement.getObject().isResource()) {
                    logger.error("ID {} does not have a type.", id);
					throw new SpdxInvalidIdException("ID "+id+" does not have a type.");
				}
				existingType = SpdxResourceFactory.resourceToSpdxType(statement.getObject().asResource());
				if (existingType.isEmpty()) {
                    logger.error("ID {} does not have a type.", id);
					throw new SpdxInvalidIdException("ID "+id+" does not have a type.");
				}
			} else {
				// Try listed license URL
				resource = model.createResource(SpdxConstantsCompatV2.LISTED_LICENSE_NAMESPACE_PREFIX + id);
				if (!model.containsResource(resource)) {
				    // Check to see if it is defined with "https" instead of "http" - technically incorrect
				    // but, we'll allow it for compatibility
				    resource = model.createResource(HTTPS_LISTED_LICENSE_NAMESPACE_PREFIX + id);
				}
				if (!model.containsResource(resource)) {
					// Check for listed license ID's - these URI's may not be defined in the local model but are still valid
					if (!ListedLicenses.getListedLicenses().isSpdxListedExceptionId(id) && 
							!ListedLicenses.getListedLicenses().isSpdxListedLicenseId(id)) {
						// Try listed reference types
						try {
							if (!ListedReferenceTypes.getListedReferenceTypes().isListedReferenceType(new URI(SpdxConstantsCompatV2.SPDX_LISTED_REFERENCE_TYPES_PREFIX + id))) {
                                logger.error("ID {} does not exist in the model.", id);
								throw new SpdxInvalidIdException("ID "+id+" does not exist in the model.");
							}
						} catch (URISyntaxException e) {
                            logger.error("ID {} does not exist in the model.", id);
							throw new SpdxInvalidIdException("ID "+id+" does not exist in the model.");
						}
					}
				}
			}
		}
		return resource;
	}
	
	/**
	 * Convert an ID to a URI reference within the SPDX document
	 * @param id SPDX ID
	 * @return URI for the ID within the document
	 */
	private String idToUriInDocument(String id) {
        return documentUri + "#" + URLEncoder.encode(id, StandardCharsets.UTF_8);
    }
	
	/**
	 * Convert an ID string for an Anonymous type into an AnonId
	 * @param id SPDX ID
	 * @return an AnonId
	 * @throws SpdxInvalidIdException On SPDX parsing errors
	 */
	private AnonId idToAnonId(String id) throws SpdxInvalidIdException {
		Matcher matcher = RdfStore.ANON_ID_PATTERN.matcher(id);
		if (!matcher.matches()) {
            logger.error("{} is not a valid Anonymous ID", id);
			throw new SpdxInvalidIdException(id + " is not a valid Anonymous ID");
		}
		String anon = matcher.group(1);
		return new AnonId(anon);
	}
	
	/**
	 * @param id SPDX ID
	 * @return true if the ID is an anonymous ID
	 */
	private boolean isAnonId(String id) {
		return RdfStore.ANON_ID_PATTERN.matcher(id).matches();
	}

	/**
	 * Gets an existing or creates a new resource with and ID and type
	 * @param objectUri uri or anon type string
	 * @param type SPDX Type
	 * @return the resource
	 * @throws SpdxInvalidIdException on invalid SPDX id
	 */
	protected Resource getOrCreate(String objectUri, String type) throws SpdxInvalidIdException {
		Objects.requireNonNull(objectUri, "Missing required object URI");
		Objects.requireNonNull(type, "Missing required type");
		Resource rdfType = SpdxResourceFactory.typeToResource(type);
		model.enterCriticalSection(false);
		try {
			if (LISTED_LICENSE_CLASSES.contains(type)) {
				if (!objectUri.startsWith(SpdxConstantsCompatV2.LISTED_LICENSE_NAMESPACE_PREFIX)) {
					String id = objectUri.substring(objectUri.indexOf('#')+1);
					return model.createResource(SpdxConstantsCompatV2.LISTED_LICENSE_NAMESPACE_PREFIX + id, rdfType);
				} else {
					return model.createResource(objectUri, rdfType);
				}
			} else if (isAnonId(objectUri)) {
				Resource retval = model.createResource(idToAnonId(objectUri));
				retval.addProperty(typeProperty, rdfType);
				return retval;
			} else {
				return model.createResource(objectUri, rdfType);
			}
		} finally {
			model.leaveCriticalSection();
		}	
	}

	/**
	 * @param id SPDX ID
	 * @return all property names associated with the ID
	 * @throws SpdxInvalidIdException On SPDX parsing errors
	 */
	public List<String> getPropertyValueNames(String id) throws SpdxInvalidIdException {
		Objects.requireNonNull(id, "Missing required ID");
		Set<String> retval = new HashSet<>();	// store unique values
		model.enterCriticalSection(true);
		try {
			Resource idResource = idToResource(id);
			
			idResource.listProperties().forEachRemaining(action -> {
				try {
					if (Objects.nonNull(action.getPredicate()) && !RDF_TYPE.equals(action.getPredicate().getURI())) {
						retval.add(resourceToPropertyName(action.getPredicate()));
					}
				} catch (SpdxRdfException e) {
                    logger.warn("Skipping invalid property {}", action.getObject().toString(), e);
				}
			});
			return List.copyOf(retval);
		} finally {
			model.leaveCriticalSection();
		}
	}
	
	/**
	 * Convert an RDFNode to a property name
	 * @param node RDF node for a resource
	 * @return a property name
	 * @throws SpdxRdfException On SPDX parsing errors
	 */
	protected static String resourceToPropertyName(RDFNode node) throws SpdxRdfException {
		Objects.requireNonNull(node, "Missing required node");
		if (node.isAnon()) {
			logger.error("Attempting to convert an anonymous node to a property name");
			throw new SpdxRdfException("Can not convert an anonymous node into a property name.");
		}
		if (node.isLiteral()) {
            logger.error("Attempting to convert an literal node to a property name: {}", node);
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
	 * @param id SPDX ID
	 * @param propertyName property name
	 * @param value value to set
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
	 */
	public void setValue(String id, String propertyName, Object value) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(value, "Missing required value");
		model.enterCriticalSection(false);
		try {
			if (SpdxConstantsCompatV2.PROP_DOCUMENT_NAMESPACE.getName().equals(propertyName)) {
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
	 * @param oUri URI for the default namespace
	 * @throws SpdxRdfException on RDF errors
	 */
	private void setDefaultNsPrefix(Object oUri) throws SpdxRdfException {
		Objects.requireNonNull(oUri, "Can not set NS prefix to null");
		if (oUri instanceof URI) {
			model.setNsPrefix("", oUri.toString());
		} else if (oUri instanceof String) {
			try {
				URI uri = new URI((String)oUri);
				model.setNsPrefix("", uri.toString());
			} catch (Exception ex) {
				logger.error("Invalid URI provided for model default namespace.", ex);
				throw new SpdxRdfException("Invalid URI provided for model default namespace.", ex);
			}
		} else {
            logger.error("Invalid type for URI provided for model default namespace: {}", oUri.getClass());
			throw new SpdxRdfException("Invalid type for URI provided for model default namespace: "+ oUri.getClass());
		}
	}

	/**
	 * Converts to an RDFNode based on the object type
	 * @param value value to set
	 * @return an RDFNode based on the object type
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
	 */
	private RDFNode valueToNode(Object value) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(value, "Missing required value");
		if (value instanceof Boolean || value instanceof String || value instanceof Integer) {
			return model.createTypedLiteral(value);
		} else if (value instanceof TypedValue) {
			TypedValue tv = (TypedValue)value;
			return getOrCreate(tv.getObjectUri(), tv.getType());
		} else if (value instanceof IndividualUriValue) {
			return model.createResource(((IndividualUriValue)value).getIndividualURI());
		} else {
            logger.error("Value type {} not supported.", value.getClass().getName());
			throw new SpdxInvalidTypeException("Value type "+value.getClass().getName()+" not supported.");
		}
	}

	/**
	 * Get the value associated with the property associated with the ID
	 * @param id SPDX ID
	 * @param propertyName property name
	 * @return Optional value
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
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
				if (isListedLicenseOrException(idResource)) {
					// If there is no locally stored property for a listed license or exception
					// fetch it from listed licenses store
					try {
						return ListedLicenses.getListedLicenses().getLicenseModelStoreCompatV2()
								.getValue(HTTPS_LISTED_LICENSE_NAMESPACE_PREFIX + id, 
										CompatibleModelStoreWrapper.propNameToPropDescriptor(propertyName));
					} catch(SpdxIdNotFoundException e) {
						return Optional.empty();
					}
				} else {
					return Optional.empty();
				}
			}
			Optional<Object> result = valueNodeToObject(iter.next(), property);
			if (iter.hasNext()) {
                logger.error("Error getting single value.  Multiple values for property {} ID {}.", propertyName, id);
				throw new SpdxRdfException("Error getting single value.  Multiple values for property "+propertyName+" ID "+id+".");
			}
			return result;
		} finally {
			model.leaveCriticalSection();
		}
	}
	
	/**
	 * @param idResource Resource for the ID
	 * @return true if the type of the ID is a ListedLicenseException or a Listed License
	 */
	private boolean isListedLicenseOrException(Resource idResource) {
		Resource valueType = idResource.getPropertyResourceValue(RDF.type);
		if (Objects.isNull(valueType)) {
			return false;
		}
		Optional<String> sValueType = SpdxResourceFactory.resourceToSpdxType(valueType);
		if (sValueType.isEmpty()) {
			return false;
		}
		String sValueTypeStr = sValueType.get();
		return SpdxConstantsCompatV2.CLASS_SPDX_LISTED_LICENSE.equals(sValueTypeStr) || 
				SpdxConstantsCompatV2.CLASS_SPDX_LISTED_LICENSE_EXCEPTION.equals(sValueTypeStr);
	}

	/**
	 * Convert a node in the RDF graph to a Java object
	 * @param propertyValue node containing the value
	 * @param property property which references the value
	 * @return a Java object based on the propertyValue
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
	 */
	private Optional<Object> valueNodeToObject(RDFNode propertyValue, Property property) throws InvalidSPDXAnalysisException {
		if (Objects.isNull(propertyValue)) {
			return Optional.empty();
		}
		if (propertyValue.isLiteral()) {
		    return literalNodeToObject(propertyValue.asLiteral().getValue(), property);
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
					(SpdxConstantsCompatV2.CLASS_SPDX_REFERENCE_TYPE.equals(sValueType.get()) ||
					!this.documentNamespace.equals(propertyValue.asResource().getNameSpace()) &&
					SpdxConstantsCompatV2.EXTERNAL_SPDX_ELEMENT_URI_PATTERN.matcher(propertyValue.asResource().getURI()).matches())) {
				// External document referenced element
				return Optional.of(new SimpleUriValue(propertyValue.asResource().getURI()));
			} else {
				if (SpdxConstantsCompatV2.CLASS_SPDX_LICENSE.equals(sValueType.get())) {
					// change to a concrete class - right now, listed licenses are the only concrete class
					sValueType = Optional.of(SpdxConstantsCompatV2.CLASS_SPDX_LISTED_LICENSE);
				}
				return Optional.of(new TypedValue(resourceToObjectUri(propertyValue.asResource()), sValueType.get(), specVersion));
			}
		} else {
			if (propertyValue.isURIResource()) {
				final String propertyUri = propertyValue.asResource().getURI();
				if (propertyUri.contains(SPDX_LISTED_LICENSE_SUBPREFIX) && !propertyUri.endsWith(SpdxConstantsCompatV2.NONE_VALUE) && 
						!propertyUri.endsWith(SpdxConstantsCompatV2.NOASSERTION_VALUE)) {
					// Must be a listed license - Note that the URI may be http: or https:
					return Optional.of(new TypedValue(resourceToObjectUri(propertyValue.asResource()), 
							SpdxConstantsCompatV2.CLASS_SPDX_LISTED_LICENSE, specVersion));
				} else {
					// Assume this is an individual value
					IndividualUriValue iv = () -> propertyUri;
					return Optional.of(iv);
				}
			} else {
				logger.error("Invalid resource type for value.  Must be a typed value, literal value or a URI Resource");
				throw new SpdxRdfException("Invalid resource type for value.  Must be a typed value, literal value or a URI Resource");
			}
		}
	}
	
    /**
     * Translate a literal node to an object based on the property and literal value type
     * @param literalValue node value for the literal
     * @param property property associated with the literal value
     * @return the object associated with the literal value
     * @throws InvalidSPDXAnalysisException On SPDX parsing errors
     */
    private Optional<Object> literalNodeToObject(Object literalValue, Property property) throws InvalidSPDXAnalysisException {
        if (literalValue instanceof String) {
            // need to check type and convert to boolean or integer
            Optional<Class<?>> propertyClass = SpdxOwlOntology.getSpdxOwlOntology().getPropertyClass(property);
            if (propertyClass.isPresent()) {
                if (Integer.class.equals(propertyClass.get())) {
                    try {
                        return Optional.of(Integer.parseInt((String)literalValue));
                    } catch(NumberFormatException ex) {
                        throw new InvalidSPDXAnalysisException("Invalid integer format for property "+ property, ex);
                    }
                } else if (Boolean.class.equals(propertyClass.get())) {
                    try {
                        return Optional.of(Boolean.valueOf((String)literalValue));
                    } catch(Exception ex) {
                        throw new InvalidSPDXAnalysisException("Invalid boolean format for property "+ property, ex);
                    }
                } else {
                    return Optional.of(literalValue);
                }
            } else {
                return Optional.of(literalValue);
            }
        } else {
            return Optional.of(literalValue);
        }
    }
    
    /**
	 * Obtain an ObjectUri from a resource
	 * @param resource rRDF resource
	 * @return ID formatted appropriately for use outside the RdfStore
	 * @throws SpdxRdfException On SPDX parsing errors
	 */
	private String resourceToObjectUri(Resource resource) throws SpdxRdfException {
		Objects.requireNonNull(resource, "Missing required resource");
		if (resource.isAnon()) {
			return RdfStore.ANON_PREFIX + resource.getId();
		} else if (resource.isURIResource()) {
			return resource.getURI();
		} else {
            logger.error("Attempting to convert unsupported resource type to an ID: {}", resource);
			throw new SpdxRdfException("Only anonymous and URI resources can be converted to an ID");
		}
	}

	/**
	 * Get the next ID for the give ID type
	 * @param idType type of ID
	 * @return next ID available
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
	 */
	public String getNextId(IdType idType) throws InvalidSPDXAnalysisException {
		switch (idType) {
		case Anonymous: return RdfStore.ANON_PREFIX+ model.createResource().getId();
		case LicenseRef: return SpdxConstantsCompatV2.NON_STD_LICENSE_ID_PRENUM+RdfStore.GENERATED+ getNextLicenseId();
		case DocumentRef: return SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM+RdfStore.GENERATED+ getNextDocumentId();
		case SpdxId: return SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM+RdfStore.GENERATED+ getNextSpdxId();
		case ListedLicense: {
			logger.error("Can not generate a license ID for a Listed License");
			throw new InvalidSPDXAnalysisException("Can not generate a license ID for a Listed License");
		}
		default: {
            logger.error("Unknown ID type for next ID: {}", idType);
			throw new InvalidSPDXAnalysisException("Unknown ID type for next ID: "+ idType);
		}
		}
	}

	/**
	 * Remove a property associated with a given ID and all values associated with that property
	 * @param id SPDX ID
	 * @param propertyName property name
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
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
		final QueryExecution qe = QueryExecutionFactory.create(query, model);
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
				Optional<String> spdxType = SpdxResourceFactory.resourceToSpdxType(type.asResource());
				if (spdxType.isEmpty()) {
					throw new RuntimeException(new InvalidSPDXAnalysisException("Missing type for resource"));
				}
				return new TypedValue(resourceToObjectUri(subject.asResource()),
						spdxType.get(), specVersion);
			} catch (Exception e) {
				logger.error("Unexpected exception converting to type");
				throw new RuntimeException(e);
			}
		}).onClose(qe::close);
	}

	/**
	 * Remove a specific value from a collection associated with an ID and property
	 * @param id SPDX ID
	 * @param propertyName property name
	 * @param value value to set
	 * @return true if the value was present
	 * @throws InvalidSPDXAnalysisException  On SPDX parsing errors
	 */
	public boolean removeValueFromCollection(String id, String propertyName, Object value) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(value, "Missing required value");
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
	 * @param id SPDX ID
	 * @param propertyName property name
	 * @return the total number of objects associated with the ID and property
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
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
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
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
	 * Clear (remove) all values associated with the ID and property
	 * @param id SPDX ID
	 * @param propertyName property name
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
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
	 * Add value to the list of objects where the subject is the id and the predicate are the propertyName
	 * @param id SPDX ID
	 * @param propertyName property name
	 * @param value value to add
	 * @return true if the collection was modified
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
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
	 * @param id SPDX ID
	 * @param propertyName property name
	 * @return the list of values associated with id propertyName
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
	 */
	public Iterator<Object> getValueList(String id, String propertyName) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Resource idResource = idToResource(id);
		Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
		return new RdfListIterator(idResource, property);
	}
	
	/**
	 * @param id SPDX ID
	 * @param propertyName property name
	 * @param clazz class to check for assignability
	 * @return true if the OWL Ontology restrictions specifies that the class for the ID and property name allows it to be assigned to clazz
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
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
					if (!clazz.isAssignableFrom(SpdxModelFactoryCompatV2.classUriToClass(classUriRestriction))) {
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
	 * @param dataUri URI for a data type
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
	 * @param id SPDX ID
	 * @param propertyName property name
	 * @param clazz class to check for assignability
	 * @return true if all collection members associated with the property of id is assignable to clazz
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
	 */
	public boolean isCollectionMembersAssignableTo(String id, String propertyName, Class<?> clazz) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(clazz, "Missing required class parameter");
		try {
			return isAssignableTo(id, propertyName, clazz);
		} catch (MissingDataTypeAndClassRestriction ex) {
			logger.warn("Error determining assignability by OWL ontology.  Checking actual properties.",ex);
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
					if (value.get() instanceof TypedValue) {
						try {
							if (!clazz.isAssignableFrom(SpdxModelFactoryCompatV2.typeToClass(((TypedValue)value.get()).getType()))) {
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
						} else if (!(SpdxConstantsCompatV2.URI_VALUE_NOASSERTION.equals(uri) ||
								SpdxConstantsCompatV2.URI_VALUE_NONE.equals(uri))) {
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
	 * @param id SPDX ID
	 * @param propertyName property name
	 * @param clazz class to check for assignability
	 * @return true if there is a property value assignable to clazz
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
	 */
	public boolean isPropertyValueAssignableTo(String id, String propertyName, Class<?> clazz) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		try {
			return isAssignableTo(id, propertyName, clazz);
		} catch (MissingDataTypeAndClassRestriction ex) {
			logger.warn("Error determining assignability by OWL ontology.  Checking actual properties.",ex);
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
                logger.error("Error getting single value.  Multiple values for property {} ID {}.", propertyName, id);
				throw new SpdxRdfException("Error getting single value.  Multiple values for property "+propertyName+" ID "+id+".");
			}
			if (objectValue.isPresent()) {
				if (clazz.isAssignableFrom(objectValue.get().getClass())) {
					return true;
				}
				if (objectValue.get() instanceof TypedValue) {
					try {
						return clazz.isAssignableFrom(SpdxModelFactoryCompatV2.typeToClass(((TypedValue)objectValue.get()).getType()));
					} catch (InvalidSPDXAnalysisException e) {
						logger.error("Error converting typed value to class",e);
						return false;
					}
				}
				if (objectValue.get() instanceof IndividualUriValue) {
	                String uri = ((IndividualUriValue)objectValue.get()).getIndividualURI();
	                if (SpdxConstantsCompatV2.URI_VALUE_NOASSERTION.equals(uri)) {
	                    return true;
	                }
	                if (SpdxConstantsCompatV2.URI_VALUE_NONE.equals(uri)) {
	                    return true;
	                }
	                Enum<?> spdxEnum = SpdxEnumFactory.uriToEnum.get(uri);
	                if (Objects.nonNull(spdxEnum)) {
	                    return clazz.isAssignableFrom(spdxEnum.getClass());
	                } else {
	                    return false;
	                }
	            }
			}
			return false;
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * @param id SPDX ID
	 * @param propertyName property name
	 * @return true if the property of associated with id contains more than one object
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
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
		    // Check for Listed License URI's
		    if (idResource.isURIResource() && idResource.getURI().contains(SPDX_LISTED_LICENSE_SUBPREFIX)) {
		        String licenseOrExceptionId = idResource.getURI().substring(idResource.getURI().lastIndexOf('/')+1);
		        if (ListedLicenses.getListedLicenses().isSpdxListedLicenseId(licenseOrExceptionId)) {
		            return model.createResource(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_SPDX_LISTED_LICENSE);
		        } else if (ListedLicenses.getListedLicenses().isSpdxListedExceptionId(licenseOrExceptionId)) {
		            return model.createResource(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_SPDX_LISTED_LICENSE_EXCEPTION);
		        }
		    }
            logger.error("ID {} does not have a type.", idResource);
			throw new SpdxInvalidIdException("ID "+idResource+" does not have a type.");
		} else {
		    return statement.getObject().asResource();
		}
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
	 * Translate a case-insensitive ID into a case-sensitive ID
	 * @param caseInsensisitiveId ID with case ignored
	 * @return case sensitive ID
	 */
	public Optional<String> getCasesensitiveId(String caseInsensisitiveId) {
		return Optional.ofNullable(this.idCaseSensitiveMap.get(caseInsensisitiveId.toLowerCase()));
	}

	/**
	 * Delete the entire resource and all statements
	 * @param id SPDX ID
	 * @throws SpdxInvalidIdException On SPDX parsing errors
	 */
	public void delete(String id) throws SpdxInvalidIdException {
		Resource idResource = idToResource(id);
		model.removeAll(idResource, null, null);
	}

	/**
	 * @param id associated with a type
	 * @return Type typed value for the ID if it exists and is of an SPDX type, otherwise empty
	 * @throws InvalidSPDXAnalysisException On SPDX parsing errors
	 */
	public Optional<TypedValue> getTypedValue(String id) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		if (!exists(id)) {
			return Optional.empty();
		}
		model.enterCriticalSection(false);
		try {
			try {
				Resource idResource = idToResource(id);
				if (!this.model.containsResource(idResource)) {
					return Optional.empty();
				}
				Resource idClass = idToClass(idResource);
				if (Objects.isNull(idClass)) {
					return Optional.empty();
				}
				Class<?> clazz = SpdxModelFactoryCompatV2.classUriToClass(idClass.getURI());
				if (Objects.isNull(clazz)) {
					return Optional.empty();
				}
				String type = SpdxModelFactoryCompatV2.SPDX_CLASS_TO_TYPE.get(clazz);
				if (Objects.isNull(type)) {
					return Optional.empty();
				}
				return Optional.of(new TypedValue(resourceToObjectUri(idResource), type, specVersion));
			} catch(SpdxInvalidIdException ex) {
				return Optional.empty();
			}
		} finally {
			model.leaveCriticalSection();
		}	
	}

	/**
	 * @return the Jena model
	 */
	public Model getModel() {
		return this.model;
	}
}
