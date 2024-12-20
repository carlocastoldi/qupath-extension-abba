package qupath.ext.biop.abba;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.biop.abba.struct.AtlasHelper;
import qupath.ext.biop.abba.struct.AtlasOntology;
import qupath.lib.gui.QuPathGUI;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.projects.Projects;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class LoadAtlasRoisToQuPathCommand implements Runnable {

    final static Logger logger = LoggerFactory.getLogger(LoadAtlasRoisToQuPathCommand.class);

    private static QuPathGUI qupath;

    private boolean splitLeftRight;
    private boolean doRun;

    public LoadAtlasRoisToQuPathCommand( final QuPathGUI qupath) {
        this.qupath = qupath;
    }

    public void run() {

        String splitMode =
                Dialogs.showChoiceDialog("Load Brain RoiSets into Image",
                        "This will load any RoiSets Exported using the ABBA tool onto the current image.\nContinue?", new String[]{"Split Left and Right Regions", "Do not split"}, "Do not split");

        switch (splitMode) {
            case "Do not split" :
                splitLeftRight = false;
                doRun = true;
                break;
            case "Split Left and Right Regions" :
                splitLeftRight = true;
                doRun = true;
                break;
            default:
                // null returned -> cancelled
                doRun = false;
                return;
        }
        if (doRun) {
            ImageData<BufferedImage> imageData = qupath.getImageData();
            List<String> atlasNames = AtlasTools.getAvailableAtlasRegistration(imageData);
            if (atlasNames.isEmpty()) {
                Dialogs.showErrorMessage("No atlas registration found.", "You first need to export your registration from Fiji's ABBA plugin.");
                logger.error("No atlas registration found."); // TODO : show an error message for the user
                return;
            }

            String atlasName = atlasNames.get(0);
            if (atlasNames.size()>1) {
                logger.warn("Several atlases registration have been found. Importing atlas: "+atlasName);
            }

            Path ontologyPath = Paths.get(Projects.getBaseDirectory(qupath.getProject()).getAbsolutePath(), atlasName+"-Ontology.json");
            AtlasOntology ontology = AtlasHelper.openOntologyFromJsonFile(ontologyPath.toString());
            if (ontology == null) {
                Dialogs.showErrorMessage("No atlas ontology found.",
                        "Can't find the ontology corresponding to the atlas registration of the image."+
                                "You first need to export your registration from Fiji's ABBA plugin.");
                logger.error("No atlas ontology found."); // TODO : show an error message for the user
                return;
            }

            // Get naming possibilities
            String namingProperty =
                    Dialogs.showChoiceDialog("Regions names",
                            "Please select the property for naming the imported regions.",
                            AtlasTools.getNamingProperties(ontology).toArray(new String[0]),
                            "ID");

            ontology.setNamingProperty(namingProperty);

            // Now we have all we need, the name whether to split left and right
            AtlasTools.loadWarpedAtlasAnnotations(ontology, imageData, splitLeftRight, true);

            // Add a step to the workflow
            String method = AtlasTools.class.getName()+".loadWarpedAtlasAnnotations(getCurrentImageData(), \""+namingProperty+"\", "+splitLeftRight+", true);";
            WorkflowStep newStep = new DefaultScriptableWorkflowStep("Load Brain RoiSets into Image", method);
            imageData.getHistoryWorkflow().addStep(newStep);
        }
    }

}