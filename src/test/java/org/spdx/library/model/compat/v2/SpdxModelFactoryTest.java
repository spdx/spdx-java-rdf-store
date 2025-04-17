/**
 * SPDX-FileCopyrightText: Copyright (c) 2020 Source Auditor Inc.
 * SPDX-FileType: SOURCE
 * SPDX-License-Identifier: Apache-2.0
 */
package org.spdx.library.model.compat.v2;


import java.util.Optional;

import org.spdx.core.DefaultModelStore;
import org.spdx.core.IModelCopyManager;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.ModelRegistry;
import org.spdx.core.SpdxIdNotFoundException;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.v2.Checksum;
import org.spdx.library.model.v2.ModelObjectV2;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.SpdxModelFactoryCompatV2;
import org.spdx.library.model.v2.SpdxModelInfoV2_X;
import org.spdx.library.model.v3_0_1.SpdxModelInfoV3_0;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.IModelStore;

import junit.framework.TestCase;

public class SpdxModelFactoryTest extends TestCase {
	
	static final String DOCUMENT_URI = "http://www.spdx.org/documents";
	static final String ID1 = SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM + "1";
	static final String ID2 = SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM + "2";
	
	IModelStore modelStore;
	IModelCopyManager copyManager;
	

	protected void setUp() throws Exception {
		super.setUp();
		modelStore = new RdfStore(DOCUMENT_URI);
		copyManager = new ModelCopyManager();
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV2_X());
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV3_0());
		DefaultModelStore.initialize(modelStore, "http://defaultdocument", copyManager);
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	public void testCreateSpdxDocumentV2() throws InvalidSPDXAnalysisException {
		SpdxDocument result = SpdxModelFactoryCompatV2.createSpdxDocumentV2(modelStore, DOCUMENT_URI, copyManager);
		assertEquals(SpdxConstantsCompatV2.SPDX_DOCUMENT_ID, result.getId());
	}

	public void testCreateModelObjectV2() throws InvalidSPDXAnalysisException {
		ModelObjectV2 result = SpdxModelFactoryCompatV2.createModelObjectV2(modelStore, DOCUMENT_URI, ID1, 
				SpdxConstantsCompatV2.CLASS_SPDX_CHECKSUM, copyManager);
		assertTrue(result instanceof Checksum);
		assertEquals(ID1, result.getId());
	}

	public void testGetModelObjectIModelStoreStringStringStringModelCopyManagerBooleanV2() throws InvalidSPDXAnalysisException {
		ModelObjectV2 result = SpdxModelFactoryCompatV2.getModelObjectV2(modelStore, DOCUMENT_URI, ID1, 
				SpdxConstantsCompatV2.CLASS_SPDX_CHECKSUM, copyManager, true);
		assertTrue(result instanceof Checksum);
		assertEquals(ID1, result.getId());
		ModelObjectV2 result2 = SpdxModelFactoryCompatV2.getModelObjectV2(modelStore, DOCUMENT_URI, ID1, 
				SpdxConstantsCompatV2.CLASS_SPDX_CHECKSUM, copyManager, false);
		assertTrue(result2 instanceof Checksum);
		assertEquals(ID1, result2.getId());
		try {
			result = SpdxModelFactoryCompatV2.getModelObjectV2(modelStore, DOCUMENT_URI, ID2, 
					SpdxConstantsCompatV2.CLASS_SPDX_CHECKSUM, copyManager, false);
			fail("Expected objectUri not found exception");
		} catch(SpdxIdNotFoundException ex) {
			// expected
		}
	}

	public void testGetModelObjectIModelStoreStringStringModelCopyManagerV2() throws InvalidSPDXAnalysisException {
		ModelObjectV2 result = SpdxModelFactoryCompatV2.getModelObjectV2(modelStore, DOCUMENT_URI, ID1, 
				SpdxConstantsCompatV2.CLASS_SPDX_CHECKSUM, copyManager, true);
		assertTrue(result instanceof Checksum);
		assertEquals(ID1, result.getId());
		Optional<ModelObjectV2> result2 = SpdxModelFactoryCompatV2.getModelObjectV2(modelStore, DOCUMENT_URI, ID1, copyManager);
		assertTrue(result2.isPresent());
		assertTrue(result2.get() instanceof Checksum);
		assertEquals(ID1, result2.get().getId());
		result2 = SpdxModelFactoryCompatV2.getModelObjectV2(modelStore, DOCUMENT_URI, ID2, copyManager);
		assertFalse(result2.isPresent());
	}

}
