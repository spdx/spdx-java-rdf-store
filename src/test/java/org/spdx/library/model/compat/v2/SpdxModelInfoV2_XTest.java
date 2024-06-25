/**
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Source Auditor Inc.
 */
package org.spdx.library.model.compat.v2;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.spdx.core.CoreModelObject;
import org.spdx.core.DefaultModelStore;
import org.spdx.core.IModelCopyManager;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.ModelRegistry;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.v2.ExternalSpdxElement;
import org.spdx.library.model.v2.ReferenceType;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.SpdxElement;
import org.spdx.library.model.v2.SpdxModelInfoV2_X;
import org.spdx.library.model.v2.SpdxNoAssertion;
import org.spdx.library.model.v2.SpdxNoAssertionElement;
import org.spdx.library.model.v2.SpdxNone;
import org.spdx.library.model.v2.SpdxNoneElement;
import org.spdx.library.model.v2.SpdxPackage;
import org.spdx.library.model.v2.Version;
import org.spdx.library.model.v2.license.AnyLicenseInfo;
import org.spdx.library.model.v2.license.ExternalExtractedLicenseInfo;
import org.spdx.library.model.v2.license.SpdxNoAssertionLicense;
import org.spdx.library.model.v2.license.SpdxNoneLicense;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.IModelStore;

/**
 * @author gary
 *
 */
public class SpdxModelInfoV2_XTest {
	
	SpdxModelInfoV2_X modelInfo;
	IModelStore modelStore;
	IModelCopyManager copyManager;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		modelStore = new RdfStore("http://defaultdocument");
		copyManager = new ModelCopyManager();
		modelInfo = new SpdxModelInfoV2_X();
		ModelRegistry.getModelRegistry().registerModel(modelInfo);
		DefaultModelStore.initialize(modelStore, "http://defaultdocument", copyManager);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	/**
	 * Test method for {@link org.spdx.library.model.v2.SpdxModelInfoV2_X#createExternalElement(org.spdx.storage.IModelStore, java.lang.String, org.spdx.core.IModelCopyManager, java.lang.String)}.
	 * @throws InvalidSPDXAnalysisException 
	 */
	@Test
	public void testCreateExternalElement() throws InvalidSPDXAnalysisException {
		// external license
		String licenseUri = "http://prefix#" + SpdxConstantsCompatV2.NON_STD_LICENSE_ID_PRENUM + "test";
		CoreModelObject external = modelInfo.createExternalElement(modelStore, licenseUri, copyManager, Version.TWO_POINT_THREE_VERSION);
		assertTrue(external instanceof ExternalExtractedLicenseInfo);
		assertEquals(licenseUri, external.getObjectUri());
		// external license
		String elementUri = "http://prefix#" + SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM + "test";
		external = modelInfo.createExternalElement(modelStore, elementUri, copyManager, Version.TWO_POINT_THREE_VERSION);
		assertTrue(external instanceof ExternalSpdxElement);
		assertEquals(elementUri, external.getObjectUri());
	}

	/**
	 * Test method for {@link org.spdx.library.model.v2.SpdxModelInfoV2_X#uriToIndividual(java.lang.String)}.
	 */
	@Test
	public void testUriToIndividual() {
		// None License
		assertTrue(modelInfo.uriToIndividual(SpdxConstantsCompatV2.URI_VALUE_NONE, null) instanceof SpdxNone);
		assertTrue(modelInfo.uriToIndividual(SpdxConstantsCompatV2.URI_VALUE_NONE, AnyLicenseInfo.class) instanceof SpdxNoneLicense);
		assertTrue(modelInfo.uriToIndividual(SpdxConstantsCompatV2.URI_VALUE_NONE, SpdxElement.class) instanceof SpdxNoneElement);
		// NoAssertionLicense
		assertTrue(modelInfo.uriToIndividual(SpdxConstantsCompatV2.URI_VALUE_NOASSERTION, null) instanceof SpdxNoAssertion);
		assertTrue(modelInfo.uriToIndividual(SpdxConstantsCompatV2.URI_VALUE_NOASSERTION, AnyLicenseInfo.class) instanceof SpdxNoAssertionLicense);
		assertTrue(modelInfo.uriToIndividual(SpdxConstantsCompatV2.URI_VALUE_NOASSERTION, SpdxElement.class) instanceof SpdxNoAssertionElement);
		// Reference Type
		String refUri = "http://spdx.org/rdf/references/something";
		Object ref = modelInfo.uriToIndividual(refUri, null);
		assertTrue(ref instanceof ReferenceType);
		assertEquals(refUri, ((ReferenceType)ref).getIndividualURI());
	}

	/**
	 * Test method for {@link org.spdx.library.model.v2.SpdxModelInfoV2_X#createModelObject(org.spdx.storage.IModelStore, java.lang.String, java.lang.String, org.spdx.core.IModelCopyManager, java.lang.String, boolean)}.
	 * @throws InvalidSPDXAnalysisException 
	 */
	@Test
	public void testCreateModelObject() throws InvalidSPDXAnalysisException {
		String objectUri = "http://defaultdocument#" + SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM + "test";
		CoreModelObject result = modelInfo.createModelObject(modelStore, objectUri, SpdxConstantsCompatV2.CLASS_SPDX_PACKAGE, copyManager, Version.TWO_POINT_THREE_VERSION, true);
		assertTrue(result instanceof SpdxPackage);
		assertEquals(objectUri, result.getObjectUri());
	}

}
