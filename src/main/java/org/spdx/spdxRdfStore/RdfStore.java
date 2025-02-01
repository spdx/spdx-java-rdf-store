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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ARQ;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.core.CoreModelObject;
import org.spdx.core.DuplicateSpdxIdException;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.TypedValue;
import org.spdx.library.SpdxModelFactory;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.storage.IModelStore;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.storage.PropertyDescriptor;
import org.spdx.storage.compatv2.CompatibleModelStoreWrapper;

/**
 * Model Store implemented using RDF
 * 
 * @author Gary O'Neall
 *
 */
@SuppressWarnings("LoggingSimilarMessage")
public class RdfStore implements IModelStore, ISerializableModelStore {
	
	static final Logger logger = LoggerFactory.getLogger(RdfStore.class.getName());
	
	static final String GENERATED = "gnrtd";
	static final Pattern DOCUMENT_ID_PATTERN_GENERATED = Pattern.compile(SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM+GENERATED+"(\\d+)$");
	static final Pattern SPDX_ID_PATTERN_GENERATED = Pattern.compile(SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM+GENERATED+"(\\d+)$");
	static final Pattern LICENSE_ID_PATTERN_GENERATED = Pattern.compile(SpdxConstantsCompatV2.NON_STD_LICENSE_ID_PRENUM+GENERATED+"(\\d+)$");
	static final String ANON_PREFIX = "__anon__";
	static final Pattern ANON_ID_PATTERN = Pattern.compile(ANON_PREFIX+"(.+)$");
	RdfSpdxModelManager modelManager;
	String documentUri;
	boolean dontStoreLicenseDetails = false;
	
	private OutputFormat outputFormat = OutputFormat.XML_ABBREV;

	static {
		ARQ.init();		// Insure ARQ is initialized
	}
	
	/**
	 * Create an RDF store and initialize it with an SPDX document deserialized from stream
	 * Note that the stream must contain one and only one SPDX document in SPDX version 2.X format
	 * @param stream input stream of a model
	 * @throws InvalidSPDXAnalysisException on SPDX parsing errors
	 */
	public RdfStore(InputStream stream) throws InvalidSPDXAnalysisException {
		deSerialize(stream, false);
	}
	
	/**
	 * Creates an uninitialized RDF store - the documentUri must be set or a stream deserialized before any other methods are called
	 */
	public RdfStore() {
		this.modelManager = null;
		this.documentUri = null;
	}
	
	/**
	 * Create an RDF store and initialize it with a data deserialized from stream using the documentUri
	 * for ID prefixes
	 * @param stream stream of an RDF model
	 * @throws InvalidSPDXAnalysisException on SPDX parsing errors
	 */
	public RdfStore(InputStream stream, String documentUri) throws InvalidSPDXAnalysisException {
		this.documentUri = documentUri;
		deSerialize(stream, false, documentUri);
	}
	
	/**
	 * @param documentUri URI for the SPDX document used in this store
	 */
	public RdfStore(String documentUri) {
		this.documentUri = documentUri;
		modelManager = createModelManager(documentUri);
	}

	/**
	 * @return the outputFormat
	 */
	public OutputFormat getOutputFormat() {
		return outputFormat;
	}
	
	/**
	 * @return the document URI
	 */
	public @Nullable String getDocumentUri() {
		return this.documentUri;
	}
	
	/**
	 * @param documentUri document URI to set
	 * @param overwrite setting a different document URI will overwrite an existing model - this flag will allow it to be overwritten
	 * @throws InvalidSPDXAnalysisException if the document URI already exists and override is set to false
	 */
	public void setDocumentUri(@Nullable String documentUri, boolean overwrite) throws InvalidSPDXAnalysisException {
		if (Objects.nonNull(this.documentUri) && !overwrite && !Objects.equals(this.documentUri, documentUri)) {
			throw new InvalidSPDXAnalysisException("Document URI "+this.documentUri+" already exists");
		}
		if (!Objects.equals(this.documentUri, documentUri)) {
			this.documentUri = documentUri;
			if (Objects.nonNull(documentUri)) {
				modelManager = createModelManager(documentUri);
			} else {
				modelManager = null;
			}
		}
	}

