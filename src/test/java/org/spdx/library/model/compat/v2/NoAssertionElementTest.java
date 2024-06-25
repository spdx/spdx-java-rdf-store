package org.spdx.library.model.compat.v2;
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

import org.spdx.core.DefaultModelStore;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.ModelRegistry;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.v2.Relationship;
import org.spdx.library.model.v2.SpdxElement;
import org.spdx.library.model.v2.SpdxModelInfoV2_X;
import org.spdx.library.model.v2.SpdxNoAssertionElement;
import org.spdx.library.model.v2.enumerations.RelationshipType;
import org.spdx.library.model.v3.SpdxModelInfoV3_0;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.IModelStore;

import junit.framework.TestCase;

/**
 * @author gary
 *
 */
public class NoAssertionElementTest extends TestCase {

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV2_X());
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV3_0());
		DefaultModelStore.initialize(new RdfStore("http://defaultdocument"), "http://defaultdocument", new ModelCopyManager());
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	public void testHashCodeEquals() throws InvalidSPDXAnalysisException {
		IModelStore store1 = new RdfStore("https://doc.uri1");
		SpdxNoAssertionElement e1 = new SpdxNoAssertionElement(store1, "https://doc.uri1");
		IModelStore store2 = new RdfStore("https://doc.uri2");
		SpdxNoAssertionElement e2 = new SpdxNoAssertionElement(store2, "https://doc.uri2");
		assertEquals(e1.hashCode(), e2.hashCode());
		assertEquals(e1, e2);
		assertTrue(e1.equals(e2));
		assertTrue(e2.equals(e1));
	}
	
	public void testStoreRetrieveNoneElement() throws InvalidSPDXAnalysisException {
		Relationship rel = new Relationship();
		rel.setRelationshipType(RelationshipType.DYNAMIC_LINK);
		rel.setRelatedSpdxElement(new SpdxNoAssertionElement());
		SpdxElement expected = new SpdxNoAssertionElement();
		assertEquals(expected, rel.getRelatedSpdxElement().get());
	}

}
