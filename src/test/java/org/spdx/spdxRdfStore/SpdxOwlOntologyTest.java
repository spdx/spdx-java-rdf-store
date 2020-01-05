/**
 * 
 */
package org.spdx.spdxRdfStore;

import java.util.Objects;

import org.apache.jena.ontology.OntModel;
import org.spdx.library.SpdxConstants;

import junit.framework.TestCase;

/**
 * @author gary
 *
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
		assertTrue(SpdxOwlOntology.getSpdxOwlOntology().isList(SpdxConstants.SPDX_NAMESPACE + SpdxConstants.CLASS_SPDX_FILE,
				SpdxConstants.SPDX_NAMESPACE + SpdxConstants.PROP_FILE_TYPE));
		assertFalse(SpdxOwlOntology.getSpdxOwlOntology().isList(SpdxConstants.SPDX_NAMESPACE + SpdxConstants.CLASS_SPDX_CREATION_INFO,
				SpdxConstants.SPDX_NAMESPACE + SpdxConstants.PROP_CREATION_CREATED));
		assertFalse(SpdxOwlOntology.getSpdxOwlOntology().isList(SpdxConstants.SPDX_NAMESPACE + SpdxConstants.CLASS_SPDX_PACKAGE,
				SpdxConstants.SPDX_NAMESPACE + SpdxConstants.PROP_LICENSE_DECLARED));
	}
	
	public void testClassesInOntology() {
		for (String className:SpdxConstants.ALL_SPDX_CLASSES) {
			if (!SpdxConstants.CLASS_NONE_LICENSE.equals(className) && 
					!SpdxConstants.CLASS_NOASSERTION_LICENSE.equals(className) && 
					!SpdxConstants.CLASS_EXTERNAL_SPDX_ELEMENT.equals(className)) {
				assertNotNull(className+" not present in ontology.",SpdxOwlOntology.getSpdxOwlOntology().getModel().getOntClass(
						SpdxResourceFactory.classNameToUri(className)));
			}
		}
	}
}
