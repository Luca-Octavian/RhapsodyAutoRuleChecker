// ui/wizard/help/FieldValidation.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Helper reutilizabil pentru validare LIVE in dialogurile de wizard:
 * marcheaza vizual (contur rosu) campurile obligatorii necompletate,
 * fara sa astepte click pe Save.
 *
 * Folosire tipica intr-un dialog (vezi ElementSetDialog):
 *
 *   FieldValidation.onChange(idField, this::revalidateLive);
 *   ...
 *   private void revalidateLive() {
 *       boolean valid = true;
 *       if (idField.getText().trim().isEmpty()) {
 *           FieldValidation.markInvalid(idField);
 *           valid = false;
 *       } else {
 *           FieldValidation.markValid(idField);
 *       }
 *       okBtn.setEnabled(valid);
 *   }
 *
 * WeakHashMap - ca sa nu tinem dialogurile inchise "vii" in memorie doar
 * pentru ca au fost validate o data (fara asta, ar fi un memory leak static
 * cat timp ruleaza aplicatia).
 */
public final class FieldValidation {

    private static final Color INVALID_COLOR = new Color(200, 60, 60);

    private static final Map<JComponent, Border> ORIGINAL_BORDERS = new WeakHashMap<>();

    private FieldValidation() {}

    /** Marcheaza componenta ca invalida (contur rosu peste border-ul original). */
    public static void markInvalid(JComponent c) {
        Border original = ORIGINAL_BORDERS.get(c);
        if (original == null) {
            original = c.getBorder();
            ORIGINAL_BORDERS.put(c, original);
        }
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INVALID_COLOR, 2), original));
    }

    /** Reface border-ul original (component valid). */
    public static void markValid(JComponent c) {
        Border original = ORIGINAL_BORDERS.get(c);
        if (original != null) {
            c.setBorder(original);
        }
    }

    /** Ataseaza callback-ul la orice schimbare de text (fiecare tasta apasata). */
    public static void onChange(JTextField field, final Runnable callback) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { callback.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { callback.run(); }
            @Override public void changedUpdate(DocumentEvent e) { callback.run(); }
        });
    }

    /** Ataseaza callback-ul la schimbarea selectiei unui combo box. */
    public static void onChange(JComboBox<?> combo, final Runnable callback) {
        combo.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { callback.run(); }
        });
    }
}