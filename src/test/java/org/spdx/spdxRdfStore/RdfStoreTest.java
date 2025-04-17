/**
 * SPDX-FileCopyrightText: Copyright (c) 2020 Source Auditor Inc.
 * SPDX-FileType: SOURCE
 * SPDX-License-Identifier: Apache-2.0
 */
package org.spdx.spdxRdfStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.ModelRegistry;
import org.spdx.core.TypedValue;
import org.spdx.library.model.v2.ExternalRef;
import org.spdx.library.model.v2.ReferenceType;
import org.spdx.library.model.v2.Relationship;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.SpdxElement;
import org.spdx.library.model.v2.SpdxFile;
import org.spdx.library.model.v2.SpdxModelFactoryCompatV2;
import org.spdx.library.model.v2.SpdxModelInfoV2_X;
import org.spdx.library.model.v2.SpdxPackage;
import org.spdx.library.model.v2.enumerations.RelationshipType;
import org.spdx.library.model.v3_0_1.SpdxModelInfoV3_0;
import org.spdx.storage.compatv2.CompatibleModelStoreWrapper;

import junit.framework.TestCase;

/**
 * @author Gary O'Neall
 */
public class RdfStoreTest extends TestCase {

	private static final String ID_2 = SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM + "2";
	private static final String ID_3 = SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM + "3";
	private static final String ID_4 = SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM + "4";
	private static final String DOCUMENT_URI1 = "https://spdx.org.document1";
	
