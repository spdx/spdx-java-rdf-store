/**
 * SPDX-FileCopyrightText: Copyright (c) 2019 Source Auditor Inc.
 * SPDX-FileType: SOURCE
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
package org.spdx.library.model.compat.v2;

import java.util.Optional;

import org.spdx.core.DefaultModelStore;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.ModelRegistry;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.v2.Checksum;
import org.spdx.library.model.v2.ExternalDocumentRef;
import org.spdx.library.model.v2.GenericModelObject;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.SpdxModelFactoryCompatV2;
import org.spdx.library.model.v2.SpdxModelInfoV2_X;
import org.spdx.library.model.v2.Version;
import org.spdx.library.model.v2.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.v3_0_1.SpdxModelInfoV3_0;
import org.spdx.spdxRdfStore.RdfStore;

import junit.framework.TestCase;

/**
 * @author Gary O'Neall
 */
public class ExternalDocumentRefTest extends TestCase {
	
	static final String SHA1_VALUE1 = "2fd4e1c67a2d28fced849ee1bb76e7391b93eb12";
	static final String SHA1_VALUE2 = "2222e1c67a2d28fced849ee1bb76e7391b93eb12";
	Checksum CHECKSUM1;
	Checksum CHECKSUM2;
	static final String DOCUMENT_URI1 = "http://spdx.org/docs/uniquevalue1";
	static final String DOCUMENT_URI2 = "http://spdx.org/docs/uniquevalue2";
	static final String DOCUMENT_ID1 = "DocumentRef-1";
	static final String DOCUMENT_ID2 = "DocumentRef-2";
	
