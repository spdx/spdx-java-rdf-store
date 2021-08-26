/**
 * 
 */
package org.spdx.spdxRdfStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.SpdxConstants;
import org.spdx.library.SpdxInvalidIdException;
import org.spdx.library.model.DuplicateSpdxIdException;
import org.spdx.library.model.TypedValue;
import org.spdx.storage.IModelStore.IdType;


import junit.framework.TestCase;

/**
 * @author gary
 *
 */
public class RdfSpdxDocumentModelManagerTest extends TestCase {
	
	static final String TEST_DOCUMENT_URI1 = "http://test.document.uri/1";
	
	static final String TEST_ID1 = "id1";
	static final String TEST_ID2 = "id2";

	static final String TEST_TYPE1 = SpdxConstants.CLASS_ANNOTATION;
	static final String TEST_TYPE2 = SpdxConstants.CLASS_RELATIONSHIP;
	static final String[] TEST_VALUE_PROPERTIES = new String[] {"valueProp1", "valueProp2", "valueProp3", "valueProp4"};
	static final Object[] TEST_VALUE_PROPERTY_VALUES = new Object[] {"value1", true, "value2", null};
	static final String[] TEST_LIST_PROPERTIES = new String[] {"listProp1", "listProp2", "listProp3"};

	protected static final int MAX_RETRIES = 10;
	TypedValue[] TEST_TYPED_PROP_VALUES;
	ArrayList<?>[] TEST_LIST_PROPERTY_VALUES;

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		TEST_LIST_PROPERTY_VALUES = new ArrayList<?>[] {new ArrayList<>(Arrays.asList("ListItem1", "listItem2", "listItem3")), 
			new ArrayList<>(Arrays.asList(true, false, true)),
			new ArrayList<>(Arrays.asList(new TypedValue("typeId1", TEST_TYPE1), new TypedValue("typeId2", TEST_TYPE2)))};
			TEST_VALUE_PROPERTY_VALUES[3] = new TypedValue("typeId3", TEST_TYPE1);
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testUpdateNextIds() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		// License ID's
		String nextId = store.getNextId(IdType.LicenseRef);
		assertEquals("LicenseRef-1", nextId);
		store.getOrCreate("LicenseRef-33", SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO);
		nextId = store.getNextId(IdType.LicenseRef);
		assertEquals("LicenseRef-34", nextId);
		
		// SPDX ID's
		nextId = store.getNextId(IdType.SpdxId);
		assertEquals("SPDXRef-1", nextId);
		store.getOrCreate("SPDXRef-33", SpdxConstants.CLASS_SPDX_FILE);
		nextId = store.getNextId(IdType.SpdxId);
		assertEquals("SPDXRef-34", nextId);
		
		// Anonymous ID's
		nextId = store.getNextId(IdType.Anonymous);
		assertTrue(nextId.startsWith(RdfStore.ANON_PREFIX));
		String nextNextId = store.getNextId(IdType.Anonymous);
		assertFalse(nextId.equals(nextNextId));
		assertTrue(nextNextId.startsWith(RdfStore.ANON_PREFIX));
		
		// Document ID
		nextId = store.getNextId(IdType.DocumentRef);
		assertEquals(SpdxConstants.EXTERNAL_DOC_REF_PRENUM + "1", nextId);
		store.getOrCreate(SpdxConstants.EXTERNAL_DOC_REF_PRENUM + "33", SpdxConstants.CLASS_EXTERNAL_DOC_REF);
		nextId = store.getNextId(IdType.DocumentRef);
		assertEquals(SpdxConstants.EXTERNAL_DOC_REF_PRENUM + "34", nextId);
		
