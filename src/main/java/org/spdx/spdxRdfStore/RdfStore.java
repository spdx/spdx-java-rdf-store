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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.util.FileManager;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.SpdxConstants;
import org.spdx.library.model.SpdxIdNotFoundException;
import org.spdx.library.model.TypedValue;
import org.spdx.library.model.license.LicenseInfoFactory;
import org.spdx.storage.IModelStore;
import org.spdx.storage.ISerializableModelStore;

/**
 * Model Store implemented using RDF
 * 
 * @author Gary O'Neall
 *
 */
public class RdfStore implements IModelStore, ISerializableModelStore {
	
	static final Logger logger = LoggerFactory.getLogger(RdfStore.class.getName());
	
	static Pattern DOCUMENT_ID_PATTERN_NUMERIC = Pattern.compile(SpdxConstants.EXTERNAL_DOC_REF_PRENUM+"(\\d+)$");
	static Pattern SPDX_ID_PATTERN_NUMERIC = Pattern.compile(SpdxConstants.SPDX_ELEMENT_REF_PRENUM+"(\\d+)$");
	static final String ANON_PREFIX = "__anon__";
	static Pattern ANON_ID_PATTERN = Pattern.compile(ANON_PREFIX+"(.+)$");
	private static final Set<String> LITERAL_VALUE_SET = new HashSet<String>(Arrays.asList(SpdxConstants.LITERAL_VALUES));

