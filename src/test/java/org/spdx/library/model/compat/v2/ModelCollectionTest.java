/**
 * SPDX-FileCopyrightText: Copyright (c) 2020 Source Auditor Inc.
 * SPDX-FileType: SOURCE
 * SPDX-License-Identifier: Apache-2.0
 */
package org.spdx.library.model.compat.v2;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.spdx.core.DefaultModelStore;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.ModelCollection;
import org.spdx.core.ModelRegistry;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.v2.GenericSpdxElement;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.SpdxModelInfoV2_X;
import org.spdx.library.model.v3_0_1.SpdxModelInfoV3_0;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.PropertyDescriptor;

import junit.framework.TestCase;

public class ModelCollectionTest extends TestCase {
	
	static final PropertyDescriptor PROPERTY_NAME = new PropertyDescriptor("property", SpdxConstantsCompatV2.SPDX_NAMESPACE);
	static final String[] ELEMENTS = new String[] {"e1", "e2", "e3", "e4"};
	GenericSpdxElement gmo;

	protected void setUp() throws Exception {
		super.setUp();
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV2_X());
		ModelRegistry.getModelRegistry().registerModel(new SpdxModelInfoV3_0());
		DefaultModelStore.initialize(new RdfStore("http://defaultdocument"), "http://defaultdocument", new ModelCopyManager());
		gmo = new GenericSpdxElement();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testSize() throws InvalidSPDXAnalysisException {
		ModelCollection<String> mc = new ModelCollection<String>(gmo.getModelStore(), gmo.getObjectUri(), PROPERTY_NAME, 
				gmo.getCopyManager(), null, gmo.getSpecVersion(), null);
		for (String element:ELEMENTS) {
			mc.add(element);
		}
		assertEquals(ELEMENTS.length, mc.size());
	}

	public void testIsEmpty() throws InvalidSPDXAnalysisException {
		ModelCollection<String> mc = new ModelCollection<String>(gmo.getModelStore(), gmo.getObjectUri(), PROPERTY_NAME, 
				gmo.getCopyManager(), String.class, gmo.getSpecVersion(), null);
		assertTrue(mc.isEmpty());
		for (String element:ELEMENTS) {
			mc.add(element);
		}
		assertFalse(mc.isEmpty());
	}

	public void testContains() throws InvalidSPDXAnalysisException {
		ModelCollection<String> mc = new ModelCollection<String>(gmo.getModelStore(), 
				gmo.getObjectUri(), PROPERTY_NAME, gmo.getCopyManager(), String.class, gmo.getSpecVersion(), null);
		for (String element:ELEMENTS) {
			mc.add(element);
		}
		for (String element:ELEMENTS) {
			assertTrue(mc.contains(element));
		}
		assertFalse(mc.contains("not there"));
	}

	public void testToImmutableList() throws InvalidSPDXAnalysisException {
		ModelCollection<String> mc = new ModelCollection<String>(gmo.getModelStore(), 
				gmo.getObjectUri(), PROPERTY_NAME, gmo.getCopyManager(), String.class, gmo.getSpecVersion(), null);
		for (String element:ELEMENTS) {
			mc.add(element);
		}
		List<Object> result = mc.toImmutableList();
		assertEquals(ELEMENTS.length, result.size());
		for (String element:ELEMENTS) {
			assertTrue(result.contains(element));
		}
	}

	public void testAdd() throws InvalidSPDXAnalysisException {
		ModelCollection<String> mc = new ModelCollection<String>(gmo.getModelStore(), 
				gmo.getObjectUri(), PROPERTY_NAME, gmo.getCopyManager(), String.class, gmo.getSpecVersion(), null);
		assertEquals(0, mc.size());
		mc.add(ELEMENTS[0]);
		assertEquals(1, mc.size());
		assertTrue(mc.contains(ELEMENTS[0]));
		mc.add(ELEMENTS[1]);
		assertEquals(2, mc.size());
		assertTrue(mc.contains(ELEMENTS[0]));
		assertTrue(mc.contains(ELEMENTS[1]));
	}