	/**
	 * @param outputFormat the outputFormat to set
	 */
	public void setOutputFormat(OutputFormat outputFormat) {
		this.outputFormat = outputFormat;
	}
	
	

	/**
	 * @return the dontStoreLicenseDetails - if true, listed license properties will not be stored in the RDF store
	 */
	public boolean isDontStoreLicenseDetails() {
		return dontStoreLicenseDetails;
	}

	/**
	 * @param dontStoreLicenseDetails the dontStoreLicenseDetails to set - if true, listed license properties will not be stored in the RDF store
	 */
	public void setDontStoreLicenseDetails(boolean dontStoreLicenseDetails) {
		this.dontStoreLicenseDetails = dontStoreLicenseDetails;
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#exists(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean exists(String objectUri) {
		if (Objects.isNull(modelManager)) {
			return false;
		}
		Objects.requireNonNull(objectUri, "Missing required objectUri");
		if (!isAnon(objectUri) && !objectUri.startsWith(documentUri + "#") && 
				!objectUri.startsWith(SpdxConstantsCompatV2.LISTED_LICENSE_NAMESPACE_PREFIX) &&
				!objectUri.startsWith(SpdxConstantsCompatV2.LISTED_LICENSE_URL)) {
			return false;
		}
		String id;
		try {
			id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		} catch (InvalidSPDXAnalysisException e) {
            logger.warn("Unable to convert Object URI into a document URI + ID: {}", objectUri);
			return false;
		}
		return modelManager.exists(id);
	}

	@Override
	public boolean isAnon(String objectUri) {
		Objects.requireNonNull(objectUri, "Missing required objectUri");
		return ANON_ID_PATTERN.matcher(objectUri).matches();
	}
	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getIdType(java.lang.String)
	 */
	@Override
	public IdType getIdType(String objectUri) {
		Objects.requireNonNull(objectUri, "Missing required objectUri");
		if (isAnon(objectUri)) {
			return IdType.Anonymous;
		}
		if (objectUri.startsWith(SpdxConstantsCompatV2.LISTED_LICENSE_NAMESPACE_PREFIX) || 
				objectUri.startsWith(SpdxConstantsCompatV2.LISTED_LICENSE_URL)) {
			return IdType.ListedLicense;
		}
		String id;
		try {
			id = CompatibleModelStoreWrapper.objectUriToId(false, objectUri, documentUri);
		} catch (InvalidSPDXAnalysisException e) {
            logger.warn("Error converting object URI to ID for URI: {}", objectUri, e);
			return IdType.Unknown;
		}
		if (SpdxConstantsCompatV2.LICENSE_ID_PATTERN.matcher(id).matches()) {
			return IdType.LicenseRef;
		}
		if (SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PATTERN.matcher(id).matches()) {
			return IdType.DocumentRef;
		}
		if (SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PATTERN.matcher(id).matches()) {
			return IdType.SpdxId;
		}
		return IdType.Unknown;
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#create(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void create(TypedValue typedValue) throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(typedValue, "Missing required typed value");
		if (SpdxConstantsCompatV2.CLASS_EXTERNAL_SPDX_ELEMENT.equals(typedValue.getType()) ||
				SpdxConstantsCompatV2.CLASS_EXTERNAL_EXTRACTED_LICENSE.equals(typedValue.getType())) {
			return; // we don't create the external elements
		}
		String id = CompatibleModelStoreWrapper.objectUriToId(this, typedValue.getObjectUri(), documentUri);
		if (modelManager.getCasesensitiveId(id).isPresent()) {
			throw new DuplicateSpdxIdException("Id "+id+" already exists.");
		}
		modelManager.getOrCreate(typedValue.getObjectUri(), typedValue.getType());
	}
	