	private static final String TEST_FILE_NAME = "TestFiles" + File.separator + "SPDXRdfExample.rdf";
	private static final String TEST_FILE_HTTPS_NAME = "TestFiles" + File.separator + "SPDXRdfExampleHttps.rdf";   // copy of the SPDXRdfExample file with the listed license URL http string replaced with https
	private static final String TEST_FILE_NAMESPACE = "http://spdx.org/spdxdocs/spdx-example-444504E0-4F89-41D3-9A0C-0305E82C3301";
	private static final String HAS_FILE_FILE_PATH = "TestFiles" + File.separator + "withHasFile.rdf";
	private static final String HAS_FILE_AND_CONTAINS_PATH  = "TestFiles" + File.separator + "withHasFileAndContains.rdf";

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV2_X());
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV3_0());
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	public void testClose() throws Exception {
		RdfStore rdfStore = null;
		try {
			rdfStore = new RdfStore(DOCUMENT_URI1);
			rdfStore.create(new TypedValue(DOCUMENT_URI1 + "#" + SpdxConstantsCompatV2.SPDX_DOCUMENT_ID, 
					SpdxConstantsCompatV2.CLASS_SPDX_DOCUMENT, CompatibleModelStoreWrapper.LATEST_SPDX_2X_VERSION));
		} finally {
		    if (Objects.nonNull(rdfStore)) {
		        rdfStore.close();
		    }
		}
		try {
			rdfStore.create(new TypedValue(DOCUMENT_URI1 + "#" + SpdxConstantsCompatV2.SPDX_DOCUMENT_ID, 
					SpdxConstantsCompatV2.CLASS_SPDX_DOCUMENT, CompatibleModelStoreWrapper.LATEST_SPDX_2X_VERSION));
			fail("Able to create items in a closed RDF store");
		} catch (InvalidSPDXAnalysisException ex) {
			// expected
		}
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfStore#getAllItems(java.lang.String, java.lang.String)}.
	 */
	public void testGetAllItems() throws InvalidSPDXAnalysisException {
		RdfStore rdfStore = new RdfStore(DOCUMENT_URI1);
		SpdxModelFactoryCompatV2.createModelObjectV2(rdfStore, DOCUMENT_URI1, SpdxConstantsCompatV2.SPDX_DOCUMENT_ID, SpdxConstantsCompatV2.CLASS_SPDX_DOCUMENT, null);
		SpdxModelFactoryCompatV2.createModelObjectV2(rdfStore, DOCUMENT_URI1, ID_2, SpdxConstantsCompatV2.CLASS_SPDX_FILE, null);
		SpdxModelFactoryCompatV2.createModelObjectV2(rdfStore, DOCUMENT_URI1, ID_3, SpdxConstantsCompatV2.CLASS_SPDX_FILE, null);
		SpdxModelFactoryCompatV2.createModelObjectV2(rdfStore, DOCUMENT_URI1, ID_4, SpdxConstantsCompatV2.CLASS_SPDX_FILE, null);
		try (Stream<TypedValue> result = rdfStore.getAllItems(DOCUMENT_URI1, SpdxConstantsCompatV2.CLASS_SPDX_FILE)) {
	        final ArrayList<TypedValue> resultList = new ArrayList<>();
            resultList.add(new TypedValue(DOCUMENT_URI1 + "#" + ID_2, SpdxConstantsCompatV2.CLASS_SPDX_FILE, CompatibleModelStoreWrapper.LATEST_SPDX_2X_VERSION));
            resultList.add(new TypedValue(DOCUMENT_URI1 + "#" + ID_3, SpdxConstantsCompatV2.CLASS_SPDX_FILE, CompatibleModelStoreWrapper.LATEST_SPDX_2X_VERSION));
            resultList.add(new TypedValue(DOCUMENT_URI1 + "#" + ID_4, SpdxConstantsCompatV2.CLASS_SPDX_FILE, CompatibleModelStoreWrapper.LATEST_SPDX_2X_VERSION));
            for (TypedValue tv:result.collect(Collectors.toList())) {
                assertTrue(resultList.contains(tv));
                resultList.remove(tv);
            }
            assertEquals(0, resultList.size());
		}
	}
	
	   public void testInconsistentLicenseUri() throws InvalidSPDXAnalysisException, IOException {
	       // See issue #1 - inconsistent use of http:// https:// in license Id's are causing issues
	       // Test file uses the following references to listed licenses:
	       //    http://spdx.org/licenses/CC0-1.0 - not defined within the RDF file, just a URI reference
	       //    http://spdx.org/licenses/LGPL-2.0-only - Defined using http:// referenced https://
	       //    https://spdx.org/licenses/GPL-2.0-only - Defined and referenced using https
	       //    http://spdx.org/licenses/Apache-2.0 - defined and referenced using http
	       //    https://spdx.org/licenses/MPL-1.0 - not defined within the RDF file, just a URI reference with https
	       RdfStore rdfStore = new RdfStore(DOCUMENT_URI1);
	       rdfStore.loadModelFromFile(TEST_FILE_HTTPS_NAME, false);
	       SpdxDocument doc = new SpdxDocument(rdfStore, TEST_FILE_NAMESPACE, null, false);
	       List<String> warnings = doc.verify();
	       assertEquals(0, warnings.size());
	    }
	
	public void testDelete() throws InvalidSPDXAnalysisException {
		RdfStore rdfStore = new RdfStore(DOCUMENT_URI1);
		SpdxModelFactoryCompatV2.createModelObjectV2(rdfStore, DOCUMENT_URI1, SpdxConstantsCompatV2.SPDX_DOCUMENT_ID, SpdxConstantsCompatV2.CLASS_SPDX_DOCUMENT, null);
		assertTrue(rdfStore.exists(DOCUMENT_URI1 + "#" + SpdxConstantsCompatV2.SPDX_DOCUMENT_ID));
		rdfStore.delete(DOCUMENT_URI1 + "#" + SpdxConstantsCompatV2.SPDX_DOCUMENT_ID);
		assertFalse(rdfStore.exists(DOCUMENT_URI1 + "#" + SpdxConstantsCompatV2.SPDX_DOCUMENT_ID));
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfStore#loadModelFromFile(java.lang.String, boolean)}.
	 * @throws IOException 
	 */
	public void testLoadModelFromFile() throws InvalidSPDXAnalysisException, IOException {
		RdfStore rdfStore = new RdfStore(DOCUMENT_URI1);
		rdfStore.loadModelFromFile(TEST_FILE_NAME, false);
		SpdxDocument doc = new SpdxDocument(rdfStore, TEST_FILE_NAMESPACE, null, false);
		Collection<SpdxElement> documentDescribes = doc.getDocumentDescribes();
		assertEquals(2, documentDescribes.size());
		SpdxFile describedFile = null;
		SpdxPackage describedPackage = null;
		for (SpdxElement described:documentDescribes) {
			if (described instanceof SpdxFile) {
				describedFile = (SpdxFile)described;
			} else if (described instanceof SpdxPackage) {
				describedPackage = (SpdxPackage)described;
			} else {
				fail("Unknown type for document describes");
			}
		}
		assertEquals("SPDXRef-Package", describedPackage.getId());
		assertEquals("SPDXRef-File", describedFile.getId());
		
		// change the document to test overwrite
		documentDescribes.remove(describedFile);
		assertEquals(1, documentDescribes.size());
		assertTrue(documentDescribes.contains(describedPackage));
		// test for errors in the ReferenceType
		Collection<ExternalRef> externalRefs = describedPackage.getExternalRefs();
		for (ExternalRef externalRef:externalRefs) {
			ReferenceType refType = externalRef.getReferenceType();
			String refUri = refType.getIndividualURI();
			assertFalse(refUri.isEmpty());
		}
		
		try {
			rdfStore.loadModelFromFile(TEST_FILE_NAME, false);
			fail("this should have failed since the document URI already exists");
		} catch (SpdxRdfException ex) {
			// expected
		}
		documentDescribes = doc.getDocumentDescribes();
		assertEquals(1, documentDescribes.size());
		assertTrue(documentDescribes.contains(describedPackage));
		
		rdfStore.loadModelFromFile(TEST_FILE_NAME, true);
		documentDescribes = doc.getDocumentDescribes();
		assertEquals(2, documentDescribes.size());
	}
	
	public void testHandleHasFile() throws InvalidSPDXAnalysisException, IOException {
		RdfStore rdfStore = new RdfStore(DOCUMENT_URI1);
		String documentUri = rdfStore.loadModelFromFile(HAS_FILE_FILE_PATH, false);
		Model model = rdfStore.modelManager.model;
		String query = "SELECT ?s ?o  WHERE { ?s  <http://spdx.org/rdf/terms#hasFile> ?o }";
		try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
			 ResultSet result = qe.execSelect();
			 assertFalse(result.hasNext());
		}
		SpdxPackage pkg = (SpdxPackage)SpdxModelFactoryCompatV2.getModelObjectV2(rdfStore, documentUri, "SPDXRef-Package", SpdxConstantsCompatV2.CLASS_SPDX_PACKAGE, null, false);
		boolean found = false;
		for (Relationship relationship:pkg.getRelationships()) {
			if (RelationshipType.CONTAINS.equals(relationship.getRelationshipType()) &&
					relationship.getRelatedSpdxElement().isPresent() &&
					"SPDXRef-DoapSource".equals(relationship.getRelatedSpdxElement().get().getId())) {
				assertFalse(found);
				found = true;
			}
		}
		assertTrue(found);
	}
	
	public void testDuplicateHasFiles() throws InvalidSPDXAnalysisException, IOException {
		RdfStore rdfStore = new RdfStore(DOCUMENT_URI1);
		String documentUri = rdfStore.loadModelFromFile(HAS_FILE_AND_CONTAINS_PATH, false);
		Model model = rdfStore.modelManager.model;
		String query = "SELECT ?s ?o  WHERE { ?s  <http://spdx.org/rdf/terms#hasFile> ?o }";
		try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
			 ResultSet result = qe.execSelect();
			 assertFalse(result.hasNext());
		}
		SpdxPackage pkg = (SpdxPackage)SpdxModelFactoryCompatV2.getModelObjectV2(rdfStore, documentUri, "SPDXRef-Package", SpdxConstantsCompatV2.CLASS_SPDX_PACKAGE, null, false);
		boolean found = false;
		for (Relationship relationship:pkg.getRelationships()) {
			if (RelationshipType.CONTAINS.equals(relationship.getRelationshipType()) &&
					relationship.getRelatedSpdxElement().isPresent() &&
					"SPDXRef-JenaLib".equals(relationship.getRelatedSpdxElement().get().getId())) {
				assertFalse(found);
				found = true;
			}
		}
		assertTrue(found);
	}
	
	public void testSerializeDeserialize() throws Exception {
		try (RdfStore rdfStore = new RdfStore()) {
			SpdxDocument result = null;
			try (InputStream spdxRdfInput = RDFDataMgr.open(TEST_FILE_NAME)) {
				result = rdfStore.deSerialize(spdxRdfInput, false);
				assertEquals("http://spdx.org/spdxdocs/spdx-example-444504E0-4F89-41D3-9A0C-0305E82C3301", result.getDocumentUri());
				assertEquals("http://spdx.org/spdxdocs/spdx-example-444504E0-4F89-41D3-9A0C-0305E82C3301", rdfStore.getDocumentUri());
				List<String> verify = result.verify();
				assertTrue(verify.isEmpty());
			}
			try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
				rdfStore.serialize(output);
				assertTrue(output.size() > 0);
			}
			try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
				rdfStore.serialize(output, result);
				assertTrue(output.size() > 0);
			}
		}
	}
}
