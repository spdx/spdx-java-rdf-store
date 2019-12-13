/**
 * 
 */
package org.spdx.spdxRdfStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.SpdxConstants;
import org.spdx.library.SpdxInvalidIdException;
import org.spdx.library.model.IndividuallValue;
import org.spdx.library.model.SpdxIdNotFoundException;
import org.spdx.library.model.SpdxInvalidTypeException;
import org.spdx.library.model.TypedValue;
import org.spdx.storage.IModelStore;
import org.spdx.storage.IModelStore.IdType;
import org.spdx.storage.IModelStore.ReadWrite;

/**
 * Manages the reads/write/updates for a specific Jena model associated with a document
 * 
 * @author Gary O'Neall
 *
 */
public class RdfSpdxDocumentModelManager {
	
	static final Logger logger = LoggerFactory.getLogger(RdfSpdxDocumentModelManager.class.getName());
	
	static final String RDF_TYPE = SpdxConstants.RDF_NAMESPACE + SpdxConstants.RDF_PROP_TYPE;
	
	/**
	 * Listen for any new resources being created to make sure we update the next ID numbers
	 *
	 */
	class NextIdListener extends ObjectListener {
		@Override
		public void added(Object x) {
			if (x instanceof RDFNode) {
				updateCounters((RDFNode)x);
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

	/**
	 * @param documentUri Unique URI for this document
	 * @param model Model used to store this document
	 */
	public RdfSpdxDocumentModelManager(String documentUri, Model model) {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(model, "Missing required model");
		this.documentUri = documentUri;
		this.model = model;
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
			String id = node.asResource().getLocalName();
			Matcher anonMatcher = RdfStore.ANON_ID_PATTERN.matcher(id);
			if (anonMatcher.matches()) {
				return; // we get the anon ID's from the model directly, don't need to update
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
			NodeIterator iter = model.listObjects();
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
			resource = idToResource(id);
			return model.containsResource(resource);
		} catch (SpdxInvalidIdException e) {
			logger.warn("Invalid SPDX ID passed to exist.  Returning false");
			return false;
		} finally {
			model.leaveCriticalSection();
		}
	}
	
	private RDFNode idToResource(String id) throws SpdxInvalidIdException {
		Objects.requireNonNull(model, "Missing required model");
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		IdType type = RdfStore.stGetIdType(id);
		switch (type) {
			case Anonomous: return model.createResource(idToAnonId(id));
			case LicenseRef:
			case DocumentRef:
			case SpdxId: return ResourceFactory.createResource(idToUriInDocument(id));
			case ListedLicense: return ResourceFactory.createResource(
					SpdxConstants.LISTED_LICENSE_DOCUMENT_URI + id);
			case Literal: return ResourceFactory.createPlainLiteral(idToLiteralString(id));
			case Unkown:
				default: {
					logger.error("Unknown type for ID "+id);
					throw new SpdxInvalidIdException("Unknown type for ID");
				}
		}
	}
	
	/**
	 * Convert an ID to a literal string
	 * @param id
	 * @return literal form of the ID
	 */
	private String idToLiteralString(String id) {
		return SpdxConstants.SPDX_NAMESPACE + id.toLowerCase();
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
	 */
	private AnonId idToAnonId(String id) {
		String anon = RdfStore.ANON_ID_PATTERN.matcher(id).group(1);
		return new AnonId(anon);
	}
	
	/**
	 * Convert a listed license to the full listed license URI
	 * @param licenseId
	 * @return listed license URI for the license ID
	 */
	private String idToListedLicenseUri(String licenseId) {
		return SpdxConstants.LISTED_LICENSE_DOCUMENT_URI + licenseId;
	}

	public void close() {
		this.model.unregister(nextIdListener);
	}
	
	@Override
	public void finalize() {
		close();
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
		IdType idType = RdfStore.stGetIdType(id);
		model.enterCriticalSection(false);
		try {
			switch (idType) {
			case Anonomous: {
				Resource r = model.createResource(new AnonId(id));	// Maybe we just create the resource with the ID?
				model.add(r, RDF.type, rdfType);
				return r;
			}
			case LicenseRef:
			case DocumentRef:
			case SpdxId: return model.createResource(idToUriInDocument(id), rdfType);
			case ListedLicense: return model.createResource(idToListedLicenseUri(id), rdfType);
			case Literal:
			case Unkown:
				default: {
					logger.error("Attempting to create a resource for an Uknown ID type: "+id);
					throw new SpdxInvalidIdException("Invalid ID type for create: "+id);
				}
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
		List<String> retval = new ArrayList<String>();
		model.enterCriticalSection(true);
		try {
			RDFNode idNode = idToResource(id);
			if (!idNode.isResource()) {
				return retval;
			}
			Resource idResource = idNode.asResource();
			
			idResource.listProperties().forEachRemaining(action -> {
				try {
					retval.add(resourceToPropertyName(action.getObject()));
				} catch (SpdxRdfException e) {
					logger.warn("Skipping invalid property "+action.getObject().toString(),e);
				}
			});
			return retval;
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
			RDFNode idNode = idToResource(id);
			if (!idNode.isResource()) {
				logger.error("Can not store values for ID" + id + ".  This ID is not a resource.");
				throw new SpdxRdfException("Can not store values for ID" + id + ".  This ID is not a resource.");
			}
			Resource idResource = idNode.asResource();
			if (!model.containsResource(idResource)) {
				logger.error("ID "+id+" was not found in the memory store.  The ID must first be created before getting or setting property values.");
				throw new SpdxIdNotFoundException("ID "+id+" was not found in the memory store.  The ID must first be created before getting or setting property values.");
			}
			idResource.addProperty(property, valueToNode(value));
		} finally {
			model.leaveCriticalSection();
		}
	}
	
	/**
	 * Converts a value to an RDFNode basd on the object type
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
			RDFNode valueNode = idToResource(tv.getId());
			if (!valueNode.isResource()) {
				logger.error("Typed value ID "+tv.getId() + " is not a resource type");
				throw new SpdxRdfException("Typed value ID is not a resource type");
			}
			Resource valueResource = valueNode.asResource();
			if (!model.containsResource(valueResource)) {
				valueResource = create(tv.getId(), tv.getType());
			}
			return valueResource;
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
			RDFNode idResource = idToResource(id);
			if (!idResource.isResource()) {
				logger.error("Can not get value for ID.  ID "+id+" is not a resource.");
				throw new SpdxRdfException("Can not get value for ID.  ID "+id+" is not a resource.");
			}
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			Resource propertyValue = idResource.asResource().getPropertyResourceValue(property);
			if (Objects.isNull(propertyValue)) {
				return Optional.empty();
			}
			if (propertyValue.isLiteral()) {
				return Optional.of(propertyValue.asLiteral().getValue());
			}
			Resource valueType = propertyValue.getPropertyResourceValue(RDF.type);
			Optional<String> sValueType;
			if (!Objects.isNull(valueType)) {
				sValueType = SpdxResourceFactory.resourceToSpdxType(valueType);
			} else {
				sValueType = Optional.empty();
			}
			if (sValueType.isPresent()) {
				return Optional.of(new TypedValue(resourceToId(propertyValue), sValueType.get()));
			} else {
				if (propertyValue.isURIResource()) {
					// Assume this is an individual value
					final String propertyUri = propertyValue.getURI();
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
		} finally {
			model.leaveCriticalSection();
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
		case ListedLicense: throw new InvalidSPDXAnalysisException("Can not generate a license ID for a Listed License");
		case Literal: throw new InvalidSPDXAnalysisException("Can not generate a license ID for a Literal");
		default: throw new InvalidSPDXAnalysisException("Unknown ID type for next ID: "+idType.toString());
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
			RDFNode idResource = idToResource(id);
			if (!idResource.isResource()) {
				throw new SpdxRdfException("Can not remove property from ID "+id+".  Not a resource.");
			}
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			model.removeAll(idResource.asResource(), property, null);
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
			RDFNode idResource = idToResource(id);
			if (!idResource.isResource()) {
				logger.error("Can not remove value from collection associated with ID "+id+".  Not a resource.");
				throw new SpdxRdfException("Can not remove value from collection associated with ID "+id+".  Not a resource.");
			}
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			RDFNode rdfValue = valueToNode(value);
			if (model.contains(idResource.asResource(), property, rdfValue)) {
				model.removeAll(idResource.asResource(), property, rdfValue);
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
			RDFNode idResource = idToResource(id);
			if (!idResource.isResource()) {
				throw new SpdxRdfException("Can not obtain collection from ID "+id+".  Not a resource.");
			}
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			return model.listObjectsOfProperty(idResource.asResource(), property).toList().size();
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
			RDFNode idResource = idToResource(id);
			if (!idResource.isResource()) {
				logger.error("Can not check value from collection associated with ID "+id+".  Not a resource.");
				throw new SpdxRdfException("Can not check value from collection associated with ID "+id+".  Not a resource.");
			}
			Property property = model.createProperty(SpdxResourceFactory.propertyNameToUri(propertyName));
			RDFNode rdfValue = valueToNode(value);
			return model.contains(idResource.asResource(), property, rdfValue);
		} finally {
			model.leaveCriticalSection();
		}
	}

	public void clearValueCollection(String id, String propertyName) {
		// TODO Auto-generated method stub
		
	}

	public boolean addValueToCollection(String id, String propertyName, Object value) {
		// TODO Auto-generated method stub
		return false;
	}

	public List<Object> getValueList(String id, String propertyName) {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isCollectionMembersAssignableTo(String id, String propertyName, Class<?> clazz) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isPropertyValueAssignableTo(String id, String propertyName, Class<?> clazz) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isCollectionProperty(String id, String propertyName) {
		// TODO Auto-generated method stub
		return false;
	}
	
	
}
