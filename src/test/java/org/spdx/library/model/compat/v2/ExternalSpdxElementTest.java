/**
 * SPDX-FileCopyrightText: Copyright (c) 2024 Source Auditor Inc.
 * SPDX-FileType: SOURCE
 * SPDX-License-Identifier: Apache-2.0
 */
package org.spdx.library.model.compat.v2;


import java.util.Collection;

import org.spdx.core.DefaultModelStore;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.ModelRegistry;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.v2.Checksum;
import org.spdx.library.model.v2.ExternalSpdxElement;
import org.spdx.library.model.v2.GenericModelObject;
import org.spdx.library.model.v2.GenericSpdxElement;
import org.spdx.library.model.v2.Relationship;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.SpdxModelInfoV2_X;
import org.spdx.library.model.v2.Version;
import org.spdx.library.model.v2.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.v2.enumerations.RelationshipType;
import org.spdx.library.model.v3_0_1.SpdxModelInfoV3_0;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.IModelStore.IdType;

import junit.framework.TestCase;

public class ExternalSpdxElementTest extends TestCase {
	
	static final String DOCID1 = SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM + "DOCID1";
	static final String SPDXID1 = SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM + "SPDXID1";
	static final String DOCURI1 = "http://doc/uri/one";
	static final String ID1 = DOCID1 + ":" + SPDXID1;

	static final String DOCID2 = SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM + "DOCID1";
	static final String SPDXID2 = SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM + "SPDXID2";
	static final String DOCURI2 = "http://doc/uri/two";
	static final String ID2 = DOCID2 + ":" + SPDXID2;

	static final String DOCID3 = SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM + "DOCID3";
	static final String SPDXID3 = SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM + "SPDXID3";
	static final String DOCURI3 = "http://doc/uri/three";
	static final String ID3 = DOCID3 + ":" + SPDXID3;
	
	Checksum CHECKSUM1;
	Checksum CHECKSUM2;
	Checksum CHECKSUM3;
	
	GenericModelObject gmo;
	SpdxDocument doc;

