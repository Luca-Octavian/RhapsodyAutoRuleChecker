// ui/wizard/help/FieldValidation.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Helper reutilizabil pentru validare LIVE in dialogurile de wizard:
 * marcheaza vizual campurile obligatorii necompletate, fara sa astepte
 * click pe Save.
 *
 * Foloseste proprietatea client "JComponent.outline" din FlatLaf, care
 * deseneaza un contur folosind culorile temei curente (Arc, Nord, etc.)
 * in loc de o culoare hardcodata -- ramane coerent vizual indiferent de
 * tema aleasa. Functioneaza direct pe JTextField, JComboBox, JSpinner
 * si alte JComponent-uri suportate de FlatLaf.
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
 * Nota: daca aplicatia ar rula vreodata sub un alt Look and Feel (nu FlatLaf),
 * proprietatea client e pur si simplu ignorata -- nu apare nicio eroare, dar
 * nici indicatie vizuala. Cat timp ramanem pe FlatLaf (cazul nostru), functioneaza
 * direct.
 */
public final class FieldValidation {

    private FieldValidation() {}

    /** Marcheaza componenta ca invalida (contur rosu, colorat de tema curenta). */
    public static void markInvalid(JComponent c) {
        c.putClientProperty("JComponent.outline", "error");
    }

    /** Sterge conturul de eroare (component valid). */
    public static void markValid(JComponent c) {
        c.putClientProperty("JComponent.outline", null);
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