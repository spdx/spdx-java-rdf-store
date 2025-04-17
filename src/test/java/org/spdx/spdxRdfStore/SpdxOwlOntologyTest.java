/**
 * SPDX-FileCopyrightText: Copyright (c) 2020 Source Auditor Inc.
 * SPDX-FileType: SOURCE
 * SPDX-License-Identifier: Apache-2.0
 */
package org.spdx.spdxRdfStore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;

import junit.framework.TestCase;

/**
 * @author Gary O'Neall
 */
public class SpdxOwlOntologyTest extends TestCase {

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
	 * Test method for {@link org.spdx.spdxRdfStore.SpdxOwlOntology#getModel()}.
	 */
	public void testGetModel() {
		OntModel model = SpdxOwlOntology.getSpdxOwlOntology().getModel();
		assertTrue(Objects.nonNull(model));
	}

	public void testIsList() throws SpdxRdfException {
		assertTrue(SpdxOwlOntology.getSpdxOwlOntology().isList(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_SPDX_FILE,
				SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_FILE_TYPE.getName()));
		assertFalse(SpdxOwlOntology.getSpdxOwlOntology().isList(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_SPDX_CREATION_INFO,
				SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_CREATION_CREATED.getName()));
		assertFalse(SpdxOwlOntology.getSpdxOwlOntology().isList(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_SPDX_PACKAGE,
				SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_LICENSE_DECLARED.getName()));
	}
	
	public void testClassesInOntology() {
		for (String className:SpdxConstantsCompatV2.ALL_SPDX_CLASSES) {
			if (!SpdxConstantsCompatV2.CLASS_NONE_LICENSE.equals(className) && 
					!SpdxConstantsCompatV2.CLASS_NOASSERTION_LICENSE.equals(className) && 
					!SpdxConstantsCompatV2.CLASS_EXTERNAL_SPDX_ELEMENT.equals(className) &&
					!SpdxConstantsCompatV2.CLASS_EXTERNAL_EXTRACTED_LICENSE.equals(className) &&
					!SpdxConstantsCompatV2.CLASS_SPDX_NONE_ELEMENT.equals(className) &&
					!SpdxConstantsCompatV2.CLASS_SPDX_NOASSERTION_ELEMENT.equals(className)) {
				assertNotNull(className+" not present in ontology.",SpdxOwlOntology.getSpdxOwlOntology().getModel().getOntClass(
						SpdxResourceFactory.classNameToUri(className)));
			}
		}
	}
	
	public void testGetClassUriRestrictions() throws SpdxRdfException {
		List<String> result = SpdxOwlOntology.getSpdxOwlOntology().getClassUriRestrictions(
				SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_SPDX_PACKAGE,
				SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_ANNOTATION.getName());
		assertEquals(1, result.size());
		assertEquals(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_ANNOTATION, result.get(0));
	}
	
	public void testGetDataUriRestrictions() throws SpdxRdfException {
		List<String> result = SpdxOwlOntology.getSpdxOwlOntology().getDataUriRestrictions(
				SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_SPDX_PACKAGE,
				SpdxConstantsCompatV2.RDFS_NAMESPACE + SpdxConstantsCompatV2.RDFS_PROP_COMMENT.getName());
		assertEquals(1, result.size());
		assertEquals("http://www.w3.org/2001/XMLSchema#string", result.get(0));
	}
	
	public void testGetPropertyClass() throws SpdxRdfException {
		Model model = ModelFactory.createDefaultModel();
		Optional<Class<? extends Object>> dataClass = SpdxOwlOntology.getSpdxOwlOntology().getPropertyClass(
					model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_COPYRIGHT_TEXT.getName()));
		assertEquals(Optional.of(String.class), dataClass);
		dataClass = SpdxOwlOntology.getSpdxOwlOntology().getPropertyClass(
				model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_STD_LICENSE_FSF_LIBRE.getName()));
	assertEquals(Optional.of(Boolean.class), dataClass);
	dataClass = SpdxOwlOntology.getSpdxOwlOntology().getPropertyClass(
			model.createProperty(SpdxConstantsCompatV2.RDF_POINTER_NAMESPACE + SpdxConstantsCompatV2.PROP_POINTER_OFFSET.getName()));
	assertEquals(Optional.of(Integer.class), dataClass);
	}
	 
	public void testCheckGetOwlUriFromRenamed() {
		assertEquals(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_SPDX_VERSION.getName(), 
				SpdxOwlOntology.checkGetOwlUriFromRenamed(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_SPDX_SPEC_VERSION.getName()));
	}
	
	public void testCheckGetRenamedUri() {
		assertEquals(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_SPDX_SPEC_VERSION.getName(), 
				SpdxOwlOntology.checkGetRenamedUri(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_SPDX_VERSION.getName()));
	}
}
