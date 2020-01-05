/**
 * 
 */
package org.spdx.spdxRdfStore;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.library.SpdxConstants;

/**
 * Singleton class to manage the OWL ontology
 * 
 * @author Gary O'Neall
 *
 */
public class SpdxOwlOntology {
	
	static final Logger logger = LoggerFactory.getLogger(SpdxOwlOntology.class);
	
	static SpdxOwlOntology myself = null;
	
	static final String ONTOLOGY_PATH = "/resources/spdx-2-2-revision-3-onotology.owl.xml";
	
	private OntModel model;
	
	Property PROP_MIN_CARDINALITY;
	Property PROP_MIN_QUAL_CARDINALITY;
	Property PROP_MAX_CARDINALITY;
	Property PROP_MAX_QUAL_CARDINALITY;
	Property PROP_CARDINALITY;
	Property PROP_QUAL_CARDINALITY;
	Property ON_PROPERTY_PROPERTY;
	Property RANGE_PROPERTY;

	
	public static synchronized SpdxOwlOntology getSpdxOwlOntology() {
		if (Objects.isNull(myself)) {
			myself = new SpdxOwlOntology();
		}
		return myself;
	}

	/**
	 * 
	 */
	private SpdxOwlOntology() {
		try (InputStream is = SpdxOwlOntology.class.getResourceAsStream(ONTOLOGY_PATH)) {
			if (Objects.isNull(is)) {
				throw new RuntimeException("Can not open SPDX OWL ontology file");
			}
			model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
			model.read(is, "RDF/XML");
			PROP_MIN_CARDINALITY = model.createProperty("http://www.w3.org/2002/07/owl#minCardinality");
			PROP_MIN_QUAL_CARDINALITY = model.createProperty("http://www.w3.org/2002/07/owl#minQualifiedCardinality");
			PROP_MAX_CARDINALITY = model.createProperty("http://www.w3.org/2002/07/owl#maxCardinality");
			PROP_MAX_QUAL_CARDINALITY = model.createProperty("http://www.w3.org/2002/07/owl#maxQualifiedCardinality");
			PROP_CARDINALITY = model.createProperty("http://www.w3.org/2002/07/owl#cardinality");
			ON_PROPERTY_PROPERTY = model.createProperty("http://www.w3.org/2002/07/owl#onProperty");
			RANGE_PROPERTY = model.getProperty("http://www.w3.org/2000/01/rdf-schema#range");
			PROP_QUAL_CARDINALITY = model.getProperty("http://www.w3.org/2002/07/owl#qualifiedCardinality");
		} catch (IOException e) {
			throw new RuntimeException("I/O error in the SPDX OWL ontology file",e);
		}
	}
	
	public OntModel getModel() {
		return this.model;
	}

	/**
	 * @param classUri URI for the class containing the property
	 * @param propertyUri URI for the property with the (possible) cardinality restrictions
	 * @return true if the property has a max cardinality greater than 1 or does not have a max cardinality
	 * @throws SpdxRdfException 
	 */
	public boolean isList(String classUri, String propertyUri) throws SpdxRdfException {
		Objects.requireNonNull(classUri, "Missing class URI");
		Objects.requireNonNull(propertyUri, "Missing property URI");
		OntClass ontClass = model.getOntClass(classUri);
		if (Objects.isNull(ontClass)) {
			if (classUri.endsWith("GenericSpdxElement")) {
				ontClass = model.getOntClass(SpdxConstants.SPDX_NAMESPACE + SpdxConstants.CLASS_SPDX_ELEMENT);
			} else {
				logger.error(classUri + " is not an SPDX class");
				throw new SpdxRdfException(classUri + " is not an SPDX class");
			}
		}
		OntProperty property = model.getOntProperty(propertyUri);
		if (Objects.isNull(property)) {
			logger.error(propertyUri + " is not an SPDX property");
			throw new SpdxRdfException(propertyUri + " is not an SPDX property");
		}
		List<Statement> propertyRestrictions = new ArrayList<Statement>();
		addPropertyRestrictions(ontClass, property, propertyRestrictions);
		if (propertyRestrictions.isEmpty()) {
			throw new SpdxRdfException(propertyUri + " was not found related to class "+classUri);
		}
		int minCardinality = -1;
		int maxCardinality = -1;
		int exactCardinality = -1;
		for (Statement stmt:propertyRestrictions) {
			if (stmt.getPredicate().equals(PROP_MIN_CARDINALITY) ||
					stmt.getPredicate().equals(PROP_MIN_QUAL_CARDINALITY)) {
				if (stmt.getObject().asLiteral().getInt() > minCardinality) {
					minCardinality = stmt.getObject().asLiteral().getInt();
				}
			} else if (stmt.getPredicate().equals(PROP_MAX_CARDINALITY) ||
					stmt.getPredicate().equals(PROP_MAX_QUAL_CARDINALITY)) {
				if (stmt.getObject().asLiteral().getInt() > maxCardinality) {
					maxCardinality = stmt.getObject().asLiteral().getInt();
				}
			} else if (stmt.getPredicate().equals(PROP_CARDINALITY) ||
					stmt.getPredicate().equals(PROP_QUAL_CARDINALITY)) {
				exactCardinality = stmt.getObject().asLiteral().getInt();
			}
		}
		return (exactCardinality == -1 && (maxCardinality == -1) || (maxCardinality > 1)) || exactCardinality > 1;
	}

/**
 * Adds restriction statement to the propertyRestrictions list
 * @param ontClass class related to the property - all superclasses will be searched
 * @param property property on which the restriction occurs
 * @param propertyRestrictions list of all statements containing a restriction on the property within the class and all superclasses (including transitivie superclasses)
 */
private void addPropertyRestrictions(OntClass ontClass, OntProperty property, List<Statement> propertyRestrictions) {	
		if (ontClass.isRestriction()) {
			if (model.listStatements(new SimpleSelector(ontClass, ON_PROPERTY_PROPERTY, property)).hasNext()) {
				// matches the property
				ontClass.listProperties().forEachRemaining((Statement stmt) -> {
					propertyRestrictions.add(stmt);
				});
			}
		} else if (ontClass.isUnionClass()) {
			ontClass.asUnionClass().listOperands().forEachRemaining((OntClass operand) -> {
				 addPropertyRestrictions(operand, property, propertyRestrictions);
			 });
		} else {
			 ontClass.listSuperClasses().forEachRemaining((OntClass superClass) -> {
				 addPropertyRestrictions(superClass, property, propertyRestrictions);
			 });
		}
	}

}