	GenericModelObject gmo;

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV2_X());
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV3_0());
		DefaultModelStore.initialize(new RdfStore("http://defaultdocument"), "http://defaultdocument", new ModelCopyManager());
		gmo = new GenericModelObject();
		new SpdxDocument(gmo.getModelStore(), gmo.getDocumentUri(), gmo.getCopyManager(), true);
		CHECKSUM1 = gmo.createChecksum(ChecksumAlgorithm.SHA1, SHA1_VALUE1);
		CHECKSUM2 = gmo.createChecksum(ChecksumAlgorithm.SHA1, SHA1_VALUE2);
		
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	public void testEquivalent() throws InvalidSPDXAnalysisException {
		ExternalDocumentRef edf = gmo.createExternalDocumentRef(DOCUMENT_ID1, DOCUMENT_URI1, CHECKSUM1);
		Checksum checksumCopy = gmo.createChecksum(CHECKSUM1.getAlgorithm(), CHECKSUM1.getValue());
		ExternalDocumentRef edf2 = gmo.createExternalDocumentRef(DOCUMENT_ID2, DOCUMENT_URI1, checksumCopy);
		assertTrue(edf.equivalent(edf2));
		edf2.setSpdxDocumentNamespace(DOCUMENT_URI2);
		assertFalse(edf.equivalent(edf2));
		edf2.setSpdxDocumentNamespace(DOCUMENT_URI1);
		assertTrue(edf.equivalent(edf2));
		// Checksum
		edf2.setChecksum(CHECKSUM2);
		assertFalse(edf.equivalent(edf2));
		edf2.setChecksum(CHECKSUM1);
		assertTrue(edf.equivalent(edf2));
	}

	/**
	 * Test method for {@link org.spdx.library.model.compat.v2.compat.v2.ExternalDocumentRef#verify()}.
	 * @throws InvalidSPDXAnalysisException 
	 */
	public void testVerify() throws InvalidSPDXAnalysisException {
		ExternalDocumentRef edf = gmo.createExternalDocumentRef(DOCUMENT_ID1, DOCUMENT_URI1, CHECKSUM1);
		edf.setStrict(false);
		assertEquals(0, edf.verify().size());
		edf.setChecksum(null);
		assertEquals(1, edf.verify().size());
		edf.setSpdxDocumentNamespace(null);
		assertEquals(2, edf.verify().size());
	}

	/**
	 * Test method for {@link org.spdx.library.model.compat.v2.compat.v2.ExternalDocumentRef#setChecksum(org.spdx.library.model.compat.v2.compat.v2.Checksum)}.
	 * @throws InvalidSPDXAnalysisException 
	 */
	public void testSetChecksum() throws InvalidSPDXAnalysisException {
		ExternalDocumentRef edf = gmo.createExternalDocumentRef(DOCUMENT_ID1, DOCUMENT_URI1, CHECKSUM1);
		ExternalDocumentRef edf2 = new ExternalDocumentRef(edf.getModelStore(), edf.getDocumentUri(), edf.getId(), edf.getCopyManager(), false);
		assertEquals(CHECKSUM1, edf.getChecksum().get());
		assertEquals(CHECKSUM1, edf2.getChecksum().get());
		edf.setChecksum(CHECKSUM2);
		assertEquals(CHECKSUM2, edf.getChecksum().get());
		assertEquals(CHECKSUM2, edf2.getChecksum().get());
	}

	public void testsetSpdxDocumentNamespace() throws InvalidSPDXAnalysisException {
		ExternalDocumentRef edf = gmo.createExternalDocumentRef(DOCUMENT_ID1, DOCUMENT_URI1, CHECKSUM1);
		assertEquals(DOCUMENT_URI1, edf.getSpdxDocumentNamespace());
		edf.setSpdxDocumentNamespace(DOCUMENT_URI2);
		assertEquals(DOCUMENT_URI2, edf.getSpdxDocumentNamespace());
	}

	/**
	 * Test method for {@link org.spdx.library.model.compat.v2.compat.v2.ExternalDocumentRef#setExternalDocumentId(java.lang.String)}.
	 * @throws InvalidSPDXAnalysisException 
	 */
	public void testSetExternalDocumentId() throws InvalidSPDXAnalysisException {
		ExternalDocumentRef edf = gmo.createExternalDocumentRef(DOCUMENT_ID1, DOCUMENT_URI1, CHECKSUM1);
		ExternalDocumentRef edf2 = new ExternalDocumentRef(edf.getModelStore(), edf.getDocumentUri(), edf.getId(), edf.getCopyManager(), false);
		assertEquals(DOCUMENT_URI1, edf.getSpdxDocumentNamespace());
		assertEquals(DOCUMENT_URI1, edf2.getSpdxDocumentNamespace());

		edf.setSpdxDocumentNamespace(DOCUMENT_URI2);
		assertEquals(DOCUMENT_URI2, edf.getSpdxDocumentNamespace());
		assertEquals(DOCUMENT_URI2, edf2.getSpdxDocumentNamespace());
	}

	/**
	 * Test method for {@link org.spdx.library.model.compat.v2.compat.v2.ExternalDocumentRef#compareTo(org.spdx.library.model.compat.v2.compat.v2.ExternalDocumentRef)}.
	 * @throws InvalidSPDXAnalysisException 
	 */
	public void testCompareTo() throws InvalidSPDXAnalysisException {
		ExternalDocumentRef edf = gmo.createExternalDocumentRef(DOCUMENT_ID1, DOCUMENT_URI1, CHECKSUM1);
		ExternalDocumentRef edf2 = gmo.createExternalDocumentRef(DOCUMENT_ID2, DOCUMENT_URI1, CHECKSUM1);
		edf.setStrict(false);
		edf2.setStrict(false);
		assertEquals(0, edf.compareTo(edf2));
		assertEquals(0, edf2.compareTo(edf));
		edf.setChecksum(CHECKSUM2);
		assertTrue(edf.compareTo(edf2) < 0);
		assertTrue(edf2.compareTo(edf) > 0);
		edf.setSpdxDocumentNamespace(DOCUMENT_URI2);
		assertTrue(edf.compareTo(edf2) > 0);
		assertTrue(edf2.compareTo(edf) < 0);
		edf.setSpdxDocumentNamespace(null);
		assertTrue(edf.compareTo(edf2) < 0);
		assertTrue(edf2.compareTo(edf) > 0);
		edf2.setSpdxDocumentNamespace(null);
		assertTrue(edf.compareTo(edf2) < 0);
		assertTrue(edf2.compareTo(edf) > 0);
		edf.setChecksum(null);
		assertTrue(edf.compareTo(edf2) < 0);
		assertTrue(edf2.compareTo(edf) > 0);
		edf2.setChecksum(null);
		assertEquals(0, edf.compareTo(edf2));
		assertEquals(0, edf2.compareTo(edf));
	}
	
	public void testGetExternalDocRefByDocNamespace() throws InvalidSPDXAnalysisException {
		// need a document to tie the external refs to
		SpdxModelFactoryCompatV2.createModelObjectV2(gmo.getModelStore(), gmo.getDocumentUri(), 
				SpdxConstantsCompatV2.SPDX_DOCUMENT_ID, SpdxConstantsCompatV2.CLASS_SPDX_DOCUMENT, gmo.getCopyManager());
		// test empty
		Optional<ExternalDocumentRef> result = ExternalDocumentRef.getExternalDocRefByDocNamespace(gmo.getModelStore(), gmo.getDocumentUri(), 
				DOCUMENT_URI1, null, Version.TWO_POINT_THREE_VERSION);
		assertFalse(result.isPresent());
		// test create
		result = ExternalDocumentRef.getExternalDocRefByDocNamespace(gmo.getModelStore(), gmo.getDocumentUri(), 
				DOCUMENT_URI1, gmo.getCopyManager(), Version.TWO_POINT_THREE_VERSION);
		assertTrue(result.isPresent());
		assertTrue(result.get().getId().startsWith(SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM));
		// test non matching
		result = ExternalDocumentRef.getExternalDocRefByDocNamespace(gmo.getModelStore(), gmo.getDocumentUri(), 
				DOCUMENT_URI2, null, Version.TWO_POINT_THREE_VERSION);
		assertFalse(result.isPresent());
		// test add second
		result = ExternalDocumentRef.getExternalDocRefByDocNamespace(gmo.getModelStore(), gmo.getDocumentUri(), 
				DOCUMENT_URI2, gmo.getCopyManager(), Version.TWO_POINT_THREE_VERSION);
		assertTrue(result.isPresent());
		assertTrue(result.get().getId().startsWith(SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM));
		// test match
		result = ExternalDocumentRef.getExternalDocRefByDocNamespace(gmo.getModelStore(), gmo.getDocumentUri(), 
				DOCUMENT_URI1, null, Version.TWO_POINT_THREE_VERSION);
		assertTrue(result.isPresent());
		assertTrue(result.get().getId().startsWith(SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM));
		result = ExternalDocumentRef.getExternalDocRefByDocNamespace(gmo.getModelStore(), gmo.getDocumentUri(), 
				DOCUMENT_URI2, gmo.getCopyManager(), Version.TWO_POINT_THREE_VERSION);
		assertTrue(result.isPresent());
		assertTrue(result.get().getId().startsWith(SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM));
	}

}