	/**
	 * Initialize an empty model manager
	 * @param documentUri Document URI for the model manager
	 * @return modelManager associated with the documentUri
	 */
	private RdfSpdxModelManager createModelManager(String documentUri) {
		Model model = ModelFactory.createDefaultModel();
		model.getGraph().getPrefixMapping().setNsPrefix("spdx", SpdxConstantsCompatV2.SPDX_NAMESPACE);
		model.getGraph().getPrefixMapping().setNsPrefix("doap", SpdxConstantsCompatV2.DOAP_NAMESPACE);
		model.getGraph().getPrefixMapping().setNsPrefix("ptr", SpdxConstantsCompatV2.RDF_POINTER_NAMESPACE);
		model.getGraph().getPrefixMapping().setNsPrefix("rdfs", SpdxConstantsCompatV2.RDFS_NAMESPACE);
        return new RdfSpdxModelManager(documentUri, model);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getPropertyValueNames(java.lang.String, java.lang.String)
	 */
	@Override
	public List<PropertyDescriptor> getPropertyValueDescriptors(String objectUri) throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required object URI");
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		return modelManager.getPropertyValueNames(id).stream()
				.map(CompatibleModelStoreWrapper::propNameToPropDescriptor)
				.collect(Collectors.toList());
	}



	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#setValue(java.lang.String, java.lang.String, java.lang.String, java.lang.Object)
	 */
	@Override
	public void setValue(String objectUri, PropertyDescriptor prop, Object value)
			throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri);
		Objects.requireNonNull(prop);
		if (isListedLicenseOrException(objectUri) && dontStoreLicenseDetails) {
			return;
		}
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		modelManager.setValue(id, prop.getName(), value);
	}

	/**
	 * @param objectUri URI or temp ID of an SPDX object
	 * @return true if the object URI is associated with a listed license or a listed exception
	 */
	private boolean isListedLicenseOrException(String objectUri) {
		return Objects.isNull(objectUri) || objectUri.startsWith(SpdxConstantsCompatV2.LISTED_LICENSE_NAMESPACE_PREFIX) ||
				objectUri.startsWith(SpdxConstantsCompatV2.LISTED_LICENSE_URL);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getValue(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Optional<Object> getValue(String objectUri, PropertyDescriptor prop)
			throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required object URI");
		Objects.requireNonNull(prop, "Missing required property descriptor");
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		return modelManager.getPropertyValue(id, prop.getName());
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getNextId(org.spdx.storage.IModelStore.IdType, java.lang.String)
	 */
	@Override
	public String getNextId(IdType idType) throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(idType, "Missing required ID type");
		return modelManager.getNextId(idType);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#removeProperty(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void removeProperty(String objectUri, PropertyDescriptor prop) throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required Object URI");
		Objects.requireNonNull(prop, "Missing required property descriptor");
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		modelManager.removeProperty(id, prop.getName());
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getAllItems(java.lang.String, java.util.Optional)
	 */
	@Override
	public Stream<TypedValue> getAllItems(@Nullable String prefix, String typeFilter)
			throws InvalidSPDXAnalysisException {
		checkClosed();
		return modelManager.getAllItems(typeFilter);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#removeValueFromCollection(java.lang.String, java.lang.String, java.lang.String, java.lang.Object)
	 */
	public boolean removeValueFromCollection(String objectUri, PropertyDescriptor propertyDescriptor, Object value)
			throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required object URI");
		Objects.requireNonNull(propertyDescriptor, "Missing required property descriptor");
		Objects.requireNonNull(value, "Mising required value");
		if (isListedLicenseOrException(objectUri) && dontStoreLicenseDetails) {
			return false;
		}
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		return modelManager.removeValueFromCollection(id, propertyDescriptor.getName(), value);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#collectionSize(java.lang.String, java.lang.String, java.lang.String)
	 */
	public int collectionSize(String objectUri, PropertyDescriptor propertyDescriptor) throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required Object URI");
		Objects.requireNonNull(propertyDescriptor, "Missing required property descriptor");
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		return modelManager.collectionSize(id, propertyDescriptor.getName());
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#collectionContains(java.lang.String, java.lang.String, java.lang.String, java.lang.Object)
	 */
	public boolean collectionContains(String objectUri, PropertyDescriptor propertyDescriptor, Object value)
			throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required Object URI");
		Objects.requireNonNull(propertyDescriptor, "Missing required property descriptor");
		Objects.requireNonNull(value, "Missing required value");
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		return modelManager.collectionContains(id, propertyDescriptor.getName(), value);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#clearValueCollection(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void clearValueCollection(String objectUri, PropertyDescriptor propertyDescriptor)
			throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required Object URI");
		Objects.requireNonNull(propertyDescriptor, "Missing required property descriptor");
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		modelManager.clearValueCollection(id, propertyDescriptor.getName());
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#addValueToCollection(java.lang.String, java.lang.String, java.lang.String, java.lang.Object)
	 */
	public boolean addValueToCollection(String objectUri, PropertyDescriptor propertyDescriptor, Object value)
			throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(objectUri, "Missing required Object URI");
		Objects.requireNonNull(propertyDescriptor, "Missing required property descriptor");
		checkClosed();
		if (isListedLicenseOrException(objectUri) && dontStoreLicenseDetails) {
			return false;
		}
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		return modelManager.addValueToCollection(id, propertyDescriptor.getName(), value);
	}
	
	private void checkClosed() throws InvalidSPDXAnalysisException {
		if (Objects.isNull(modelManager)) {
			throw new InvalidSPDXAnalysisException("RDF Store has been closed or not properly initialized");
		}
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getValueList(java.lang.String, java.lang.String, java.lang.String)
	 */
	public Iterator<Object> listValues(String objectUri, PropertyDescriptor propertyDescriptor)
			throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required Object URI");
		Objects.requireNonNull(propertyDescriptor, "Missing required property descriptor");
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		return modelManager.getValueList(id, propertyDescriptor.getName());
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#isCollectionMembersAssignableTo(java.lang.String, java.lang.String, java.lang.String, java.lang.Class)
	 */
	public boolean isCollectionMembersAssignableTo(String objectUri, PropertyDescriptor propertyDescriptor, Class<?> clazz)
			throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required Object URI");
		Objects.requireNonNull(propertyDescriptor, "Missing required property descriptor");
		Objects.requireNonNull(clazz, "Missing required class");
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		return modelManager.isCollectionMembersAssignableTo(id, propertyDescriptor.getName(), clazz);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#isPropertyValueAssignableTo(java.lang.String, java.lang.String, java.lang.String, java.lang.Class)
	 */
	@Override
	public boolean isPropertyValueAssignableTo(String objectUri, PropertyDescriptor propertyDescriptor, Class<?> clazz, String specVersion)
			throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required Object URI");
		Objects.requireNonNull(propertyDescriptor, "Missing required property descriptor");
		Objects.requireNonNull(clazz, "Missing required class");
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		return modelManager.isPropertyValueAssignableTo(id, propertyDescriptor.getName(), clazz);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#isCollectionProperty(java.lang.String, java.lang.String, java.lang.String)
	 */
	public boolean isCollectionProperty(String objectUri, PropertyDescriptor propertyDescriptor)
			throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required Object URI");
		Objects.requireNonNull(propertyDescriptor, "Missing required property descriptor");
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		return modelManager.isCollectionProperty(id, propertyDescriptor.getName());
	}

	@Override
	public void leaveCriticalSection(IModelStoreLock lock) {
		lock.unlock();
	}

	@Override
	public IModelStoreLock enterCriticalSection(boolean readLockRequested) throws InvalidSPDXAnalysisException {
		checkClosed();
		return modelManager.enterCriticalSection(readLockRequested);
	}
	
	/**
	 * Load a document from a file or URL
	 * @param fileNameOrUrl file name or URL of a serialized RDF model
	 * @param overwrite if true, overwrite any existing documents with the same document URI
	 * @return the DocumentURI of the SPDX document
	 * @throws InvalidSPDXAnalysisException on SPDX parsing errors
	 * @throws IOException on IO error
	 */
	public String loadModelFromFile(String fileNameOrUrl, boolean overwrite) throws InvalidSPDXAnalysisException, IOException {
		InputStream spdxRdfInput = RDFDataMgr.open(fileNameOrUrl);
		if (Objects.isNull(spdxRdfInput)) {
		    throw new FileNotFoundException(fileNameOrUrl + " not found.");
		}
		try {
			SpdxDocument doc = deSerialize(spdxRdfInput, overwrite);
			return doc.getDocumentUri();
		} finally {
			try {
				spdxRdfInput.close();
			} catch (IOException e) {
                logger.warn("Error closing SPDX RDF file {}", fileNameOrUrl, e);
			}
		}
	}
	
	/**
	 * Form the document namespace URI from the SPDX document URI
	 * @param docUriString String form of the SPDX document URI
	 * @return document namespace
	 */
	private static String formDocNamespace(String docUriString) {
		// just remove any fragments for the DOC URI
		int fragmentIndex = docUriString.indexOf('#');
		if (fragmentIndex <= 0) {
			return docUriString;
		} else {
			return docUriString.substring(0, fragmentIndex);
		}
	}
	
	/**
	 * @return the spdx doc nodes from the model
	 */
	public static List<Node> getSpdxDocNodes(Model model) {
		Node rdfTypePredicate = model.getProperty(SpdxConstantsCompatV2.RDF_NAMESPACE, 
				SpdxConstantsCompatV2.RDF_PROP_TYPE.getName()).asNode();
		Node spdxDocObject = model.getProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE, 
				SpdxConstantsCompatV2.CLASS_SPDX_DOCUMENT).asNode();
		Triple m = Triple.createMatch(null, rdfTypePredicate, spdxDocObject);
		ExtendedIterator<Triple> tripleIter = model.getGraph().find(m);	// find the document
		List<Node> retval = new ArrayList<>();
		while (tripleIter.hasNext()) {
			Triple docTriple = tripleIter.next();
			retval.add(docTriple.getSubject());
		}
		return retval;
	}
	
	/**
	 * @param model model containing a single document
	 * @return the document namespace for the document stored in the model
	 * @throws InvalidSPDXAnalysisException on SPDX parsing errors
	 */
	public static List<String> getDocumentNamespaces(Model model) throws InvalidSPDXAnalysisException {
		List<String> retval = new ArrayList<>();
		for (Node documentNode:getSpdxDocNodes(model)) {
			if (documentNode == null) {
				throw(new InvalidSPDXAnalysisException("Invalid model - must contain an SPDX Document"));
			}
			if (!documentNode.isURI()) {
				throw(new InvalidSPDXAnalysisException("SPDX Documents must have a unique URI"));
			}
			String docUri = documentNode.getURI();
			retval.add(formDocNamespace(docUri));
		}
		return retval;
	}

	@Override
	public void serialize(OutputStream stream) throws InvalidSPDXAnalysisException {
		checkClosed();
		modelManager.serialize(stream, outputFormat);
	}
	
	@Override
	public void serialize(OutputStream stream, @Nullable CoreModelObject spdxDocument) throws InvalidSPDXAnalysisException {
		checkClosed();
		if (Objects.nonNull(spdxDocument)) {
			if (!(spdxDocument instanceof SpdxDocument)) {
                logger.error("Attempting to serialize {} which is not an SpdxDocument", spdxDocument.getClass().getName());
				throw new InvalidSPDXAnalysisException("Attempting to serialize "+spdxDocument.getClass().getName()+" which is not an SpdxDocument");
			}
			if (!this.documentUri.equals(((SpdxDocument)spdxDocument).getDocumentUri())) {
                logger.error("{} not found in model store", ((SpdxDocument) spdxDocument).getDocumentUri());
				throw new InvalidSPDXAnalysisException(((SpdxDocument)spdxDocument).getDocumentUri() + " not found in model store");
			}
		}
		modelManager.serialize(stream, outputFormat);
	}

	@Override
	public SpdxDocument deSerialize(InputStream stream, boolean overwrite) throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		model.read(stream, null, this.outputFormat.getType());
		List<String> documentNamespaces = getDocumentNamespaces(model);
		if (documentNamespaces.size() > 1) {
			throw new InvalidSPDXAnalysisException("Can only deserialize SPDX version 2 RDF documents with a single SPDX document");
		}
		if (documentNamespaces.isEmpty()) {
			throw new InvalidSPDXAnalysisException("Missing SPDX document");
		}
		String documentNamespace = documentNamespaces.get(0);
		CompatibilityUpgrader.upgrade(model, documentNamespace);
		if (Objects.nonNull(modelManager) && !getDocumentNamespaces(modelManager.getModel()).isEmpty()) {
			if (overwrite) {
                logger.warn("Overwriting previous model from file for document URI {}", documentNamespace);
			} else {
				throw new SpdxRdfException("RDF Store contains data and overwrite is set to false");
			}
		}
		this.modelManager = new RdfSpdxModelManager(documentNamespace, model);
		this.documentUri = documentNamespace;
		
		@SuppressWarnings("unchecked")
		Stream<SpdxDocument> documentStream = (Stream<SpdxDocument>)SpdxModelFactory.getSpdxObjects(this, null, SpdxConstantsCompatV2.CLASS_SPDX_DOCUMENT, 
				documentNamespace + "#" + SpdxConstantsCompatV2.SPDX_DOCUMENT_ID, documentNamespace);
		List<SpdxDocument> documents = documentStream.collect(Collectors.toList());
		if (documents.isEmpty()) {
			logger.error("No SPDX document found in file");
			throw new InvalidSPDXAnalysisException("No SPDX document was found in file");
		}
		return documents.get(0);
	}
	
	
    /**
     * Deserialize an RDF stream without an enclosing document
     * @param stream stream containing the SPDX data
     * @param documentNamespace document namespace to use
     * @throws InvalidSPDXAnalysisException on SPDX parsing errors
     */
	public void deSerialize(InputStream stream, boolean overwrite, String documentNamespace) throws InvalidSPDXAnalysisException {
        Model model = ModelFactory.createDefaultModel();
        model.read(stream, null, this.outputFormat.getType());
        CompatibilityUpgrader.upgrade(model, documentNamespace);
        if (!getDocumentNamespaces(modelManager.getModel()).isEmpty())  {
            if (overwrite) {
                logger.warn("Overwriting previous model from file for document URI {}", documentNamespace);
            } else {
                throw new SpdxRdfException("Document "+documentNamespace+" is already open in the RDF Store");
            }
        }
        this.modelManager = new RdfSpdxModelManager(documentNamespace, model);
        this.documentUri = documentNamespace;
    }

	@Override
	public Optional<String> getCaseSensitiveId(String documentUri, String caseInsensisitiveId) {
		if (Objects.isNull(modelManager)) {
			return Optional.empty();
		}
		return modelManager.getCasesensitiveId(caseInsensisitiveId);
	}

	@Override
	public Optional<TypedValue> getTypedValue(String objectUri) throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required Object URI");
		if (!isAnon(objectUri) && !objectUri.startsWith(documentUri + "#") && 
				!objectUri.startsWith(SpdxConstantsCompatV2.LISTED_LICENSE_NAMESPACE_PREFIX) &&
				!objectUri.startsWith(SpdxConstantsCompatV2.LISTED_LICENSE_URL)) {
			return Optional.empty();
		} else {
			String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
			return modelManager.getTypedValue(id);
		}
	}

	@Override
	public void delete(String objectUri) throws InvalidSPDXAnalysisException {
		checkClosed();
		Objects.requireNonNull(objectUri, "Missing required Object URI");
		String id = CompatibleModelStoreWrapper.objectUriToId(this, objectUri, documentUri);
		modelManager.delete(id);
	}

	@Override
	public void close() {
		if (Objects.nonNull(modelManager)) {
			modelManager.close();
			modelManager = null;
		}
	}
}