		// test initialization of the next ID's
		RdfSpdxDocumentModelManager store2 = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		nextId = store2.getNextId(IdType.LicenseRef);
		assertEquals("LicenseRef-34", nextId);
		nextId = store2.getNextId(IdType.SpdxId);
		assertEquals("SPDXRef-34", nextId);
		nextId = store2.getNextId(IdType.Anonymous);
		assertTrue(nextId.startsWith(RdfStore.ANON_PREFIX));
		assertFalse(nextId.equals(nextNextId));
		nextId = store2.getNextId(IdType.DocumentRef);
		assertEquals(SpdxConstants.EXTERNAL_DOC_REF_PRENUM + "34", nextId);
	}
	
	
	public void testCreateExists() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		String id1 = "TestId1";
		String id2 = "testId2";
		assertFalse(store.exists(id1));
		assertFalse(store.exists(id2));
		store.getOrCreate(id1, SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO);
		assertTrue(store.exists(id1));
		assertFalse(store.exists(id2));
		store.getOrCreate(id2, SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO);
		assertTrue(store.exists(id1));
		assertTrue(store.exists(id2));
		
		// listed licenese
		String llId = "llid1";
		String llId2 = "llid2";
		assertFalse(store.exists(llId));
		assertFalse(store.exists(llId2));
		store.getOrCreate(llId, SpdxConstants.CLASS_SPDX_LISTED_LICENSE);
		assertTrue(store.exists(llId));
		assertFalse(store.exists(llId2));
		store.getOrCreate(llId2, SpdxConstants.CLASS_SPDX_LISTED_LICENSE);
		assertTrue(store.exists(llId));
		assertTrue(store.exists(llId2));
		
		// listed exception
		String exId = "exid1";
		String exId2 = "exid2";
		assertFalse(store.exists(exId));
		assertFalse(store.exists(exId2));
		store.getOrCreate(exId, SpdxConstants.CLASS_SPDX_LICENSE_EXCEPTION);
		assertTrue(store.exists(exId));
		assertFalse(store.exists(exId2));
		store.getOrCreate(exId2, SpdxConstants.CLASS_SPDX_LICENSE_EXCEPTION);
		assertTrue(store.exists(exId));
		assertTrue(store.exists(exId2));
		
		// anonymous
		String anon1 = store.getNextId(IdType.Anonymous);
		String anon2 = store.getNextId(IdType.Anonymous);
		assertFalse(store.exists(anon1));
		assertFalse(store.exists(anon2));
		store.getOrCreate(anon1, SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO);
		assertTrue(store.exists(anon1));
		assertFalse(store.exists(anon2));
		store.getOrCreate(anon2, SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO);
		assertTrue(store.exists(anon1));
		assertTrue(store.exists(anon2));
		
		// separate store with the same model
		RdfSpdxDocumentModelManager store2 = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		assertTrue(store2.exists(id1));
		assertTrue(store2.exists(id2));
		assertTrue(store2.exists(llId));
		assertTrue(store2.exists(llId2));
		assertTrue(store2.exists(exId));
		assertTrue(store2.exists(exId2));
		assertTrue(store2.exists(anon1));
		assertTrue(store2.exists(anon2));
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#getPropertyValueNames(java.lang.String)}.
	 * @throws InvalidSPDXAnalysisException 
	 */
	public void testGetPropertyValueNames() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_ANNOTATION);
		store.getOrCreate(TEST_ID2, SpdxConstants.CLASS_ANNOTATION);
		assertEquals(0, store.getPropertyValueNames(TEST_ID1).size());
		assertEquals(0, store.getPropertyValueNames(TEST_ID2).size());
		for (int i = 0; i < TEST_VALUE_PROPERTIES.length; i++) {
			store.setValue(TEST_ID1, TEST_VALUE_PROPERTIES[i], TEST_VALUE_PROPERTY_VALUES[i]);
		}
		for (int i = 0; i < TEST_LIST_PROPERTIES.length; i++) {
			for (Object value:TEST_LIST_PROPERTY_VALUES[i]) {
				store.addValueToCollection(TEST_ID1, TEST_LIST_PROPERTIES[i], value);
			}
		}
		List<String> result = store.getPropertyValueNames(TEST_ID1);
		assertEquals(TEST_VALUE_PROPERTIES.length + TEST_LIST_PROPERTIES.length, result.size());
		for (String prop:TEST_VALUE_PROPERTIES) {
			assertTrue(result.contains(prop));
		}
		for (String prop:TEST_LIST_PROPERTIES) {
			assertTrue(result.contains(prop));
		}
		assertEquals(0, store.getPropertyValueNames(TEST_ID2).size());		
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#setValue(java.lang.String, java.lang.String, java.lang.Object)}.
	 */
	public void testGetSetValue() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_ANNOTATION);
		store.getOrCreate(TEST_ID2, SpdxConstants.CLASS_ANNOTATION);
		assertFalse(store.getPropertyValue(TEST_ID1, TEST_VALUE_PROPERTIES[0]).isPresent());
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_VALUE_PROPERTIES[0]).isPresent());
		store.setValue(TEST_ID1, TEST_VALUE_PROPERTIES[0], TEST_VALUE_PROPERTY_VALUES[0]);
		Optional<Object> result = store.getPropertyValue(TEST_ID1, TEST_VALUE_PROPERTIES[0]);
		assertTrue(result.isPresent());
		assertEquals(TEST_VALUE_PROPERTY_VALUES[0], result.get());
		store.setValue(TEST_ID1, TEST_VALUE_PROPERTIES[0], TEST_VALUE_PROPERTY_VALUES[1]);
		assertEquals(TEST_VALUE_PROPERTY_VALUES[1], store.getPropertyValue(TEST_ID1, TEST_VALUE_PROPERTIES[0]).get());
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_VALUE_PROPERTIES[0]).isPresent());
	}
	
	public void testRenamedProperty() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_SPDX_DOCUMENT);
		String value = "2.1.2";
		store.setValue(TEST_ID1, SpdxConstants.PROP_SPDX_SPEC_VERSION, value);
		assertEquals(value, store.getPropertyValue(TEST_ID1, SpdxConstants.PROP_SPDX_SPEC_VERSION).get());
		assertEquals(value, store.getPropertyValue(TEST_ID1, SpdxConstants.PROP_SPDX_VERSION).get());
		List<String> allValues = store.getPropertyValueNames(TEST_ID1);
		assertEquals(1, allValues.size());
		assertEquals(SpdxConstants.PROP_SPDX_SPEC_VERSION, allValues.get(0));
	}
	
	public void testGetNextId() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		// License ID's
		String nextId = store.getNextId(IdType.LicenseRef);
		assertEquals("LicenseRef-1", nextId);
		nextId = store.getNextId(IdType.LicenseRef);
		assertEquals("LicenseRef-2", nextId);
		
		// SPDX ID's
		nextId = store.getNextId(IdType.SpdxId);
		assertEquals("SPDXRef-1", nextId);
		nextId = store.getNextId(IdType.SpdxId);
		assertEquals("SPDXRef-2", nextId);
		
		// Document ID
		nextId = store.getNextId(IdType.DocumentRef);
		assertEquals(SpdxConstants.EXTERNAL_DOC_REF_PRENUM + "1", nextId);
		nextId = store.getNextId(IdType.DocumentRef);
		assertEquals(SpdxConstants.EXTERNAL_DOC_REF_PRENUM + "2", nextId);
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#removeProperty(java.lang.String, java.lang.String)}.
	 */
	public void testRemoveProperty() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_ANNOTATION);
		store.getOrCreate(TEST_ID2, SpdxConstants.CLASS_ANNOTATION);
		assertFalse(store.getPropertyValue(TEST_ID1, TEST_LIST_PROPERTIES[0]).isPresent());
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_LIST_PROPERTIES[0]).isPresent());
		for (Object e:TEST_LIST_PROPERTY_VALUES[0]) {
			try {
				store.addValueToCollection(TEST_ID1, TEST_LIST_PROPERTIES[0], e);
				store.addValueToCollection(TEST_ID2, TEST_LIST_PROPERTIES[0], e);
			} catch (InvalidSPDXAnalysisException e1) {
				fail(e1.getMessage());
			}
		}

		assertCollectionsEquals(TEST_LIST_PROPERTY_VALUES[0], toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])));
		assertCollectionsEquals(TEST_LIST_PROPERTY_VALUES[0], toList(store.getValueList(TEST_ID2, TEST_LIST_PROPERTIES[0])));
		store.removeProperty(TEST_ID1, TEST_LIST_PROPERTIES[0]);
		assertFalse(store.getPropertyValue(TEST_ID1, TEST_LIST_PROPERTIES[0]).isPresent());
		for (Object e:TEST_LIST_PROPERTY_VALUES[0]) {
			try {
				store.addValueToCollection(TEST_ID2, TEST_LIST_PROPERTIES[0], e);
			} catch (InvalidSPDXAnalysisException e1) {
				fail(e1.getMessage());
			}
		}
	}
	
	private void assertCollectionsEquals(Object c1, Object c2) {
		if (!(c1 instanceof Collection)) {
			fail("c1 is not a collection");
		}
		if (!(c2 instanceof Collection)) {
			fail("c2 is not a collection");
		}
		Collection<?> col1 = (Collection<?>)c1;
		Collection<?> col2 = (Collection<?>)c2;
		assertEquals(col1.size(), col2.size());
		for (Object item:col1) {
			assertTrue(col2.contains(item));
		}
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#getAllItems(java.lang.String)}.
	 */
	public void testGetAllItems() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO);
		store.getOrCreate(TEST_ID2, SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO);
		assertEquals(0, store.getPropertyValueNames(TEST_ID1).size());
		assertEquals(0, store.getPropertyValueNames(TEST_ID2).size());
		for (int i = 0; i < TEST_VALUE_PROPERTIES.length; i++) {
			store.setValue(TEST_ID1, TEST_VALUE_PROPERTIES[i], TEST_VALUE_PROPERTY_VALUES[i]);
		}
		for (int i = 0; i < TEST_LIST_PROPERTIES.length; i++) {
			for (Object value:TEST_LIST_PROPERTY_VALUES[i]) {
				store.addValueToCollection(TEST_ID1, TEST_LIST_PROPERTIES[i], value);
			}
		}
		final ArrayList<TypedValue> expected = new ArrayList<>(Arrays.asList(new TypedValue[]{
				new TypedValue(TEST_ID1, SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO),
				new TypedValue(TEST_ID2, SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO),
				(TypedValue)TEST_VALUE_PROPERTY_VALUES[3],
				(TypedValue)TEST_LIST_PROPERTY_VALUES[2].get(0),
				(TypedValue)TEST_LIST_PROPERTY_VALUES[2].get(1)
		}));
		for (Object item:store.getAllItems(null).collect(Collectors.toList())) {
			assertTrue(expected.contains(item));
			expected.remove(item);
		}
		assertEquals(0, expected.size());
		
		// filtered
		final ArrayList<TypedValue> newexpected = new ArrayList<>(Arrays.asList(new TypedValue[]{
				new TypedValue(TEST_ID1, SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO),
				new TypedValue(TEST_ID2, SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO)
		}));
		for (Object item: store.getAllItems(SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO).collect(Collectors.toList())) {
			assertTrue(newexpected.contains(item));
			newexpected.remove(item);
		}
		assertEquals(0, newexpected.size());
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#beginTransaction(org.spdx.storage.IModelStore.ReadWrite)}.
	 * @throws InvalidSPDXAnalysisException 
	 * @throws IOException 
	 * Currently not supported
	 *TODO: Implment
	 *
	public void testBeginTransaction() throws InvalidSPDXAnalysisException, IOException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		IModelStore.ModelTransaction transaction = store.beginTransaction(IModelStore.ReadWrite.WRITE);
		try {
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_ANNOTATION);
		store.getOrCreate(TEST_ID2, SpdxConstants.CLASS_ANNOTATION);
		assertFalse(store.getPropertyValue(TEST_ID1, TEST_VALUE_PROPERTIES[0]).isPresent());
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_VALUE_PROPERTIES[0]).isPresent());
		store.setValue(TEST_ID1, TEST_VALUE_PROPERTIES[0], TEST_VALUE_PROPERTY_VALUES[0]);
		Optional<Object> result = store.getPropertyValue(TEST_ID1, TEST_VALUE_PROPERTIES[0]);
		assertTrue(result.isPresent());
		assertEquals(TEST_VALUE_PROPERTY_VALUES[0], result.get());
		store.setValue(TEST_ID1, TEST_VALUE_PROPERTIES[0], TEST_VALUE_PROPERTY_VALUES[1]);
		assertEquals(TEST_VALUE_PROPERTY_VALUES[1], store.getPropertyValue(TEST_ID1, TEST_VALUE_PROPERTIES[0]).get());
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_VALUE_PROPERTIES[0]).isPresent());
		} finally {
			transaction.commit();
			transaction.close();
		}
		assertEquals(TEST_VALUE_PROPERTY_VALUES[1], store.getPropertyValue(TEST_ID1, TEST_VALUE_PROPERTIES[0]).get());
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_VALUE_PROPERTIES[0]).isPresent());
	} */

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#removeValueFromCollection(java.lang.String, java.lang.String, java.lang.Object)}.
	 */
	public void testRemoveValueFromCollection() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_ANNOTATION);
		String value1 = "value1";
		String value2 = "value2";
		store.addValueToCollection( TEST_ID1, TEST_LIST_PROPERTIES[0], value1);
		store.addValueToCollection(TEST_ID1, TEST_LIST_PROPERTIES[0], value2);
		assertEquals(2, toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).size());
		assertTrue(toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).contains(value1));
		assertTrue(toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).contains(value2));
		assertTrue(store.removeValueFromCollection(TEST_ID1, TEST_LIST_PROPERTIES[0], value1));
		assertEquals(1, toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).size());
		assertFalse(toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).contains(value1));
		assertTrue(toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).contains(value2));
		assertFalse("Already removed - should return false",store.removeValueFromCollection(TEST_ID1, TEST_LIST_PROPERTIES[0], value1));
	}
	
	List<Object> toList(Iterator<Object> iter) {
		List<Object> retval = new ArrayList<Object>();
		while (iter.hasNext()) {
			retval.add(iter.next());
		}
		return retval;
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#collectionSize(java.lang.String, java.lang.String)}.
	 */
	public void testCollectionSize() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_ANNOTATION);
		store.getOrCreate(TEST_ID2, SpdxConstants.CLASS_ANNOTATION);
		assertFalse(store.getPropertyValue(TEST_ID1, TEST_LIST_PROPERTIES[0]).isPresent());
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_LIST_PROPERTIES[0]).isPresent());
		String value1 = "value1";
		String value2 = "value2";
		store.addValueToCollection(TEST_ID1, TEST_LIST_PROPERTIES[0], value1);
		store.addValueToCollection(TEST_ID1, TEST_LIST_PROPERTIES[0], value2);
		assertEquals(2, store.collectionSize(TEST_ID1, TEST_LIST_PROPERTIES[0]));
		assertEquals(0, store.collectionSize(TEST_ID1, TEST_LIST_PROPERTIES[1]));
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#collectionContains(java.lang.String, java.lang.String, java.lang.Object)}.
	 */
	public void testCollectionContains() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_ANNOTATION);
		store.getOrCreate(TEST_ID2, SpdxConstants.CLASS_ANNOTATION);
		assertFalse(store.getPropertyValue(TEST_ID1, TEST_LIST_PROPERTIES[0]).isPresent());
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_LIST_PROPERTIES[0]).isPresent());
		String value1 = "value1";
		String value2 = "value2";
		store.addValueToCollection(TEST_ID1, TEST_LIST_PROPERTIES[0], value1);
		store.addValueToCollection(TEST_ID1, TEST_LIST_PROPERTIES[0], value2);
		assertTrue(store.collectionContains(TEST_ID1, TEST_LIST_PROPERTIES[0],value1));
		assertTrue(store.collectionContains(TEST_ID1, TEST_LIST_PROPERTIES[0],value2));
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_LIST_PROPERTIES[0]).isPresent());
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#clearValueCollection(java.lang.String, java.lang.String)}.
	 */
	public void testClearValueCollection() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_ANNOTATION);
		store.getOrCreate(TEST_ID2, SpdxConstants.CLASS_ANNOTATION);
		assertFalse(store.getPropertyValue(TEST_ID1, TEST_LIST_PROPERTIES[0]).isPresent());
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_LIST_PROPERTIES[0]).isPresent());
		String value1 = "value1";
		String value2 = "value2";
		store.addValueToCollection(TEST_ID1, TEST_LIST_PROPERTIES[0], value1);
		store.addValueToCollection(TEST_ID1, TEST_LIST_PROPERTIES[0], value2);
		assertEquals(2, toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).size());
		assertTrue(toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).contains(value1));
		assertTrue(toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).contains(value2));
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_LIST_PROPERTIES[0]).isPresent());
		store.clearValueCollection(TEST_ID1, TEST_LIST_PROPERTIES[0]);
		assertEquals(0, toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).size());
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#addValueToCollection(java.lang.String, java.lang.String, java.lang.Object)}.
	 */
	public void testAddValueToCollection() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_ANNOTATION);
		store.getOrCreate(TEST_ID2, SpdxConstants.CLASS_ANNOTATION);
		assertFalse(store.getPropertyValue(TEST_ID1, TEST_LIST_PROPERTIES[0]).isPresent());
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_LIST_PROPERTIES[0]).isPresent());
		String value1 = "value1";
		String value2 = "value2";
		assertTrue(store.addValueToCollection(TEST_ID1, TEST_LIST_PROPERTIES[0], value1));
		assertTrue(store.addValueToCollection(TEST_ID1, TEST_LIST_PROPERTIES[0], value2));
		assertEquals(2, toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).size());
		assertTrue(toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).contains(value1));
		assertTrue(toList(store.getValueList(TEST_ID1, TEST_LIST_PROPERTIES[0])).contains(value2));
		assertFalse(store.getPropertyValue(TEST_ID2, TEST_LIST_PROPERTIES[0]).isPresent());
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#isCollectionMembersAssignableTo(java.lang.String, java.lang.String, java.lang.Class)}.
	 * @throws InvalidSPDXAnalysisException 
	 */
	public void testIsCollectionMembersAssignableTo() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_ANNOTATION);
		// String
		String sProperty = "stringprop";
		store.addValueToCollection(TEST_ID1, sProperty, "String 1");
		store.addValueToCollection(TEST_ID1, sProperty, "String 2");
		assertTrue(store.isCollectionMembersAssignableTo(TEST_ID1, sProperty, String.class));
		assertFalse(store.isCollectionMembersAssignableTo(TEST_ID1, sProperty, Boolean.class));
		assertFalse(store.isCollectionMembersAssignableTo(TEST_ID1, sProperty, TypedValue.class));
		// Boolean
		String bProperty = "boolprop";
		store.addValueToCollection(TEST_ID1, bProperty, Boolean.valueOf(true));
		store.addValueToCollection(TEST_ID1, bProperty, Boolean.valueOf(false));
		assertFalse(store.isCollectionMembersAssignableTo(TEST_ID1, bProperty, String.class));
		assertTrue(store.isCollectionMembersAssignableTo(TEST_ID1, bProperty, Boolean.class));
		assertFalse(store.isCollectionMembersAssignableTo(TEST_ID1, bProperty, TypedValue.class));
		// TypedValue
		String tvProperty  = "tvprop";
		store.addValueToCollection(TEST_ID1, tvProperty, new TypedValue(TEST_ID2, TEST_TYPE2));
		assertFalse(store.isCollectionMembersAssignableTo(TEST_ID1, tvProperty, String.class));
		assertFalse(store.isCollectionMembersAssignableTo(TEST_ID1, tvProperty, Boolean.class));
		assertTrue(store.isCollectionMembersAssignableTo(TEST_ID1, tvProperty, TypedValue.class));
		// Mixed
		String mixedProperty = "mixedprop";
		store.addValueToCollection(TEST_ID1, mixedProperty, new TypedValue(TEST_ID2, TEST_TYPE2));
		store.addValueToCollection(TEST_ID1, mixedProperty, Boolean.valueOf(true));
		store.addValueToCollection(TEST_ID1, mixedProperty, "mixed value");
		assertFalse(store.isCollectionMembersAssignableTo(TEST_ID1, mixedProperty, String.class));
		assertFalse(store.isCollectionMembersAssignableTo(TEST_ID1, mixedProperty, Boolean.class));
		assertFalse(store.isCollectionMembersAssignableTo(TEST_ID1, mixedProperty, TypedValue.class));
		// Empty
		String emptyProperty = "emptyprop";
		assertTrue(store.isCollectionMembersAssignableTo(TEST_ID1, emptyProperty, String.class));
		assertTrue(store.isCollectionMembersAssignableTo(TEST_ID1, emptyProperty, Boolean.class));
		assertTrue(store.isCollectionMembersAssignableTo(TEST_ID1, emptyProperty, TypedValue.class));
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#isPropertyValueAssignableTo(java.lang.String, java.lang.String, java.lang.Class)}.
	 */
	public void testIsPropertyValueAssignableTo() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_ANNOTATION);
		// String
		String sProperty = "stringprop";
		store.setValue(TEST_ID1, sProperty, "String 1");
		assertTrue(store.isPropertyValueAssignableTo(TEST_ID1, sProperty, String.class));
		assertFalse(store.isPropertyValueAssignableTo(TEST_ID1, sProperty, Boolean.class));
		assertFalse(store.isPropertyValueAssignableTo(TEST_ID1, sProperty, TypedValue.class));
		// Boolean
		String bProperty = "boolprop";
		store.setValue(TEST_ID1, bProperty, Boolean.valueOf(true));
		assertFalse(store.isPropertyValueAssignableTo(TEST_ID1, bProperty, String.class));
		assertTrue(store.isPropertyValueAssignableTo(TEST_ID1, bProperty, Boolean.class));
		assertFalse(store.isPropertyValueAssignableTo(TEST_ID1, bProperty, TypedValue.class));
		// TypedValue
		String tvProperty = "tvprop";
		store.setValue(TEST_ID1, tvProperty, new TypedValue(TEST_ID2, TEST_TYPE2));
		assertFalse(store.isPropertyValueAssignableTo(TEST_ID1, tvProperty, String.class));
		assertFalse(store.isPropertyValueAssignableTo(TEST_ID1, tvProperty, Boolean.class));
		assertTrue(store.isPropertyValueAssignableTo(TEST_ID1, tvProperty, TypedValue.class));
		// Empty
		String emptyProperty = "emptyprop";
		assertFalse(store.isPropertyValueAssignableTo(TEST_ID1, emptyProperty, String.class));
	}

	/**
	 * Test method for {@link org.spdx.spdxRdfStore.RdfSpdxDocumentModelManager#isCollectionProperty(java.lang.String, java.lang.String)}.
	 */
	public void testIsCollectionProperty() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_SPDX_CREATION_INFO);
		// String
		String sProperty = SpdxConstants.PROP_CREATION_CREATED;
		store.setValue(TEST_ID1, sProperty, "String 1");
		String listProperty = SpdxConstants.PROP_CREATION_CREATOR;
		store.addValueToCollection(TEST_ID1, listProperty, "testValue");
		store.addValueToCollection(TEST_ID1, listProperty, "testValue2");
		assertTrue(store.isCollectionProperty(TEST_ID1, listProperty));
		assertFalse(store.isCollectionProperty(TEST_ID1, sProperty));
	}
	
	public void testGetCasesensitiveId() throws SpdxInvalidIdException, DuplicateSpdxIdException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		String licenseId = SpdxConstants.NON_STD_LICENSE_ID_PRENUM+"NowisTheTime";
		String spdxId = SpdxConstants.SPDX_ELEMENT_REF_PRENUM + "AnAnother";
		String documentId = SpdxConstants.EXTERNAL_DOC_REF_PRENUM + "DocumentNextOne";
		store.getOrCreate(licenseId, SpdxConstants.CLASS_SPDX_EXTRACTED_LICENSING_INFO);
		store.getOrCreate(spdxId, SpdxConstants.CLASS_SPDX_FILE);
		store.getOrCreate(documentId, SpdxConstants.CLASS_EXTERNAL_DOC_REF);
		assertEquals(licenseId, store.getCasesensitiveId(licenseId.toUpperCase()).get());
		assertEquals(spdxId, store.getCasesensitiveId(spdxId.toUpperCase()).get());
		assertEquals(documentId, store.getCasesensitiveId(documentId.toUpperCase()).get());
		assertEquals(documentId, store.getCasesensitiveId(documentId.toLowerCase()).get());
		assertFalse(store.getCasesensitiveId("LicenseRef-NOtThere").isPresent());
		store.delete(spdxId);
		assertFalse(store.getCasesensitiveId(spdxId).isPresent());
	}

	public void testGetTypedValue() throws InvalidSPDXAnalysisException {
		Model model = ModelFactory.createDefaultModel();
		RdfSpdxDocumentModelManager store = new RdfSpdxDocumentModelManager(TEST_DOCUMENT_URI1, model);
		store.getOrCreate(TEST_ID1, SpdxConstants.CLASS_SPDX_CREATION_INFO);
		Optional<TypedValue> result = store.getTypedValue(TEST_ID1);
		assertTrue(result.isPresent());
		assertEquals(TEST_ID1, result.get().getId());
		assertEquals(SpdxConstants.CLASS_SPDX_CREATION_INFO, result.get().getType());
	}
}
