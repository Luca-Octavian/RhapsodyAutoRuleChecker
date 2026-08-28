// ui/wizard/help/HelpIcon.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help;

import javax.swing.*;
import java.awt.*;

/**
 * Iconita mica "(?)" care se ataseaza langa un label si arata pe hover
 * descrierea prietenoasa a campului, luata din HoverInfoProvider.
 *
 * De ce iconita separata si nu doar setToolTipText pe JTextField:
 * un field GOL nu are niciun indiciu vizual ca ai putea sa afli mai
 * multe daca stai cu mouse-ul deasupra. O iconita "(?)" e un semnal
 * explicit ca acolo exista ajutor.
 */
public final class HelpIcon extends JLabel {

    private static final long serialVersionUID = 1L;

    public HelpIcon(String internalKey) {
        super("\u24D8"); // simbol circular "i" din Unicode; poti inlocui cu un .png daca vrei stil custom
        HoverInfoProvider.FieldHelp help = HoverInfoProvider.get(internalKey);

        Color muted = UIManager.getColor("Label.disabledForeground");
        setForeground(muted != null ? muted : Color.GRAY);
        setFont(getFont().deriveFont(Font.PLAIN, 13f));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

        StringBuilder tip = new StringBuilder("<html><body style='width: 220px'>");
        tip.append("<b>").append(help.friendlyLabel()).append("</b><br>");
        tip.append(help.description());
        if (help.example() != null) {
            tip.append("<br><i style='color:gray'>").append(help.example()).append("</i>");
        }
        tip.append("</body></html>");

        setToolTipText(tip.toString());
    }

    /**
     * Helper de conventie: construieste un rand "Label + (?)" gata de adaugat
     * intr-un GridBagLayout, de exemplu.
     */
    public static JPanel labelWithHelp(String label, String internalKey) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.add(new JLabel(label));
        row.add(new HelpIcon(internalKey));
        return row;
    }
}