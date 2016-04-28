// From Ryan Scherle at a NESCent Informatics hack day -

package org.opentreeoflife.taxa;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.semanticweb.skos.*;
import org.semanticweb.skosapibinding.SKOSManager;
import org.semanticweb.skosapibinding.SKOSFormatExt;

public class Skos {
    static final String SKOS_BASE_URI = "http://purl.obolibrary.org/obo/OTT_";

	static void dumpNodesSkos(Taxonomy tax, Collection<Taxon> nodes, String outprefix) throws IOException {
            try {
                SKOSManager manager = new SKOSManager();
                SKOSDataset dataset = manager.createSKOSDataset(URI.create(SKOS_BASE_URI));
                SKOSDataFactory df = manager.getSKOSDataFactory();
                SKOSConceptScheme conceptScheme = df.getSKOSConceptScheme(URI.create(SKOS_BASE_URI));
                SKOSEntityAssertion entityAssertion1 = df.getSKOSEntityAssertion(conceptScheme);
                
                List<SKOSChange> addAssertions = new ArrayList<SKOSChange>();
                addAssertions.add (new AddAssertion(dataset, entityAssertion1));
                
                manager.applyChanges(addAssertions);
                
                
                for (Taxon node : tax.taxa()) {
                    if (node == null) {
                        System.err.println("null in nodes list!?" );
                    }
                    else if (!node.prunedp) {
                        dumpNodeSkos(tax, node, manager, dataset, true);
                    }
                }
                
                // save the dataset to a file in RDF/XML format
                File outFile = new File(outprefix + "taxonomy.skos");
                System.err.println("Writing " + outFile.getPath());
                manager.save(dataset, SKOSFormatExt.RDFXML, URI.create("file:" + outFile.getAbsolutePath()));
                
            } catch (SKOSCreationException e) {
                e.printStackTrace();
            } catch (SKOSChangeException e) {
                e.printStackTrace(); 
            } catch (SKOSStorageException e) {
                e.printStackTrace(); 
            }
        }


	// Recursive!
	static void dumpNodeSkos(Taxonomy tax, Taxon node, SKOSManager manager, SKOSDataset dataset, boolean rootp) throws SKOSChangeException {
            SKOSDataFactory df = manager.getSKOSDataFactory();
            SKOSConceptScheme conceptScheme = df.getSKOSConceptScheme(URI.create(SKOS_BASE_URI));
            List<SKOSChange> addAssertions = new ArrayList<SKOSChange>();
            
            // node concept
            SKOSConcept nodeConcept = df.getSKOSConcept(URI.create(SKOS_BASE_URI + "#" + node.id));
            SKOSEntityAssertion nodeAssertion = df.getSKOSEntityAssertion(nodeConcept);
            addAssertions.add (new AddAssertion(dataset, nodeAssertion));

            // node is in scheme
            SKOSObjectRelationAssertion nodeRelation = df.getSKOSObjectRelationAssertion(nodeConcept, df.getSKOSInSchemeProperty(), conceptScheme);
            addAssertions.add (new AddAssertion(dataset, nodeRelation));

            // node parent
            if(!node.isRoot() && node.parent.id != null) {
                SKOSConcept nodeParentConcept = df.getSKOSConcept(URI.create(SKOS_BASE_URI + "#" + node.parent.id));
                nodeRelation = df.getSKOSObjectRelationAssertion(nodeConcept, df.getSKOSBroaderProperty(), nodeParentConcept);
                addAssertions.add (new AddAssertion(dataset, nodeRelation));
            }
            
            // node name 
            /*
              /// commented out because there were problems creating the literal value for the name
              
                SKOSEntity a = nodeConcept;
                SKOSAnnotation b = df.getSKOSAnnotation(df.getSKOSPrefLabelProperty().getURI(), df.getSKOSUntypedConstant(node.name, "en"));
                SKOSAnnotationAssertion nodeLabelAnnotation = df.getSKOSAnnotationAssertion(a, b);
                addAssertions.add (new AddAssertion(dataset, nodeLabelAnnotation));
            }
            */


            /*
              // other items from dumpNode that need to be output as SKOS
              
		// 3. rank:
		out.print((node.rank == Taxonomy.NO_RANK ? "no rank" : node.rank) + "\t|\t");
                
		// 4. source information
		// comma-separated list of URI-or-CURIE
		out.print(node.getSourceIdsString() + "\t|\t");
                
		// 5. uniqname
		out.print(node.uniqueName() + "\t|\t");
                
		// 6. flags
		// (node.mode == null ? "" : node.mode)
		Taxonomy.printFlags(node, out);
            */

            
            // add all assertions to the document
            manager.applyChanges(addAssertions);
            
            
            if (node.children != null) {
                for (Taxon child : node.children) {
                    if (child == null)
                        System.err.println("null in children list!? " + node);
                    else
                        dumpNodeSkos(tax, child, manager, dataset, false);
                }
            }
	}
        
}
