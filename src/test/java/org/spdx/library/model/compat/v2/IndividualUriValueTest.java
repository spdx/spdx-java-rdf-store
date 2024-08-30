package org.spdx.library.model.compat.v2;


import org.spdx.core.DefaultModelStore;
import org.spdx.core.IModelCopyManager;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.ModelRegistry;
import org.spdx.core.SimpleUriValue;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.v2.ExternalDocumentRef;
import org.spdx.library.model.v2.ExternalSpdxElement;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.SpdxModelInfoV2_X;
import org.spdx.library.model.v2.SpdxNoAssertionElement;
import org.spdx.library.model.v2.SpdxNoneElement;
import org.spdx.library.model.v2.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.v2.license.ExternalExtractedLicenseInfo;
import org.spdx.library.model.v2.license.SpdxNoAssertionLicense;
import org.spdx.library.model.v2.license.SpdxNoneLicense;
import org.spdx.library.model.v3_0_1.SpdxModelInfoV3_0;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.IModelStore;

import junit.framework.TestCase;

public class IndividualUriValueTest extends TestCase {
	
	static final String SHA1_CHECKSUM = "399e50ed82067fc273ed02495fbdb149a667ebe9";
	IModelStore modelStore;
	IModelCopyManager copyManager;
	
	protected void setUp() throws Exception {
		super.setUp();
		modelStore = new RdfStore("http://defaultdocument");
		copyManager = new ModelCopyManager();
		DefaultModelStore.initialize(modelStore, "http://defaultdocument", copyManager);
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV2_X());
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV3_0());
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	// Test if a simple URI value is equal to the ExternalExtracedLicenseInfo with the same URI value
	@SuppressWarnings("unlikely-arg-type")
	public void testEqualUriValueExternalExtractedLicenseInfo() throws InvalidSPDXAnalysisException {
		String id = SpdxConstantsCompatV2.NON_STD_LICENSE_ID_PRENUM+"ID";
		String externalDocId = SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM + "externalDoc";
		String externalDocNamespace = "http://example.external.namespace";
		ExternalDocumentRef edr = new ExternalDocumentRef(modelStore, "http://defaultdocument", externalDocId, copyManager, true);
		edr.setChecksum(edr.createChecksum(ChecksumAlgorithm.SHA1, SHA1_CHECKSUM));
		edr.setSpdxDocumentNamespace(externalDocNamespace);
		ExternalExtractedLicenseInfo eel = new ExternalExtractedLicenseInfo(modelStore, externalDocNamespace, 
				id, copyManager);
		SimpleUriValue suv = new SimpleUriValue(externalDocNamespace + "#" + id);
		assertTrue(eel.equals(suv));
		assertTrue(suv.equals(eel));
	}

	// Test if a simple URI value is equal to the ExternalSpdxElement with the same URI value
	@SuppressWarnings("unlikely-arg-type")
	public void testEqualUriValueExternalSpdxElement() throws InvalidSPDXAnalysisException {
		String id = SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM+"ID";
		String externalDocId = SpdxConstantsCompatV2.EXTERNAL_DOC_REF_PRENUM + "externalDoc";
		String externalDocNamespace = "http://example.external.namespace";
		ExternalDocumentRef edr = new ExternalDocumentRef(modelStore, "http://defaultdocument", externalDocId, copyManager, true);
		edr.setChecksum(edr.createChecksum(ChecksumAlgorithm.SHA1, SHA1_CHECKSUM));
		edr.setSpdxDocumentNamespace(externalDocNamespace);
		ExternalSpdxElement ese = new ExternalSpdxElement(modelStore, externalDocNamespace, id, copyManager);
		SimpleUriValue suv = new SimpleUriValue(externalDocNamespace + "#" + id);
		assertTrue(ese.equals(suv));
		assertTrue(suv.equals(ese));
	}
	
	// Test if a simple URI value is equal to the NoAssertionLicense with the same URI value
	@SuppressWarnings("unlikely-arg-type")
	public void testEqualUriValueNoAssertionLicense() throws InvalidSPDXAnalysisException {
		SpdxNoAssertionLicense nal = new SpdxNoAssertionLicense();
		SimpleUriValue suv = new SimpleUriValue(SpdxConstantsCompatV2.URI_VALUE_NOASSERTION);
		assertTrue(nal.equals(suv));
		assertTrue(suv.equals(nal));
	}
	
	// Test if a simple URI value is equal to the NoneLicense with the same URI value
	@SuppressWarnings("unlikely-arg-type")
	public void testEqualUriValueNoneLicense() throws InvalidSPDXAnalysisException {
		SpdxNoneLicense nl = new SpdxNoneLicense();
		SimpleUriValue suv = new SimpleUriValue(SpdxConstantsCompatV2.URI_VALUE_NONE);
		assertTrue(nl.equals(suv));
		assertTrue(suv.equals(nl));
	}
	
	// Test if a simple URI value is equal to the SpdxNoneElement with the same URI value
	@SuppressWarnings("unlikely-arg-type")
	public void testEqualUriValueNone() throws InvalidSPDXAnalysisException {
		SpdxNoneElement ne = new SpdxNoneElement();
		SimpleUriValue suv = new SimpleUriValue(SpdxConstantsCompatV2.URI_VALUE_NONE);
		assertTrue(ne.equals(suv));
		assertTrue(suv.equals(ne));
	}
	
	// Test if a simple URI value is equal to the SpdxNoAssertionElement with the same URI value
	@SuppressWarnings("unlikely-arg-type")
	public void testEqualUriValueNoAssertion() throws InvalidSPDXAnalysisException {
		SpdxNoAssertionElement na = new SpdxNoAssertionElement();
		SimpleUriValue suv = new SimpleUriValue(SpdxConstantsCompatV2.URI_VALUE_NOASSERTION);
		assertTrue(na.equals(suv));
		assertTrue(suv.equals(na));
	}
}
