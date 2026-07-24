// ui/ModelUpdateController.java
package org.rhapsodychecker.rhapsodyruleverifier.ui;

import org.rhapsodychecker.rhapsodyruleverifier.prefs.ModelFreshnessChecker;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Logica din spatele butonului "Update Model": decide daca fisierul de model
 * chiar s-a schimbat de la ultima scanare (verificare ieftina, doar
 * File.lastModified(), fara nicio conectare la Rhapsody), si actioneaza
 * corespunzator:
 *
 *   - daca s-a schimbat  -> ruleaza actiunea de reincarcare primita (de ex. loadModel())
 *   - daca NU s-a schimbat -> afiseaza un mesaj clar "nimic de actualizat",
 *                             fara sa mai reincarce degeaba (evita 1-2 minute
 *                             de reconectare/recolectare inutila)
 *   - daca nu exista deloc cale de model, sau fisierul nu exista pe disc ->
 *     avertizeaza corespunzator
 *
 * Separata de MainFrame pentru a putea fi testata/inlocuita independent
 * de restul UI-ului.
 */
public final class ModelUpdateController {

    private final ModelFreshnessChecker freshnessChecker;

    public ModelUpdateController(ModelFreshnessChecker freshnessChecker) {
        this.freshnessChecker = freshnessChecker;
    }

    /**
     * @param dialogParent  componenta parinte pentru dialogurile afisate (de obicei MainFrame)
     * @param modelPath     calea curenta din campul de model (poate fi goala)
     * @param reloadAction  actiunea de reincarcare efectiva (de obicei MainFrame::loadModel);
     *                      rulata doar daca fisierul chiar pare modificat
     */
    public void handleUpdateRequest(Component dialogParent, String modelPath, Runnable reloadAction) {
        if (modelPath == null || modelPath.trim().isEmpty()) {
            JOptionPane.showMessageDialog(dialogParent,
                    "No model path selected yet.",
                    "Update Model", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File file = new File(modelPath);
        if (!file.exists()) {
            JOptionPane.showMessageDialog(dialogParent,
                    "Model file not found:\n" + modelPath,
                    "Update Model", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (freshnessChecker.isModelUnchangedSinceLastScan(modelPath)) {
            JOptionPane.showMessageDialog(dialogParent,
                    "The model file hasn't changed since it was last loaded.\n"
                            + "Nothing to update.",
                    "No Changes Detected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Fisierul pare modificat -> chiar merita o reincarcare completa
        reloadAction.run();
    }
}