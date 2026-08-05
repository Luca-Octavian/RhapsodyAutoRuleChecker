package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import java.awt.*;
import javax.swing.*;

/**
 * A simple header strip with a bold-ish label and a thin bottom divider.
 *
 * <p>Used at the top of content panes (package tree, results) to give
 * each section an identifiable header rather than a floating label.
 */
public class SectionHeader extends JPanel {

    public SectionHeader(String title) {
        setLayout(new BorderLayout());

        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));

        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        add(label, BorderLayout.CENTER);
    }
}