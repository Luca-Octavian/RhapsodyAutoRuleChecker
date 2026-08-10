package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help;

import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/**
 * Custom-painted horizontal step indicator (1-2-3) showing completed,
 * current and upcoming steps as circles connected by lines.
 *
 * <p>The cluster is capped at {@value #MAX_SPAN}px total width and
 * centered horizontally within the panel, so it never stretches
 * edge-to-edge on wide dialogs. Labels below the circles have been
 * removed — the parent dialog supplies the step title separately.
 */
public final class StepIndicator extends JPanel {

    private static final int CIRCLE_DIAMETER = 24;
    private static final int CIRCLE_RADIUS = CIRCLE_DIAMETER / 2;
    private static final int MAX_SPAN = 300;
    private static final float NUMBER_FONT_SIZE = 12f;

    private final int stepCount;
    private int currentStep = 0;

    public StepIndicator(String[] stepLabels) {
        this.stepCount = stepLabels == null ? 0 : stepLabels.length;
        setOpaque(false);
    }

    /**
     * Sets the current step (0-based). Steps before it are completed,
     * the step at this index is current, steps after are upcoming.
     */
    public void setCurrentStep(int index) {
        this.currentStep = index;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(MAX_SPAN + 40, 36);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(180, 32);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (stepCount == 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();

        // Vertical center for circles
        int circleY = h / 2;

        // Compute spacing: cap total span so circles stay compact
        int totalSpan = Math.min(w - 40, MAX_SPAN);
        int spacing = stepCount > 1 ? totalSpan / (stepCount - 1) : 0;

        // Center the cluster horizontally
        int startX = (w - totalSpan) / 2;

        Color purpleColor = AccentColors.primary();
        Color neutralBorder = AccentColors.palette().border();
        Color mutedText = AccentColors.mutedText();
        Color surfaceText = AccentColors.palette().surface();

        Font numberFont = getFont().deriveFont(Font.BOLD, NUMBER_FONT_SIZE);

        for (int i = 0; i < stepCount; i++) {
            int cx = startX + i * spacing;

            // ── Draw connecting line to the RIGHT of this circle ──
            if (i < stepCount - 1) {
                int nextCx = startX + (i + 1) * spacing;
                if (i < currentStep) {
                    g2.setColor(purpleColor);
                } else {
                    g2.setColor(neutralBorder);
                }
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(cx + CIRCLE_RADIUS + 2, circleY, nextCx - CIRCLE_RADIUS - 2, circleY);
            }

            // ── Draw circle ──
            int circleLeft = cx - CIRCLE_RADIUS;
            int circleTop = circleY - CIRCLE_RADIUS;

            if (i <= currentStep) {
                // Completed or current: filled purple
                g2.setColor(purpleColor);
                g2.fillOval(circleLeft, circleTop, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
            } else {
                // Upcoming: unfilled, 1px neutral border
                g2.setColor(neutralBorder);
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawOval(circleLeft, circleTop, CIRCLE_DIAMETER, CIRCLE_DIAMETER);
            }

            // ── Draw glyph inside circle ──
            if (i < currentStep) {
                // Completed: white checkmark
                drawCheckmark(g2, cx, circleY);
            } else if (i == currentStep) {
                // Current: white number
                g2.setColor(surfaceText);
                g2.setFont(numberFont);
                String num = String.valueOf(i + 1);
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(num);
                int th = fm.getAscent();
                g2.drawString(num, cx - tw / 2, circleY + th / 2 - 1);
            } else {
                // Upcoming: neutral number
                g2.setColor(mutedText);
                g2.setFont(numberFont);
                String num = String.valueOf(i + 1);
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(num);
                int th = fm.getAscent();
                g2.drawString(num, cx - tw / 2, circleY + th / 2 - 1);
            }
        }

        g2.dispose();
    }

    private void drawCheckmark(Graphics2D g2, int cx, int cy) {
        g2.setColor(AccentColors.palette().surface());
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Path2D check = new Path2D.Float();
        // Checkmark points relative to center, scaled to fit inside 24px circle
        check.moveTo(cx - 5, cy - 1);
        check.lineTo(cx - 1, cy + 3);
        check.lineTo(cx + 5, cy - 4);

        g2.draw(check);
    }
}