	protected void setUp() throws Exception {
		super.setUp();
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV2_X());
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV3_0());
		DefaultModelStore.initialize(new RdfStore("http://defaultdocument"), "http://defaultdocument", new ModelCopyManager());
		gmo = new GenericModelObject();
		doc = new SpdxDocument(gmo.getModelStore(), gmo.getDocumentUri(), gmo.getCopyManager(), true);
		CHECKSUM1 = gmo.createChecksum(ChecksumAlgorithm.SHA1, "A94A8FE5CCB19BA61C4C0873D391E987982FBBD3");
		CHECKSUM2 = gmo.createChecksum(ChecksumAlgorithm.SHA1, "1086444D91D3A28ECA55124361F6DE2B93A9AE91");
		CHECKSUM3 = gmo.createChecksum(ChecksumAlgorithm.SHA1, "571D85D7752CB4E5C6D919BAC21FD2BAAE9F2FCA");
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testVerify() throws InvalidSPDXAnalysisException {
		gmo.createExternalDocumentRef(DOCID1, DOCURI1, CHECKSUM1);
		ExternalSpdxElement externalElement = new ExternalSpdxElement(DOCURI1, SPDXID1);
		assertEquals(0, externalElement.verify().size());
	}

	public void testGetExternalDocumentId() throws InvalidSPDXAnalysisException {
		doc.getExternalDocumentRefs().add(gmo.createExternalDocumentRef(DOCID1, DOCURI1, CHECKSUM1));
		ExternalSpdxElement externalElement = new ExternalSpdxElement(DOCURI1, SPDXID1);
		assertEquals(DOCID1, externalElement.getExternalDocumentId(doc));
	}

	public void testGetExternalElementId() throws InvalidSPDXAnalysisException {
		gmo.createExternalDocumentRef(DOCID1, DOCURI1, CHECKSUM1);
		ExternalSpdxElement externalElement = new ExternalSpdxElement(DOCURI1, SPDXID1);
		assertEquals(SPDXID1, externalElement.getExternalElementId());
	}
	
	public void testEquivalent() throws InvalidSPDXAnalysisException {
		gmo.createExternalDocumentRef(DOCID1, DOCURI1, CHECKSUM1);
		gmo.createExternalDocumentRef(DOCID2, DOCURI2, CHECKSUM2);
		ExternalSpdxElement externalElement = new ExternalSpdxElement(DOCURI1, SPDXID1);
		assertTrue(externalElement.equivalent(externalElement));
		ExternalSpdxElement externalElement2 = new ExternalSpdxElement(DOCURI1, SPDXID1);
		assertTrue(externalElement.equivalent(externalElement2));
		ExternalSpdxElement externalElement3 = new ExternalSpdxElement(DOCURI2, SPDXID2);
		assertFalse(externalElement.equivalent(externalElement3));
	}
	
	public void testUseInRelationship() throws InvalidSPDXAnalysisException {
		gmo.createExternalDocumentRef(DOCID1, DOCURI1, CHECKSUM1);
		ExternalSpdxElement externalElement = new ExternalSpdxElement(DOCURI1, SPDXID1);
		GenericSpdxElement element = new GenericSpdxElement(externalElement.getModelStore(), 
				externalElement.getDocumentUri(), 
				externalElement.getModelStore().getNextId(IdType.Anonymous), 
				externalElement.getCopyManager(), true);
		element.setName("Element1Name");
		Relationship relationship = element.createRelationship(externalElement, RelationshipType.AMENDS, "External relationship");
		GenericSpdxElement compare = new GenericSpdxElement(element.getModelStore(), element.getDocumentUri(),
				element.getId(), element.getCopyManager(), false);
		element.addRelationship(relationship);
		assertEquals("Element1Name", compare.getName().get());
		assertEquals("Element1Name", element.getName().get());
		Collection<Relationship> relCollection = compare.getRelationships();
		Relationship[] relArray = relCollection.toArray(new Relationship[1]);
		Relationship compareRelationship = relArray[0];
		assertEquals(RelationshipType.AMENDS, compareRelationship.getRelationshipType());
		assertEquals("External relationship", compareRelationship.getComment().get());
		ExternalSpdxElement compareRelatedElement = (ExternalSpdxElement)compareRelationship.getRelatedSpdxElement().get();
		assertEquals(SPDXID1, compareRelatedElement.getId());
		assertEquals(DOCID1, compareRelatedElement.getExternalDocumentId(doc));
		assertEquals(SPDXID1, compareRelatedElement.getExternalElementId());
	}
	
	public void testExternalElementReference() throws InvalidSPDXAnalysisException {
		Checksum checksum = gmo.createChecksum(ChecksumAlgorithm.SHA1, "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3");
		doc.getExternalDocumentRefs().add(gmo.createExternalDocumentRef(DOCID1, DOCURI1, checksum));
		String expected = DOCID1 + ":" + SPDXID1;
		ExternalSpdxElement ese = new ExternalSpdxElement(DOCURI1, SPDXID1);
		String result = ese.referenceElementId(doc);
		assertEquals(expected, result);
	}

	
	public void testUriToExternalSpdxElementId() throws InvalidSPDXAnalysisException {
		Checksum checksum = gmo.createChecksum(ChecksumAlgorithm.SHA1, "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3");
		gmo.createExternalDocumentRef(DOCID1, DOCURI1, checksum);
		String uri = DOCURI1 + "#" + SPDXID1;
		String expected = DOCID1 + ":" + SPDXID1;
		String result = ExternalSpdxElement.uriToExternalSpdxElementReference(uri, gmo.getModelStore(),
				gmo.getDocumentUri(), null, Version.TWO_POINT_THREE_VERSION);
		assertEquals(expected, result);
		uri = DOCURI2 + "#" + SPDXID2;
		String generatedDocId = "DocumentRef-gnrtd0";
		expected = generatedDocId + ":" + SPDXID2;
		try {
			result = ExternalSpdxElement.uriToExternalSpdxElementReference(uri, gmo.getModelStore(),
					gmo.getDocumentUri(), null, Version.TWO_POINT_THREE_VERSION);
			fail("Expected to fail since DOCID2 has not been created");
		} catch (InvalidSPDXAnalysisException e) {
			// expected
		}
		result = ExternalSpdxElement.uriToExternalSpdxElementReference(uri, gmo.getModelStore(),
				gmo.getDocumentUri(), gmo.getCopyManager(), Version.TWO_POINT_THREE_VERSION);
		assertTrue(result.startsWith("DocumentRef-"));
		assertTrue(result.endsWith(":" + SPDXID2));
		uri = DOCURI2 + "#" + SPDXID3;
		expected = generatedDocId + ":" + SPDXID3;
		result = ExternalSpdxElement.uriToExternalSpdxElementReference(uri, gmo.getModelStore(),
				gmo.getDocumentUri(), null, Version.TWO_POINT_THREE_VERSION);
		assertTrue(result.startsWith("DocumentRef-"));
		assertTrue(result.endsWith(":" + SPDXID3));
	}
}
