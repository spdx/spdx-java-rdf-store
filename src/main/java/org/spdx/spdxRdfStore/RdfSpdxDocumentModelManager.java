/**
 * 
 */
package org.spdx.spdxRdfStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
import org.spdx.library.model.IndividuallValue;
import org.spdx.library.model.SpdxInvalidTypeException;
import org.spdx.library.model.TypedValue;
import org.spdx.storage.IModelStore;
import org.spdx.storage.IModelStore.IdType;
import org.spdx.storage.IModelStore.ReadWrite;

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
public class RdfSpdxDocumentModelManager {
	
	static final Logger logger = LoggerFactory.getLogger(RdfSpdxDocumentModelManager.class.getName());
	
	static final String RDF_TYPE = SpdxConstants.RDF_NAMESPACE + SpdxConstants.RDF_PROP_TYPE;
	
	static final Set<String> LISTED_LICENSE_CLASSES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(SpdxConstants.LISTED_LICENSE_URI_CLASSES)));
	
	/**
	 * Listen for any new resources being created to make sure we update the next ID numbers
	 *
	 */
	class NextIdListener extends ObjectListener {
		@Override
		public void added(Object x) {
			if (x instanceof RDFNode) {
				updateCounters((RDFNode)x);
			} else if (x instanceof Statement) {
				Statement st = (Statement)x;
				if (Objects.nonNull(st.getSubject())) {
					updateCounters(st.getSubject());
				}
			}
		}
	}
	
	private ReadWriteLock counterLock = new ReentrantReadWriteLock();
	private NextIdListener nextIdListener = new NextIdListener();
	
	private String documentUri;
	private Model model;

	private int nextNextSpdxId = 1;

	private int nextNextDocumentId = 1;

	private int nextNextLicenseId = 1;

	private Property typeProperty;

	/**
	 * @param documentUri Unique URI for this document
	 * @param model Model used to store this document
	 */
	public RdfSpdxDocumentModelManager(String documentUri, Model model) {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(model, "Missing required model");
		this.documentUri = documentUri;
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
	private synchronized void checkUpdateNextLicenseId(Matcher licenseRefMatcher) {
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
	 * Update the counter based on the ID represented by the node
	 * @param node
	 */
	private void updateCounters(RDFNode node) {
		Objects.requireNonNull(node);
		if (node.isResource()) {
			if (node.isAnon()) {
				return;
			}
			String id = node.asResource().getLocalName();
			if (Objects.isNull(id)) {
				return;
			}
			Matcher licenseRefMatcher = SpdxConstants.LICENSE_ID_PATTERN_NUMERIC.matcher(id);
			if (licenseRefMatcher.matches()) {
				checkUpdateNextLicenseId(licenseRefMatcher);
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
				updateCounters(iter.next());
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
		if (isAnonId(id)) {
			resource = model.createResource(idToAnonId(id));
		} else {
			// first try local to the document
			resource = model.createResource(idToUriInDocument(id));
			if (!model.containsResource(resource)) {
				// Try listed license URL
				resource = model.createResource(idToListedLicenseUri(id));
			}
		}
		Statement statement = model.getProperty(resource, typeProperty);
		if (statement == null || !statement.getObject().isResource()) {
			logger.error("ID "+id+" does not have a type.");
			throw new SpdxInvalidIdException("ID "+id+" does not have a type.");
		}
		Optional<String> existingType = SpdxResourceFactory.resourceToSpdxType(statement.getObject().asResource());
		if (!existingType.isPresent()) {
			logger.error("ID "+id+" does not have a type.");
			throw new SpdxInvalidIdException("ID "+id+" does not have a type.");
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
		return SpdxConstants.LISTED_LICENSE_DOCUMENT_URI + licenseId;
	}


	/**
	 * Create a new resource with and ID and type
	 * @param id ID used in the SPDX model
	 * @param type SPDX Type
	 * @return the created resource
	 * @throws SpdxInvalidIdException
	 */
	public Resource create(String id, String type) throws SpdxInvalidIdException {
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
	private String resourceToPropertyName(RDFNode node) throws SpdxRdfException {
		Objects.requireNonNull(node, "Missing required node");
		if (node.isAnon()) {
			logger.error("Attempting to convert an anonomous node to a property name");
			throw new SpdxRdfException("Can not convert an anonomous node into a property name.");
		}
		if (node.isLiteral()) {
			logger.error("Attempting to convert an literal node to a property name: "+node.toString());
			throw new SpdxRdfException("Can not convert a literal node into a property name.");
		}
		return node.asResource().getLocalName();
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
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			Resource idResource = idToResource(id);
			idResource.removeAll(property);
			idResource.addProperty(property, valueToNode(value));
		} finally {
			model.leaveCriticalSection();
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
		if (value instanceof Boolean || value instanceof String) {
			return model.createTypedLiteral(value);
		} else if (value instanceof TypedValue) {
			TypedValue tv = (TypedValue)value;
			return create(tv.getId(), tv.getType());
		} else if (value instanceof IndividuallValue) {
			return model.createResource(((IndividuallValue)value).getIndividualURI());
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
			Optional<Object> result = valueNodeToObject(iter.next());
			if (iter.hasNext()) {
				logger.error("Error getting single value.  Multiple values for property "+propertyName+" ID "+id+".");
				throw new SpdxRdfException("Error getting single value.  Multiple values for property "+propertyName+" ID "+id+".");
			}
			return result;
		} finally {
			model.leaveCriticalSection();
		}
	}
	
	private Optional<Object> valueNodeToObject(RDFNode propertyValue) throws InvalidSPDXAnalysisException {
		if (Objects.isNull(propertyValue)) {
			return Optional.empty();
		}
		if (propertyValue.isLiteral()) {
			return Optional.of(propertyValue.asLiteral().getValue());
		}
		Resource valueType = propertyValue.asResource().getPropertyResourceValue(RDF.type);
		Optional<String> sValueType;
		if (Objects.nonNull(valueType)) {
			sValueType = SpdxResourceFactory.resourceToSpdxType(valueType);
		} else {
			sValueType = Optional.empty();
		}
		if (sValueType.isPresent()) {
			return Optional.of(new TypedValue(resourceToId(propertyValue.asResource()), sValueType.get()));
		} else {
			if (propertyValue.isURIResource()) {
				// Assume this is an individual value
				final String propertyUri = propertyValue.asResource().getURI();
				IndividuallValue iv = new IndividuallValue() {

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
			return RdfStore.ANON_PREFIX + resource.getLocalName();
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
		case Anonomous: return RdfStore.ANON_PREFIX+String.valueOf(model.createResource().getId());
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
					if (type.isResource() && subject.isResource()) {
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

	public IModelStore.ModelTransaction beginTransaction(IModelStore.ReadWrite readWrite) {
		return new IModelStore.ModelTransaction() {

			@Override
			public void begin(ReadWrite readWrite) throws IOException {
				model.begin();
			}

			@Override
			public void commit() throws IOException {
				model.commit();
			}

			@Override
			public void close() throws IOException {
				// Nothing to do here
			}
			
		};
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
	public List<Object> getValueList(String id, String propertyName) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		model.enterCriticalSection(false);
		try {
			Resource idResource = idToResource(id);
			List<Object> retval = new ArrayList<>();
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			model.listObjectsOfProperty(idResource, property).toList().forEach((RDFNode node) -> {
				try {
					Optional<Object> value = valueNodeToObject(node);
					if (value.isPresent()) {
						retval.add(value.get());
					}
				} catch (InvalidSPDXAnalysisException e) {
					logger.warn("Exception adding value to node.  Skipping "+node.toString(), e);
				}
			}); 
			return retval;
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * @param id
	 * @param propertyName
	 * @param clazz
	 * @return true if all collection members associated with the property of id is assignable to clazz
	 * @throws InvalidSPDXAnalysisException
	 */
	public boolean isCollectionMembersAssignableTo(String id, String propertyName, Class<?> clazz) throws InvalidSPDXAnalysisException {
		// TODO Change implementation to read an RDF OWL document to determine type
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		model.enterCriticalSection(false);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			NodeIterator iter = model.listObjectsOfProperty(idResource, property);
			while (iter.hasNext()) {
				RDFNode node = iter.next();
				Optional<Object> value = valueNodeToObject(node);
				if (value.isPresent() && !clazz.isAssignableFrom(value.get().getClass())) {
					return false;
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
		model.enterCriticalSection(false);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			NodeIterator iter = model.listObjectsOfProperty(idResource, property);
			if (!iter.hasNext()) {
				return false;	// I guess you can assign anything and be compatible?
			}
			Optional<Object> objectValue = valueNodeToObject(iter.next());
			if (iter.hasNext()) {
				logger.error("Error getting single value.  Multiple values for property "+propertyName+" ID "+id+".");
				throw new SpdxRdfException("Error getting single value.  Multiple values for property "+propertyName+" ID "+id+".");
			}
			if (objectValue.isPresent()) {
				return clazz.isAssignableFrom(objectValue.get().getClass());
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
	 * @return true if the property of associated with id contains more than one object
	 * @throws InvalidSPDXAnalysisException
	 */
	public boolean isCollectionProperty(String id, String propertyName) throws InvalidSPDXAnalysisException {
		// TODO Change implementation to read an RDF OWL document to determine type
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		model.enterCriticalSection(false);
		try {
			Resource idResource = idToResource(id);
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			NodeIterator iter = model.listObjectsOfProperty(idResource, property);
			if (!iter.hasNext()) {
				return false;
			}
			iter.next();
			return iter.hasNext();	//TODO: Bit of a kludge - only returning true if there is more than one element
		} finally {
			model.leaveCriticalSection();
		}
	}
	

	public void close() {
		this.model.unregister(nextIdListener);
	}
	
	@Override
	public void finalize() {
		close();
	}
}
