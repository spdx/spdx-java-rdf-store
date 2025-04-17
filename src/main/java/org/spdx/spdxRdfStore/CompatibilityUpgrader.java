/**
 * SPDX-FileCopyrightText: Copyright (c) 2020 Source Auditor Inc.
 * SPDX-FileType: SOURCE
 * SPDX-License-Identifier: Apache-2.0
 * <p>
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 * <p>
 *       http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.spdx.spdxRdfStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.SpdxVerificationHelper;
import org.spdx.library.model.v2.enumerations.AnnotationType;
import org.spdx.library.model.v2.enumerations.RelationshipType;

/**
 * Updates the RDF model for compatibility with the current version of the spec
 * @author Gary O'Neall
 */
public class CompatibilityUpgrader {
	
	static final Map<String, Map<String, String>> TYPE_PROPERTY_MAP;
	static final Logger logger = LoggerFactory.getLogger(CompatibilityUpgrader.class);
	
	static {
        TYPE_PROPERTY_MAP = Map.of(SpdxConstantsCompatV2.CLASS_SPDX_DOCUMENT, Map.of(
				// TODO: In 3.0, uncomment those below to change the spec versions
				SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_SPDX_VERSION.getName(),
				SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_SPDX_SPEC_VERSION.getName())
		);
	}

