/**
 * 
 */
package org.spdx.spdxRdfStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.SpdxConstants;
import org.spdx.library.model.ExternalRef;
import org.spdx.library.model.ReferenceType;
import org.spdx.library.model.Relationship;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.SpdxElement;
import org.spdx.library.model.SpdxFile;
import org.spdx.library.model.SpdxModelFactory;
import org.spdx.library.model.SpdxPackage;
import org.spdx.library.model.TypedValue;
import org.spdx.library.model.enumerations.RelationshipType;

import junit.framework.TestCase;

/**
 * @author gary
 *
 */
public class RdfStoreTest extends TestCase {

	private static final String ID_2 = SpdxConstants.SPDX_ELEMENT_REF_PRENUM + "2";
	private static final String ID_3 = SpdxConstants.SPDX_ELEMENT_REF_PRENUM + "3";
	private static final String ID_4 = SpdxConstants.SPDX_ELEMENT_REF_PRENUM + "4";
	private static final String DOCUMENT_URI1 = "https://spdx.org.document1";
	private static final String DOCUMENT_URI2 = "https://spdx.org.document2";
	private static final String DOCUMENT_URI3 = "https://spdx.org.document3";
	private static final String DOCUMENT_URI4 = "https://spdx.org.document4";
	
	private static final String TEST_FILE_NAME = "TestFiles" + File.separator + "SPDXRdfExample.rdf";
	private static final String TEST_FILE_HTTPS_NAME = "TestFiles" + File.separator + "SPDXRdfExampleHttps.rdf";   // copy of the SPDXRdfExample file with the listed license URL http string replaced with https
	private static final String TEST_FILE_NAMESPACE = "http://spdx.org/spdxdocs/spdx-example-444504E0-4F89-41D3-9A0C-0305E82C3301";
	private static final String HAS_FILE_FILE_PATH = "TestFiles" + File.separator + "withHasFile.rdf";;
	

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfStore#getDocumentUris()}.
	 * @throws Exception 
	 */
	public void testGetDocumentUris() throws Exception {
		try (RdfStore rdfStore = new RdfStore()) {
			rdfStore.create(DOCUMENT_URI1, SpdxConstants.SPDX_DOCUMENT_ID, SpdxConstants.CLASS_SPDX_DOCUMENT);
			rdfStore.create(DOCUMENT_URI2, SpdxConstants.SPDX_DOCUMENT_ID, SpdxConstants.CLASS_SPDX_DOCUMENT);
			rdfStore.create(DOCUMENT_URI3, SpdxConstants.SPDX_DOCUMENT_ID, SpdxConstants.CLASS_SPDX_DOCUMENT);
			rdfStore.create(DOCUMENT_URI4, SpdxConstants.SPDX_DOCUMENT_ID, SpdxConstants.CLASS_SPDX_DOCUMENT);
			List<String> result = rdfStore.getDocumentUris();
			assertEquals(4, result.size());
			assertTrue(result.contains(DOCUMENT_URI1));
			assertTrue(result.contains(DOCUMENT_URI2));
			assertTrue(result.contains(DOCUMENT_URI3));
			assertTrue(result.contains(DOCUMENT_URI4));
		}
	}
	
