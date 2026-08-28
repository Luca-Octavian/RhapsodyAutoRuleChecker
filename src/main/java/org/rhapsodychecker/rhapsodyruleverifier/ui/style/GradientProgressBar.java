package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * A custom-painted progress bar with two visual modes:
 *
 * <ul>
 *   <li><b>Determinate</b> — fills left-to-right with an Orange 200→purple
 *       gradient that transitions proportionally to progress. At 0% the
 *       filled area is Orange 200; at 100% it ends in purple.</li>
 *   <li><b>Indeterminate</b> — an Orange 200 bouncing block, matching
 *       the standard indeterminate look in the app's model-action accent.</li>
 * </ul>
 *
 * <p>Drop-in replacement for {@link JProgressBar}. Text painting
 * ({@code setStringPainted(true)}) is supported and drawn centered
 * over the bar.
 */
public final class GradientProgressBar extends JProgressBar {

    private static final long serialVersionUID = 1L;

    private static final int ARC = 6;

    /* ── Indeterminate animation state ─────────────────────────────────── */
    private float bouncePos = 0f;
    private boolean bounceForward = true;
    private Timer bounceTimer;

    public GradientProgressBar() {
        setOpaque(false);
        setBorderPainted(false);
        setStringPainted(true);
        setString("");
    }

    // ── Indeterminate animation ─────────────────────────────────────────

    @Override
    public void setIndeterminate(boolean newValue) {
        boolean was = isIndeterminate();
        super.setIndeterminate(newValue);
        if (newValue && !was) {
            startBounce();
        } else if (!newValue && was) {
            stopBounce();
        }
    }

    private void startBounce() {
        if (bounceTimer != null) return;
        bouncePos = 0f;
        bounceForward = true;
        bounceTimer = new Timer(16, e -> {
            float speed = 0.012f;
            if (bounceForward) {
                bouncePos += speed;
                if (bouncePos >= 1f) { bouncePos = 1f; bounceForward = false; }
            } else {
                bouncePos -= speed;
                if (bouncePos <= 0f) { bouncePos = 0f; bounceForward = true; }
            }
            repaint();
        });
        bounceTimer.start();
    }

    private void stopBounce() {
        if (bounceTimer != null) {
            bounceTimer.stop();
            bounceTimer = null;
        }
    }

    // ── Custom painting ─────────────────────────────────────────────────

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, 22);
        return d;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();
        Insets ins = getInsets();
        int barX = ins.left;
        int barY = ins.top;
        int barW = w - ins.left - ins.right;
        int barH = h - ins.top - ins.bottom;

        // Track background
        Color trackColor = UIManager.getColor("ProgressBar.background");
        if (trackColor == null) trackColor = new Color(230, 230, 230);
        g2.setColor(trackColor);
        g2.fill(new RoundRectangle2D.Float(barX, barY, barW, barH, ARC, ARC));

        if (isIndeterminate()) {
            paintIndeterminate(g2, barX, barY, barW, barH);
        } else {
            paintDeterminate(g2, barX, barY, barW, barH);
        }

        // Paint text
        if (isStringPainted()) {
            paintString(g2, barX, barY, barW, barH);
        }

        g2.dispose();
    }

    private void paintDeterminate(Graphics2D g2, int barX, int barY, int barW, int barH) {
        double fraction = getPercentComplete();
        int fillW = (int) (barW * fraction);
        if (fillW <= 0) return;

        // Gradient from Orange 200 (left) to purple (right edge of filled area)
        GradientPaint gp = new GradientPaint(
                barX, 0, AccentColors.action(),
                barX + fillW, 0, AccentColors.primary());
        g2.setPaint(gp);

        // Clip to rounded rect for the fill
        Shape clip = g2.getClip();
        g2.clip(new RoundRectangle2D.Float(barX, barY, barW, barH, ARC, ARC));
        g2.fillRect(barX, barY, fillW, barH);
        g2.setClip(clip);
    }

    private void paintIndeterminate(Graphics2D g2, int barX, int barY, int barW, int barH) {
        int blockW = Math.max(barW / 5, 40);
        int range = barW - blockW;
        int blockX = barX + (int) (range * bouncePos);

        g2.setColor(AccentColors.action());
        Shape clip = g2.getClip();
        g2.clip(new RoundRectangle2D.Float(barX, barY, barW, barH, ARC, ARC));
        g2.fillRoundRect(blockX, barY, blockW, barH, ARC, ARC);
        g2.setClip(clip);
    }

    private void paintString(Graphics2D g2, int barX, int barY, int barW, int barH) {
        String text = getString();
        if (text == null || text.isEmpty()) return;

        Font font = getFont();
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int textW = fm.stringWidth(text);
        int textH = fm.getAscent();
        int tx = barX + (barW - textW) / 2;
        int ty = barY + (barH + textH) / 2 - fm.getDescent();

        // Determine if the fill covers the text center — if so, use white;
        // otherwise use a dark foreground so text is readable on the gray track.
        boolean fillCoversText;
        if (isIndeterminate()) {
            int blockW = Math.max(barW / 5, 40);
            int range = barW - blockW;
            int blockX = barX + (int) (range * bouncePos);
            int textCenter = tx + textW / 2;
            fillCoversText = textCenter >= blockX && textCenter <= blockX + blockW;
        } else {
            int fillW = (int) (barW * getPercentComplete());
            fillCoversText = fillW > (tx - barX + textW / 2);
        }

        if (fillCoversText) {
            g2.setColor(new Color(0, 0, 0, 50));
            g2.drawString(text, tx + 1, ty + 1);
            g2.setColor(Color.WHITE);
        } else {
            Color fg = UIManager.getColor("Label.foreground");
            if (fg == null) fg = Color.DARK_GRAY;
            g2.setColor(fg);
        }
        g2.drawString(text, tx, ty);
    }
}