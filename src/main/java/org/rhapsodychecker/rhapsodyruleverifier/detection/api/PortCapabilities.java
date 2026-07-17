// detection/api/PortCapabilities.java
package org.rhapsodychecker.rhapsodyruleverifier.detection.api;

/**
 * Rezultatul probe-ului pe porturi, separat pe tipuri:
 *   - directed: PORT_FLOW + PORT_PROXY (au direction)
 *   - standard: PORT (de obicei fara direction/type)
 */
public final class PortCapabilities {

    private static final double RESOLVABLE_THRESHOLD = 0.9;

    private final int directedSampled;
    private final int directedOkType;
    private final int directedOkDirection;
    private final int directedOkMultiplicity;

    private final int standardSampled;
    private final int standardOkType;
    private final int standardOkDirection;
    private final int standardOkMultiplicity;

    public PortCapabilities(
            int directedSampled,
            int directedOkType,
            int directedOkDirection,
            int directedOkMultiplicity,
            int standardSampled,
            int standardOkType,
            int standardOkDirection,
            int standardOkMultiplicity
    ) {
        this.directedSampled         = directedSampled;
        this.directedOkType          = directedOkType;
        this.directedOkDirection     = directedOkDirection;
        this.directedOkMultiplicity  = directedOkMultiplicity;
        this.standardSampled         = standardSampled;
        this.standardOkType          = standardOkType;
        this.standardOkDirection     = standardOkDirection;
        this.standardOkMultiplicity  = standardOkMultiplicity;
    }

    // ── Directed ports (PORT_FLOW + PORT_PROXY) ───────────────────────────────

    public int    directedSampled()              { return directedSampled; }
    public int    directedOkType()               { return directedOkType; }
    public int    directedOkDirection()          { return directedOkDirection; }
    public int    directedOkMultiplicity()       { return directedOkMultiplicity; }

    public double directedTypeSuccessRate()         { return rate(directedOkType, directedSampled); }
    public double directedDirectionSuccessRate()    { return rate(directedOkDirection, directedSampled); }
    public double directedMultiplicitySuccessRate() { return rate(directedOkMultiplicity, directedSampled); }

    public boolean directedTypeResolvable()         { return directedTypeSuccessRate() >= RESOLVABLE_THRESHOLD; }
    public boolean directedDirectionResolvable()    { return directedDirectionSuccessRate() >= RESOLVABLE_THRESHOLD; }
    public boolean directedMultiplicityResolvable() { return directedMultiplicitySuccessRate() >= RESOLVABLE_THRESHOLD; }

    // ── Standard ports (PORT) ─────────────────────────────────────────────────

    public int    standardSampled()              { return standardSampled; }
    public int    standardOkType()               { return standardOkType; }
    public int    standardOkDirection()          { return standardOkDirection; }
    public int    standardOkMultiplicity()       { return standardOkMultiplicity; }

    public double standardTypeSuccessRate()         { return rate(standardOkType, standardSampled); }
    public double standardDirectionSuccessRate()    { return rate(standardOkDirection, standardSampled); }
    public double standardMultiplicitySuccessRate() { return rate(standardOkMultiplicity, standardSampled); }

    public boolean standardTypeResolvable()         { return standardTypeSuccessRate() >= RESOLVABLE_THRESHOLD; }
    public boolean standardDirectionResolvable()    { return standardDirectionSuccessRate() >= RESOLVABLE_THRESHOLD; }
    public boolean standardMultiplicityResolvable() { return standardMultiplicitySuccessRate() >= RESOLVABLE_THRESHOLD; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static double rate(int ok, int total) {
        return total == 0 ? 0d : (double) ok / total;
    }

    @Override
    public String toString() {
        return "PortCapabilities{"
                + "directed[sampled=" + directedSampled
                + ", type=" + pct(directedTypeSuccessRate())
                + ", dir=" + pct(directedDirectionSuccessRate())
                + ", mult=" + pct(directedMultiplicitySuccessRate()) + "]"
                + ", standard[sampled=" + standardSampled
                + ", type=" + pct(standardTypeSuccessRate())
                + ", dir=" + pct(standardDirectionSuccessRate())
                + ", mult=" + pct(standardMultiplicitySuccessRate()) + "]}";
    }

    private static String pct(double v) {
        return String.format("%.0f%%", v * 100);
    }
}