	/**
	 * Upgrade the properties in the model to the current version of the spec
	 * @param model RDF model
	 * @param documentNamespace Namespace or URI for the SPDX document
	 */
	public static void upgrade(Model model, String documentNamespace) throws InvalidSPDXAnalysisException {
		model.enterCriticalSection(false);
		try {
			// update type property names
			for (Entry<String, Map<String, String>> entry:TYPE_PROPERTY_MAP.entrySet()) {
				String query = "SELECT ?s  WHERE { ?s  <" + 
						RdfSpdxModelManager.RDF_TYPE + "> <" +
						SpdxConstantsCompatV2.SPDX_NAMESPACE + entry.getKey() + "> }";
				try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
				    ResultSet result = qe.execSelect();
	                while (result.hasNext()) {
	                    Resource subject = result.next().get("s").asResource();
	                    for (Entry<String, String> propEntry:entry.getValue().entrySet()) {
	                        Property incompatibleProperty = model.createProperty(propEntry.getKey());
	                        if (subject.hasProperty(incompatibleProperty)) {
	                            Property compatibleProperty = model.createProperty(propEntry.getValue());
	                            NodeIterator iter = model.listObjectsOfProperty(subject, incompatibleProperty);
	                            while (iter.hasNext()) {
	                                RDFNode object = iter.next();
	                                subject.addProperty(compatibleProperty, object);
	                            }
								// We'll leave the old property for compatibility
								//  subject.removeAll(incompatibleProperty);
	                        }
	                    }
	                }
				}
			}
			upgradeArtifactOf(model, documentNamespace);
			upgradeReviewers(model, documentNamespace);
			upgradeExternalDocumentRefs(model, documentNamespace);
			upgradeHasFiles(model);
		} finally {
			model.leaveCriticalSection();
		}
	}

	/**
	 * Changes all hasFile properties to CONTAINS relationships
	 * @param model RDF model
	 */
	private static void upgradeHasFiles(Model model) {
		List<Statement> statementsToRemove = new ArrayList<>();
		Property hasFileProperty = model.createProperty("http://spdx.org/rdf/terms#hasFile");
		Property relationshipProperty = model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_RELATIONSHIP.getName());
		String query = "SELECT ?s ?o  WHERE { ?s  <http://spdx.org/rdf/terms#hasFile> ?o }";
		try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
		    ResultSet result = qe.execSelect();
	        while (result.hasNext()) {
	            QuerySolution qs = result.next();
	            Resource pkg = qs.get("s").asResource();
	            Resource file = qs.get("o").asResource();
	            statementsToRemove.add(model.createStatement(pkg, hasFileProperty, file));
	            // check for existing contains relationships - avoids duplication
	            List<Statement> foundContainsRelationships = new ArrayList<>();
	            pkg.listProperties(relationshipProperty).forEach((stmt) -> {
	            	Resource existingRelationship = stmt.getObject().asResource();
	            	Resource relatedElement = existingRelationship.getPropertyResourceValue(model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_RELATED_SPDX_ELEMENT.getName()));
	            	Resource relationshipType = existingRelationship.getPropertyResourceValue(model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_RELATIONSHIP_TYPE.getName()));
	            	if (relatedElement.isURIResource() && file.isURIResource() && relationshipType.isURIResource() &&
	            			relatedElement.getURI().equals(file.getURI()) &&
	            			relationshipType.getURI().equals(RelationshipType.CONTAINS.getIndividualURI())) {
	            		foundContainsRelationships.add(stmt);
            		}
	            });
	            if (foundContainsRelationships.isEmpty()) {
	            	Resource relationship = createRelationship(model, file, RelationshipType.CONTAINS);
	            	pkg.addProperty(relationshipProperty, relationship);
	            }
	        }
	        model.remove(statementsToRemove);
		}
	}

	/**
	 * Make sure all external document Ref's have a URI with proper ID rather than using the externalDocumentId property
	 * @param model RDF model
	 * @param documentNamespace Namespace or URI for the SPDX document
	 * @throws InvalidSPDXAnalysisException on SPDX parsing errors
	 */
	private static void upgradeExternalDocumentRefs(Model model, String documentNamespace) throws InvalidSPDXAnalysisException {
		String query = "SELECT ?s ?o  WHERE { ?s  <http://spdx.org/rdf/terms#externalDocumentId> ?o }";
		try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
		    ResultSet result = qe.execSelect();
	        List<Statement> statementsToRemove = new ArrayList<>();
	        List<Statement> statementsToAdd = new ArrayList<>();
	        while (result.hasNext()) {
	            QuerySolution qs = result.next();
	            try {
	                Resource currentExternalRef = qs.get("s").asResource();
	                String id = qs.get("o").asLiteral().getString();
	                if (!SpdxVerificationHelper.isValidExternalDocRef(id)) {
	                    throw new InvalidSPDXAnalysisException("Invalid external document ref "+id);
	                }
	                String uri = documentNamespace + "#" + id;
	                if (!currentExternalRef.isURIResource() || uri.equals(currentExternalRef.getURI())) {
	                    // need to replace this external ref with one with a valid document ID
	                    Resource newExternalRef = model.createResource(uri);
	                    // get all the properties and copy them over
	                    StmtIterator currentPropIter = currentExternalRef.listProperties();
	                    while (currentPropIter.hasNext()) {
	                        Statement stmt = currentPropIter.next();
	                        statementsToAdd.add(model.createStatement(newExternalRef, stmt.getPredicate(), stmt.getObject()));
	                        statementsToRemove.add(stmt);
	                    }
	                    // change all references from the old value to this one
	                    StmtIterator currentExternalRefRefs = model.listStatements(null, null, currentExternalRef);
	                    while (currentExternalRefRefs.hasNext()) {
	                        Statement stmt = currentExternalRefRefs.next();
	                        statementsToAdd.add(model.createStatement(stmt.getSubject(), stmt.getPredicate(), newExternalRef));
	                        statementsToRemove.add(stmt);
	                    }
	                }
	            } catch(Exception ex) {
	                throw new InvalidSPDXAnalysisException("Error upgrading external document refs",ex);
	            }
	        }
	        model.remove(statementsToRemove);
	        model.add(statementsToAdd);
		}
	}

	/**
	 * Upgrade the reviewers field to Annotations with a type reviewer
	 * @param model RDF model
	 * @param documentNamespace Namespace or URI for the SPDX document
	 * @throws InvalidSPDXAnalysisException on SPDX parsing errors
	 */
	private static void upgradeReviewers(Model model, String documentNamespace) throws InvalidSPDXAnalysisException {
		Resource document = model.createResource(documentNamespace  + "#" + SpdxConstantsCompatV2.SPDX_DOCUMENT_ID);
		Property annotationProperty = model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_ANNOTATION.getName());
		Resource reviewerType = model.createResource(AnnotationType.REVIEW.getIndividualURI());
		Property typeProperty = model.createProperty(RdfSpdxModelManager.RDF_TYPE);
		Resource annotationClass = model.createResource(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_ANNOTATION);
		Property commentProperty = model.createProperty(SpdxConstantsCompatV2.RDFS_NAMESPACE + SpdxConstantsCompatV2.RDFS_PROP_COMMENT.getName());
		Property annotatorProperty = model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_ANNOTATOR.getName());
		Property annotationDateProperty = model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_ANNOTATION_DATE.getName());
		Property annotationTypeProperty = model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_ANNOTATION_TYPE.getName());
		List<Statement> statementsToRemove = new ArrayList<>();
		Set<Integer> addedAnnotations = new HashSet<>(); // to prevent duplicates
		String query = "SELECT ?s ?o  WHERE { ?s  <http://spdx.org/rdf/terms#reviewer> ?o }";
		try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
		    ResultSet result = qe.execSelect();
	        while (result.hasNext()) {
	            QuerySolution qs = result.next();
	            Resource review = qs.get("s").asResource();
	            String reviewer = qs.get("o").asLiteral().toString();
	            Resource annotation = model.createResource();
	            annotation.addProperty(typeProperty, annotationClass);
	            String reviewDate;
	            try {
	                reviewDate = review.getRequiredProperty(model.createProperty(
	                        SpdxConstantsCompatV2.SPDX_NAMESPACE + "reviewDate"))
	                        .getString();
	                if (Objects.isNull(reviewDate)) {
	                    throw new InvalidSPDXAnalysisException("Missing or invalid review date for review");
	                }
	            } catch (Exception ex) {
	                throw new InvalidSPDXAnalysisException("Missing or invalid review date for review");
	            }
	            String comment;
	            try {
	                comment = review.getRequiredProperty(commentProperty).getString();
	                if (Objects.isNull(comment)) {
	                    throw new InvalidSPDXAnalysisException("Missing or invalid review comment for review");
	                }
	            } catch (Exception ex) {
	                throw new InvalidSPDXAnalysisException("Missing or invalid review comment for review");
	            }
	            StmtIterator iter = review.listProperties();
	            while (iter.hasNext()) {
	                statementsToRemove.add(iter.next());
	            }
	            int hashOfAnnotation = reviewer.hashCode() ^ reviewDate.hashCode() ^ comment.hashCode();
	            if (addedAnnotations.contains(hashOfAnnotation)) {
	                continue;
	            }
	            addedAnnotations.add(hashOfAnnotation);
	            annotation.addProperty(annotatorProperty, reviewer);
	            annotation.addProperty(annotationDateProperty, reviewDate);
	            annotation.addProperty(commentProperty, comment);
	            annotation.addProperty(annotationTypeProperty, reviewerType);
	            document.addProperty(annotationProperty, annotation);
	        }
	        StmtIterator iter = document.listProperties(model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_SPDX_REVIEWED_BY.getName()));
	        while (iter.hasNext()) {
	            statementsToRemove.add(iter.next());
	        }
	        model.remove(statementsToRemove);
		}
	}

	/**
	 * Convert all artifactOf properties to relationships and remove the old properties and DOAP classes
	 * @param model RDF model
	 * @param documentNamespace the document Namespace
	 * @throws InvalidSPDXAnalysisException on SPDX parsing errors
	 */
	private static void upgradeArtifactOf(Model model, String documentNamespace) throws InvalidSPDXAnalysisException {
		String docNamespace = documentNamespace + "#";
		Set<String> addedDoapProjects = new HashSet<>();	// prevent duplicates
		List<Statement> statementsToRemove = new ArrayList<>();
		Property artifactOfProperty = model.createProperty("http://spdx.org/rdf/terms#artifactOf");
		Property relationshipProperty = model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_RELATIONSHIP.getName());
		String query = "SELECT ?s ?o  WHERE { ?s  <http://spdx.org/rdf/terms#artifactOf> ?o }";
		try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
		    ResultSet result = qe.execSelect();
	        String idPrefix = "SPDXRef-fromDoap-";
	        int nextSpdxIdNum = getNexId(model, docNamespace, idPrefix, 0);
	        while (result.hasNext()) {
	            QuerySolution qs = result.next();
	            Resource subject = qs.get("s").asResource();
	            Resource doapProject = qs.get("o").asResource();
	            statementsToRemove.add(model.createStatement(subject, artifactOfProperty, doapProject));
	            StmtIterator iter = doapProject.listProperties();
	            while (iter.hasNext()) {
	                statementsToRemove.add(iter.next());
	            }
	            Resource pkg = convertDoapProjectToSpdxPackage(model, doapProject, docNamespace + idPrefix + nextSpdxIdNum);
	            if (pkg.isURIResource() && !addedDoapProjects.contains(pkg.getURI())) {
	                addedDoapProjects.add(pkg.getURI());
	                nextSpdxIdNum = getNexId(model, docNamespace, idPrefix, nextSpdxIdNum);
	                Resource relationship = createRelationship(model, pkg, RelationshipType.GENERATED_FROM);
	                subject.addProperty(relationshipProperty, relationship);
	            }
	        }
	        model.remove(statementsToRemove);
		}
	}

	/**
	 * Creates an anonymous relationship resource
	 * @param model RDF model
	 * @param relatedElement related element
	 * @param relationshipType SPDX relationship type
	 * @return resource for the relationship
	 */
	private static Resource createRelationship(Model model, Resource relatedElement, RelationshipType relationshipType) {
		Resource retval = model.createResource();
		retval.addProperty(model.createProperty(RdfSpdxModelManager.RDF_TYPE), 
				model.createResource(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_RELATIONSHIP));
		retval.addProperty(model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_RELATED_SPDX_ELEMENT.getName()), relatedElement);
		retval.addProperty(model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_RELATIONSHIP_TYPE.getName()), 
				model.createResource(relationshipType.getIndividualURI()));
		return retval;
	}

	/**
	 * @param model RDF model
	 * @param docNamespace Namespace or URI for the SPDX document
	 * @param idPrefix prefix for the ID to be used
	 * @param startingNum Starting number to search for the next available ID
	 * @return the next ID number available
	 */
	private static int getNexId(Model model, String docNamespace, String idPrefix, int startingNum) {
		int retval = startingNum;
		Resource idResource = model.getResource(docNamespace + idPrefix + retval);
		while (model.containsResource(idResource)) {
			retval++;
			idResource = model.getResource(docNamespace + idPrefix + retval);
		}
		return retval;
	}

	/**
	 * Convert a DOAP project resource into a package resource
	 * @param model RDF model
	 * @param doapProject DOAP project to convert
	 * @return a related package representing the DOAP project
	 * @throws InvalidSPDXAnalysisException on SPDX parsing errors
	 */
	private static Resource convertDoapProjectToSpdxPackage(Model model, Resource doapProject, String packageUri) throws InvalidSPDXAnalysisException {
		String packageName;
		try {
			packageName = doapProject.getProperty(model.createProperty(SpdxConstantsCompatV2.DOAP_NAMESPACE + SpdxConstantsCompatV2.PROP_NAME.getName())).getString();
		} catch (Exception ex) {
			throw new InvalidSPDXAnalysisException("DOAP project name is not a valid string");
		}
		if (Objects.isNull(packageName)) {
			throw new InvalidSPDXAnalysisException("Missing required DOAP project name");
		}
		String homePage;
		Property homePageProperty = model.createProperty(SpdxConstantsCompatV2.DOAP_NAMESPACE + SpdxConstantsCompatV2.PROP_PROJECT_HOMEPAGE.getName());
		try {
			homePage = doapProject.getProperty(homePageProperty).getString();
		} catch (Exception ex) {
			homePage = null;
		}
		String addedPackageComment = "This package was converted from a DOAP Project by the same name";
		Property commentProperty = model.createProperty(SpdxConstantsCompatV2.RDFS_NAMESPACE + SpdxConstantsCompatV2.RDFS_PROP_COMMENT.getName());
		Property nameProperty = model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_NAME.getName());
		// See if there is already a package matching the name and home page		
		ResIterator iter = model.listResourcesWithProperty(nameProperty, packageName);
		while (iter.hasNext()) {
			Resource pkg = iter.next();
			if (Objects.nonNull(homePage)) {
				Statement homePageStatement = pkg.getProperty(homePageProperty);
				if (Objects.nonNull(homePageStatement) && 
						homePage.equals(homePageStatement.getObject().asLiteral().getString())) {
					return pkg;
				}
			} else {
				// check for the comment to see if we already added this
				Statement commentStatment = pkg.getProperty(commentProperty);
				if (Objects.nonNull(commentStatment) &&
						addedPackageComment.equals(commentStatment.getObject().asLiteral().getString())) {
					return pkg;
				}
			}
		}
		Resource retval = model.createResource(packageUri);
		retval.addProperty(model.createProperty(RdfSpdxModelManager.RDF_TYPE), 
				model.createResource(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.CLASS_SPDX_PACKAGE));
		// download location
		Resource noAssertion = model.createResource(SpdxConstantsCompatV2.URI_VALUE_NOASSERTION);
		retval.addProperty(model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_PACKAGE_DOWNLOAD_URL.getName()), noAssertion);
		// concludedLicense
		retval.addProperty(model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_PACKAGE_CONCLUDED_LICENSE.getName()), noAssertion);
		// declaredLicense
		retval.addProperty(model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_PACKAGE_DECLARED_LICENSE.getName()), noAssertion);
		// copyright
		retval.addProperty(model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_PACKAGE_DECLARED_COPYRIGHT.getName()), noAssertion);
		// comment
		retval.addProperty(commentProperty, addedPackageComment);
		// filesAnalyzed
		retval.addLiteral(model.createProperty(SpdxConstantsCompatV2.SPDX_NAMESPACE + SpdxConstantsCompatV2.PROP_PACKAGE_FILES_ANALYZED.getName()), false);
		// name
		retval.addLiteral(nameProperty, packageName);
		// homePage
		if (Objects.nonNull(homePage)) {
			retval.addLiteral(homePageProperty, homePage);
		}
		return retval;
	}
	
}
