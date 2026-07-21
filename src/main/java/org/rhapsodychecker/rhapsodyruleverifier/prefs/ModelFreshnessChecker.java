// prefs/ModelFreshnessChecker.java
package org.rhapsodychecker.rhapsodyruleverifier.prefs;

import java.io.File;

/**
 * Verificare ieftina (fara conectare la Rhapsody) daca fisierul .rpyx
 * s-a modificat de la ultima scanare reusita, folosind doar
 * File.lastModified() -- un apel de sistem, instant.
 *
 * Nu detecteaza CE anume s-a schimbat in model, doar DACA fisierul
 * a fost atins de la ultima scanare completa. Daca da, o reconectare
 * si recolectare completa raman necesare -- nu exista o cale ieftina
 * de "diff partial" fara sa interoghezi Rhapsody direct.
 */
public final class ModelFreshnessChecker {

    private final RecentFilesStore store;

    public ModelFreshnessChecker(RecentFilesStore store) {
        this.store = store;
    }

    /**
     * @return true daca fisierul de la modelPath pare neschimbat fata de
     *         ultima scanare inregistrata (deci un cache existent ar putea
     *         fi reutilizat fara reconectare). false daca fisierul s-a
     *         modificat, sau daca nu exista nicio scanare anterioara inregistrata.
     */
    public boolean isModelUnchangedSinceLastScan(String modelPath) {
        File f = new File(modelPath);
        if (!f.exists()) return false;

        return store.getRecordedModelTimestamp(modelPath)
                .map(recorded -> recorded == f.lastModified())
                .orElse(false);
    }

    /**
     * Apelat dupa o scanare/colectare completa reusita, ca sa retina
     * "momentul de referinta" pentru verificarile viitoare.
     */
    public void recordSuccessfulScan(String modelPath) {
        File f = new File(modelPath);
        if (f.exists()) {
            store.recordModelTimestamp(modelPath, f.lastModified());
        }
    }
}