	Map<String, RdfSpdxDocumentModelManager> documentUriModelMap = new ConcurrentHashMap<>();

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#exists(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean exists(String documentUri, String id) {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		RdfSpdxDocumentModelManager documentModel = documentUriModelMap.get(documentUri);
		if (Objects.isNull(documentModel)) {
			return false;
		}
		return documentModel.exists(id);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getIdType(java.lang.String)
	 */
	@Override
	public IdType getIdType(String id) {
		Objects.requireNonNull(id, "Missing required ID");
		if (ANON_ID_PATTERN.matcher(id).matches()) {
			return IdType.Anonymous;
		}
		if (SpdxConstants.LICENSE_ID_PATTERN_NUMERIC.matcher(id).matches()) {
			return IdType.LicenseRef;
		}
		if (DOCUMENT_ID_PATTERN_NUMERIC.matcher(id).matches()) {
			return IdType.DocumentRef;
		}
		if (SPDX_ID_PATTERN_NUMERIC.matcher(id).matches()) {
			return IdType.SpdxId;
		}
		if (LITERAL_VALUE_SET.contains(id)) {
			return IdType.Literal;
		}
		if (LicenseInfoFactory.isSpdxListedLicenseId(id) || LicenseInfoFactory.isSpdxListedExceptionId(id)) {
			return IdType.ListedLicense;
		} else {
			return IdType.Unkown;
		}
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#create(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void create(String documentUri, String id, String type) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(type, "Missing required type");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			Model model = ModelFactory.createDefaultModel();
			model.getGraph().getPrefixMapping().setNsPrefix("spdx", SpdxConstants.SPDX_NAMESPACE);
			model.getGraph().getPrefixMapping().setNsPrefix("doap", SpdxConstants.DOAP_NAMESPACE);
			modelManager = new RdfSpdxDocumentModelManager(documentUri, model);
			RdfSpdxDocumentModelManager previousModel = documentUriModelMap.putIfAbsent(documentUri, modelManager);
			if (!Objects.isNull(previousModel))  {
				modelManager = previousModel;
			}
		}
		modelManager.create(id, type);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getPropertyValueNames(java.lang.String, java.lang.String)
	 */
	@Override
	public List<String> getPropertyValueNames(String documentUri, String id) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			return  new ArrayList<>();
		}
		return modelManager.getPropertyValueNames(id);
	}



	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#setValue(java.lang.String, java.lang.String, java.lang.String, java.lang.Object)
	 */
	@Override
	public void setValue(String documentUri, String id, String propertyName, Object value)
			throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(id);
		Objects.requireNonNull(documentUri);
		Objects.requireNonNull(propertyName);
		Objects.requireNonNull(value);
		RdfSpdxDocumentModelManager modelManager = this.documentUriModelMap.get(documentUri);
		if (modelManager == null) {
			logger.error("Attempting to set a value for a non-existent document URI "+documentUri+" ID: "+id);
			throw new SpdxIdNotFoundException("Document URI "+documentUri+" was not found in the RDF store.  The ID must first be created before getting or setting property values.");
		}
		modelManager.setValue(id, propertyName, value);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getValue(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Optional<Object> getValue(String documentUri, String id, String propertyName)
			throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			return Optional.empty();
		}
		return modelManager.getPropertyValue(id, propertyName);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getNextId(org.spdx.storage.IModelStore.IdType, java.lang.String)
	 */
	@Override
	public String getNextId(IdType idType, String documentUri) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(idType, "Missing required ID type");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			Model model = ModelFactory.createDefaultModel();
			model.getGraph().getPrefixMapping().setNsPrefix("spdx", SpdxConstants.SPDX_NAMESPACE);
			model.getGraph().getPrefixMapping().setNsPrefix("doap", SpdxConstants.DOAP_NAMESPACE);
			modelManager = new RdfSpdxDocumentModelManager(documentUri, model);
			RdfSpdxDocumentModelManager previousModel = documentUriModelMap.putIfAbsent(documentUri, modelManager);
			if (!Objects.isNull(previousModel))  {
				modelManager = previousModel;
			}
		}
		return modelManager.getNextId(idType);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#removeProperty(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void removeProperty(String documentUri, String id, String propertyName) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			logger.error("The document "+documentUri+" has not been created.  Can not remove a property.");
			throw new SpdxRdfException("The document has not been created.  Can not remove property");
		}
		modelManager.removeProperty(id, propertyName);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getDocumentUris()
	 */
	@Override
	public List<String> getDocumentUris() {
		return Collections.unmodifiableList(new ArrayList<String>(this.documentUriModelMap.keySet()));
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getAllItems(java.lang.String, java.util.Optional)
	 */
	@Override
	public Stream<TypedValue> getAllItems(String documentUri, String typeFilter)
			throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			return new ArrayList<TypedValue>().stream();
		}
		return modelManager.getAllItems(typeFilter);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#removeValueFromCollection(java.lang.String, java.lang.String, java.lang.String, java.lang.Object)
	 */
	public boolean removeValueFromCollection(String documentUri, String id, String propertyName, Object value)
			throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(value, "Mising required value");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			logger.error("The document "+documentUri+" has not been created.  Can not remove a value from a collection.");
			throw new SpdxRdfException("The document has not been created.  Can not remove value from a collection");
		}
		return modelManager.removeValueFromCollection(id, propertyName, value);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#collectionSize(java.lang.String, java.lang.String, java.lang.String)
	 */
	public int collectionSize(String documentUri, String id, String propertyName) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			logger.error("The document "+documentUri+" has not been created.  Can not retrieve a collection.");
			throw new SpdxRdfException("The document has not been created.  Can not retrieve a collection.");
		}
		return modelManager.collectionSize(id, propertyName);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#collectionContains(java.lang.String, java.lang.String, java.lang.String, java.lang.Object)
	 */
	public boolean collectionContains(String documentUri, String id, String propertyName, Object value)
			throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(value, "Missing required value");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			logger.error("The document "+documentUri+" has not been created.  Can not retrieve a collection.");
			throw new SpdxRdfException("The document has not been created.  Can not retrieve a collection.");
		}
		return modelManager.collectionContains(id, propertyName, value);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#clearValueCollection(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void clearValueCollection(String documentUri, String id, String propertyName)
			throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			logger.error("The document "+documentUri+" has not been created.  Can not retrieve a collection.");
			throw new SpdxRdfException("The document has not been created.  Can not retrieve a collection.");
		}
		modelManager.clearValueCollection(id, propertyName);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#addValueToCollection(java.lang.String, java.lang.String, java.lang.String, java.lang.Object)
	 */
	public boolean addValueToCollection(String documentUri, String id, String propertyName, Object value)
			throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			logger.error("The document "+documentUri+" has not been created.  Can not add a collection.");
			throw new SpdxRdfException("The document has not been created.  Can not add a collection.");
		}
		return modelManager.addValueToCollection(id, propertyName, value);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#getValueList(java.lang.String, java.lang.String, java.lang.String)
	 */
	public Iterator<Object> listValues(String documentUri, String id, String propertyName)
			throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			logger.error("The document "+documentUri+" has not been created.  Can not get a collection.");
			throw new SpdxRdfException("The document has not been created.  Can not get a collection.");
		}
		return modelManager.getValueList(id, propertyName);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#isCollectionMembersAssignableTo(java.lang.String, java.lang.String, java.lang.String, java.lang.Class)
	 */
	public boolean isCollectionMembersAssignableTo(String documentUri, String id, String propertyName, Class<?> clazz)
			throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(clazz, "Missing required class");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			logger.error("The document "+documentUri+" has not been created.  Can not get a collection.");
			throw new SpdxRdfException("The document has not been created.  Can not get a collection.");
		}
		return modelManager.isCollectionMembersAssignableTo(id, propertyName, clazz);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#isPropertyValueAssignableTo(java.lang.String, java.lang.String, java.lang.String, java.lang.Class)
	 */
	public boolean isPropertyValueAssignableTo(String documentUri, String id, String propertyName, Class<?> clazz)
			throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		Objects.requireNonNull(clazz, "Missing required class");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			logger.error("The document "+documentUri+" has not been created.  Can not check property for assignability.");
			throw new SpdxRdfException("The document has not been created.  Can not check property for assignability.");
		}
		return modelManager.isPropertyValueAssignableTo(id, propertyName, clazz);
	}

	/* (non-Javadoc)
	 * @see org.spdx.storage.IModelStore#isCollectionProperty(java.lang.String, java.lang.String, java.lang.String)
	 */
	public boolean isCollectionProperty(String documentUri, String id, String propertyName)
			throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		Objects.requireNonNull(id, "Missing required ID");
		Objects.requireNonNull(propertyName, "Missing required property name");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			logger.error("The document "+documentUri+" has not been created.  Can not check property for collection.");
			throw new SpdxRdfException("The document has not been created.  Can not check property for collection.");
		}
		return modelManager.isCollectionProperty(id, propertyName);
	}

	@Override
	public void leaveCriticalSection(IModelStoreLock lock) {
		lock.unlock();
	}

	@Override
	public IModelStoreLock enterCriticalSection(String documentUri, boolean readLockRequested) throws InvalidSPDXAnalysisException {
		Objects.requireNonNull(documentUri, "Missing required document URI");
		RdfSpdxDocumentModelManager modelManager = documentUriModelMap.get(documentUri);
		if (Objects.isNull(modelManager)) {
			logger.error("The document "+documentUri+" has not been created.  Can not enter critical section for a document that has not been created.");
			throw new InvalidSPDXAnalysisException("Can not enter a critical section for a document which has not been created in the RDF store.");
		}
		return modelManager.enterCriticalSection(readLockRequested);
	}
	
	/**
	 * Load a document from a file or URL
	 * @param fileNameOrUrl
	 * @param overwrite if true, overwrite any existing documents with the same document URI
	 * @return the DocumentURI of the SPDX document
	 * @throws InvalidSPDXAnalysisException
	 * @throws IOException 
	 */
	public String loadModelFromFile(String fileNameOrUrl, boolean overwrite) throws InvalidSPDXAnalysisException, IOException {
		InputStream spdxRdfInput = FileManager.get().open(fileNameOrUrl);
		if (Objects.isNull(spdxRdfInput)) {
			throw new SpdxRdfException("File not found: "+fileNameOrUrl);
		}
		try {
			return deSerialize(spdxRdfInput, overwrite);
		} finally {
			try {
				spdxRdfInput.close();
			} catch (IOException e) {
				logger.warn("Error closing SPDX RDF file "+fileNameOrUrl,e);
			}
		}
	}
	
	/**
	 * Form the document namespace URI from the SPDX document URI
	 * @param docUriString String form of the SPDX document URI
	 * @return
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
	 * @return the spdx doc node from the model
	 */
	public static Node getSpdxDocNode(Model model) {
		Node spdxDocNode = null;
		Node rdfTypePredicate = model.getProperty(SpdxConstants.RDF_NAMESPACE, SpdxConstants.RDF_PROP_TYPE).asNode();
		Node spdxDocObject = model.getProperty(SpdxConstants.SPDX_NAMESPACE, SpdxConstants.CLASS_SPDX_DOCUMENT).asNode();
		Triple m = Triple.createMatch(null, rdfTypePredicate, spdxDocObject);
		ExtendedIterator<Triple> tripleIter = model.getGraph().find(m);	// find the document
		while (tripleIter.hasNext()) {
			Triple docTriple = tripleIter.next();
			spdxDocNode = docTriple.getSubject();
		}
		return spdxDocNode;
	}
	
	/**
	 * @param model model containing a single document
	 * @return the document namespace for the document stored in the model
	 * @throws InvalidSPDXAnalysisException 
	 */
	public static String getDocumentNamespace(Model model) throws InvalidSPDXAnalysisException {
		Node documentNode = getSpdxDocNode(model);
		if (documentNode == null) {
			throw(new InvalidSPDXAnalysisException("Invalid model - must contain an SPDX Document"));
		}
		if (!documentNode.isURI()) {
			throw(new InvalidSPDXAnalysisException("SPDX Documents must have a unique URI"));
		}
		String docUri = documentNode.getURI();
		return formDocNamespace(docUri);
	}

	@Override
	public void serialize(String documentUri, OutputStream stream) throws InvalidSPDXAnalysisException, IOException {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public String deSerialize(InputStream stream, boolean overwrite) throws InvalidSPDXAnalysisException, IOException {
		Model model = ModelFactory.createDefaultModel();
		model.read(stream, null);
		String documentNamespace = getDocumentNamespace(model);
		RdfSpdxDocumentModelManager modelManager = new RdfSpdxDocumentModelManager(documentNamespace, model);
		CompatibilityUpgrader.upgrade(model);
		RdfSpdxDocumentModelManager previousModel = documentUriModelMap.putIfAbsent(documentNamespace, modelManager);
		if (!Objects.isNull(previousModel))  {
			if (overwrite) {
				logger.warn("Overwriting previous model from file for document URI "+documentNamespace);
				documentUriModelMap.put(documentNamespace, modelManager);
			} else {
				throw new SpdxRdfException("Document "+documentNamespace+" is already open in the RDF Store");
			}
		}
		return documentNamespace;
	}
}