	public void testRemove() throws InvalidSPDXAnalysisException {
		ModelCollection<String> mc = new ModelCollection<String>(gmo.getModelStore(), 
				gmo.getObjectUri(), PROPERTY_NAME, gmo.getCopyManager(), String.class, gmo.getSpecVersion(), null);
		for (String element:ELEMENTS) {
			mc.add(element);
		}
		assertEquals(ELEMENTS.length, mc.size());
		assertTrue(mc.contains(ELEMENTS[0]));
		mc.remove(ELEMENTS[0]);
		assertEquals(ELEMENTS.length-1, mc.size());
		assertFalse(mc.contains(ELEMENTS[0]));
	}

	public void testContainsAll() throws InvalidSPDXAnalysisException {
		ModelCollection<String> mc = new ModelCollection<String>(gmo.getModelStore(), 
				gmo.getObjectUri(), PROPERTY_NAME, gmo.getCopyManager(), String.class, gmo.getSpecVersion(), null);
		for (String element:ELEMENTS) {
			mc.add(element);
		}
		List<String> compare = new ArrayList<String>(Arrays.asList(ELEMENTS));
		assertTrue(mc.containsAll(compare));
		compare.add("Another");
		assertFalse(mc.containsAll(compare));
	}

	public void testAddAll() throws InvalidSPDXAnalysisException {
		ModelCollection<String> mc = new ModelCollection<String>(gmo.getModelStore(), 
				gmo.getObjectUri(), PROPERTY_NAME, gmo.getCopyManager(), String.class, gmo.getSpecVersion(), null);
		List<String> compare = new ArrayList<String>(Arrays.asList(ELEMENTS));
		mc.addAll(compare);
		
		assertEquals(ELEMENTS.length, mc.size());
		for (String element:ELEMENTS) {
			assertTrue(mc.contains(element));
		}
	}

	public void testRemoveAll() throws InvalidSPDXAnalysisException {
		ModelCollection<String> mc = new ModelCollection<String>(gmo.getModelStore(), 
				gmo.getObjectUri(), PROPERTY_NAME, gmo.getCopyManager(), String.class, gmo.getSpecVersion(), null);
		List<String> list1 = Arrays.asList(ELEMENTS);
		List<String> list2 = new ArrayList<String>(list1);
		String addedElement = "added";
		list2.add(addedElement);
		mc.addAll(list2);
		mc.removeAll(list1);
		assertEquals(1, mc.size());
		assertTrue(mc.contains(addedElement));
	}

	public void testRetainAll() throws InvalidSPDXAnalysisException {
		ModelCollection<String> mc = new ModelCollection<String>(gmo.getModelStore(), 
				gmo.getObjectUri(), PROPERTY_NAME, gmo.getCopyManager(), String.class, gmo.getSpecVersion(), null);
		List<String> list1 = Arrays.asList(ELEMENTS);
		List<String> list2 = new ArrayList<String>(list1);
		String addedElement = "added";
		list2.add(addedElement);
		mc.addAll(list2);
		assertEquals(list2.size(), mc.size());
		mc.retainAll(list1);
		assertEquals(list1.size(), mc.size());
		for (String s:list1) {
			assertTrue(mc.contains(s));
		}
	}

	public void testClear() throws InvalidSPDXAnalysisException {
		ModelCollection<String> mc = new ModelCollection<String>(gmo.getModelStore(), 
				gmo.getObjectUri(), PROPERTY_NAME, gmo.getCopyManager(), String.class, gmo.getSpecVersion(), null);
		for (String element:ELEMENTS) {
			mc.add(element);
		}
		assertEquals(ELEMENTS.length, mc.size());
		mc.clear();
		assertEquals(0, mc.size());
	}

}