	public void testClose() throws Exception {
		RdfStore rdfStore = null;
		try {
			rdfStore = new RdfStore();
			rdfStore.create(DOCUMENT_URI1, SpdxConstants.SPDX_DOCUMENT_ID, SpdxConstants.CLASS_SPDX_DOCUMENT);
			rdfStore.create(DOCUMENT_URI2, SpdxConstants.SPDX_DOCUMENT_ID, SpdxConstants.CLASS_SPDX_DOCUMENT);
			rdfStore.create(DOCUMENT_URI3, SpdxConstants.SPDX_DOCUMENT_ID, SpdxConstants.CLASS_SPDX_DOCUMENT);
			rdfStore.create(DOCUMENT_URI4, SpdxConstants.SPDX_DOCUMENT_ID, SpdxConstants.CLASS_SPDX_DOCUMENT);
			List<String> result = rdfStore.getDocumentUris();
			assertEquals(4, result.size());
			assertTrue(result.contains(DOCUMENT_URI1));
			assertTrue(result.contains(DOCUMENT_URI2));
			assertTrue(result.contains(DOCUMENT_URI3));
			assertTrue(result.contains(DOCUMENT_URI4));
		} finally {
		    if (Objects.nonNull(rdfStore)) {
		        rdfStore.close();
		    }
		}
		assertEquals(0, rdfStore.documentUriModelMap.size());
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfStore#getAllItems(java.lang.String, java.lang.String)}.
	 */
	public void testGetAllItems() throws InvalidSPDXAnalysisException {
		RdfStore rdfStore = new RdfStore();
		SpdxModelFactory.createModelObject(rdfStore, DOCUMENT_URI1, SpdxConstants.SPDX_DOCUMENT_ID, SpdxConstants.CLASS_SPDX_DOCUMENT, null);
		SpdxModelFactory.createModelObject(rdfStore, DOCUMENT_URI1, ID_2, SpdxConstants.CLASS_SPDX_FILE, null);
		SpdxModelFactory.createModelObject(rdfStore, DOCUMENT_URI1, ID_3, SpdxConstants.CLASS_SPDX_FILE, null);
		SpdxModelFactory.createModelObject(rdfStore, DOCUMENT_URI1, ID_4, SpdxConstants.CLASS_SPDX_FILE, null);
		try (Stream<TypedValue> result = rdfStore.getAllItems(DOCUMENT_URI1, SpdxConstants.CLASS_SPDX_FILE)) {
	        final ArrayList<TypedValue> resultList = new ArrayList<>();
            resultList.add(new TypedValue(ID_2, SpdxConstants.CLASS_SPDX_FILE));
            resultList.add(new TypedValue(ID_3, SpdxConstants.CLASS_SPDX_FILE));
            resultList.add(new TypedValue(ID_4, SpdxConstants.CLASS_SPDX_FILE));
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
	       RdfStore rdfStore = new RdfStore();
	       rdfStore.loadModelFromFile(TEST_FILE_HTTPS_NAME, false);
	       SpdxDocument doc = new SpdxDocument(rdfStore, TEST_FILE_NAMESPACE, null, false);
	       List<String> warnings = doc.verify();
	       assertEquals(0, warnings.size());
	    }
	
	public void testDelete() throws InvalidSPDXAnalysisException {
		RdfStore rdfStore = new RdfStore();
		SpdxModelFactory.createModelObject(rdfStore, DOCUMENT_URI1, SpdxConstants.SPDX_DOCUMENT_ID, SpdxConstants.CLASS_SPDX_DOCUMENT, null);
		assertTrue(rdfStore.exists(DOCUMENT_URI1, SpdxConstants.SPDX_DOCUMENT_ID));
		rdfStore.delete(DOCUMENT_URI1, SpdxConstants.SPDX_DOCUMENT_ID);
		assertFalse(rdfStore.exists(DOCUMENT_URI1, SpdxConstants.SPDX_DOCUMENT_ID));
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfStore#loadModelFromFile(java.lang.String, boolean)}.
	 * @throws IOException 
	 */
	public void testLoadModelFromFile() throws InvalidSPDXAnalysisException, IOException {
		RdfStore rdfStore = new RdfStore();
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
		RdfStore rdfStore = new RdfStore();
		String documentUri = rdfStore.loadModelFromFile(HAS_FILE_FILE_PATH, false);
		Model model = rdfStore.documentUriModelMap.get(documentUri).model;
		String query = "SELECT ?s ?o  WHERE { ?s  <http://spdx.org/rdf/terms#hasFile> ?o }";
		try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
			 ResultSet result = qe.execSelect();
			 assertFalse(result.hasNext());
		}
		SpdxPackage pkg = (SpdxPackage)SpdxModelFactory.getModelObject(rdfStore, documentUri, "SPDXRef-Package", SpdxConstants.CLASS_SPDX_PACKAGE, null, false);
		boolean found = false;
		for (Relationship relationship:pkg.getRelationships()) {
			if (RelationshipType.CONTAINS.equals(relationship.getRelationshipType()) &&
					relationship.getRelatedSpdxElement().isPresent() &&
					"SPDXRef-DoapSource".equals(relationship.getRelatedSpdxElement().get().getId())) {
				found = true;
				break;
			}
		}
		assertTrue(found);
	}

}
