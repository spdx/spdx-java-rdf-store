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

import java.util.Arrays;
import java.util.List;

import org.spdx.core.DefaultModelStore;
import org.spdx.core.IModelCopyManager;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.ModelRegistry;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.v2.SpdxModelInfoV2_X;
import org.spdx.library.model.v2.SpdxPackageVerificationCode;
import org.spdx.library.model.v3_0_1.SpdxModelInfoV3_0;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.IModelStore;
import org.spdx.storage.IModelStore.IdType;

import junit.framework.TestCase;

/**
 * @author Gary O'Neall
 */
public class SpdxPackageVerificationCodeTest extends TestCase {
	
	static final String[] VALUES = new String[] {"0123456789abcdef0123456789abcdef01234567",
			"c1ef456789abcdab0123456789abcdef01234567", "invalidvalue"};
	
	static final String[] VALUES2 = new String[] {"ab23456789abcdef0123456789abcdef01234567",
		"00ef456789abcdab0123456789abcdef01234567", "2invalidvalue2"};

	static final String[] [] SKIPPED_FILES = new String[][] {new String[] {"skipped1", "skipped2"},
			new String[0], new String[] {"oneSkippedFile"}};
	
	static final String[] [] SKIPPED_FILES2 = new String[][] {new String[] {},
		new String[] {"single/file"}, new String[] {"a/b/c", "d/e/f", "g/hi"}};
	
	SpdxPackageVerificationCode[] VERIFICATION_CODES;

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV2_X());
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV3_0());
		DefaultModelStore.initialize(new RdfStore("http://defaultdocument"), "http://defaultdocument", new ModelCopyManager());
		IModelStore store = DefaultModelStore.getDefaultModelStore();
		String docUri = DefaultModelStore.getDefaultDocumentUri();
		IModelCopyManager copyManager = DefaultModelStore.getDefaultCopyManager();
		VERIFICATION_CODES = new SpdxPackageVerificationCode[] {
				new SpdxPackageVerificationCode(store, docUri,store.getNextId(IdType.Anonymous), copyManager, true),
				new SpdxPackageVerificationCode(store, docUri,store.getNextId(IdType.Anonymous), copyManager, true),
				new SpdxPackageVerificationCode(store, docUri,store.getNextId(IdType.Anonymous), copyManager, true)
			};
		for (int i = 0; i < VERIFICATION_CODES.length; i++) {
			VERIFICATION_CODES[i].setValue(VALUES[i]);
			VERIFICATION_CODES[i].getExcludedFileNames().addAll(Arrays.asList(SKIPPED_FILES[i]));
		}
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Test method for {@link org.spdx.library.model.compat.v2.compat.v2.SpdxPackageVerificationCode#verify()}.
	 */
	public void testVerify() {
		List<String> verify = VERIFICATION_CODES[0].verify();
		assertEquals(0, verify.size());
		verify = VERIFICATION_CODES[1].verify();
		assertEquals(0, verify.size());
		verify = VERIFICATION_CODES[2].verify();
		assertEquals(1, verify.size());
	}

	/**
	 * Test method for {@link org.spdx.library.model.compat.v2.compat.v2.SpdxPackageVerificationCode#setValue(java.lang.String)}.
	 */
	public void testSetValue() throws InvalidSPDXAnalysisException {
		for (int i  = 0; i < VERIFICATION_CODES.length; i++) {
			SpdxPackageVerificationCode comp = VERIFICATION_CODES[i];
			assertEquals(VALUES[i], comp.getValue());
			for (String excluded:SKIPPED_FILES[i]) {
				assertTrue(comp.getExcludedFileNames().contains(excluded));
			}
			comp.setValue(VALUES2[i]);
			assertEquals(VALUES2[i], comp.getValue());
		}
	}

	/**
	 * Test method for {@link org.spdx.library.model.compat.v2.compat.v2.SpdxPackageVerificationCode#getExcludedFileNames()}.
	 * @throws InvalidSPDXAnalysisException 
	 */
	public void testGetExcludedFileNames() throws InvalidSPDXAnalysisException {
		for (int i  = 0; i < VERIFICATION_CODES.length; i++) {
			SpdxPackageVerificationCode comp = VERIFICATION_CODES[i];
			assertEquals(SKIPPED_FILES[i].length, comp.getExcludedFileNames().size());
			for (String excluded:SKIPPED_FILES[i]) {
				assertTrue(comp.getExcludedFileNames().contains(excluded));
			}
			comp.getExcludedFileNames().clear();
			comp.getExcludedFileNames().addAll(Arrays.asList(SKIPPED_FILES2[i]));
			assertEquals(SKIPPED_FILES2[i].length, comp.getExcludedFileNames().size());
			for (String excluded:SKIPPED_FILES2[i]) {
				assertTrue(comp.getExcludedFileNames().contains(excluded));
			}
			assertEquals(VALUES[i], comp.getValue());
		}
	}

}
