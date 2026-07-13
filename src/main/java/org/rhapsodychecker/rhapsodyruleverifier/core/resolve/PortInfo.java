package org.rhapsodychecker.rhapsodyruleverifier.core.resolve;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.Multiplicity;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.PortDirection;

public final class PortInfo {
    private final PortDirection direction;
    private final Multiplicity multiplicity;
    private final String directionSource;    // e.g., "provided/required", "flowPortTag:direction"
    private final String multiplicitySource; // e.g., "method:getMultiplicity", "tag:multiplicity"

    public PortInfo(PortDirection direction, Multiplicity multiplicity,
                    String directionSource, String multiplicitySource) {
        this.direction = direction;
        this.multiplicity = multiplicity;
        this.directionSource = directionSource;
        this.multiplicitySource = multiplicitySource;
    }

    public PortDirection direction() { return direction; }
    public Multiplicity multiplicity() { return multiplicity; }
    public String directionSource() { return directionSource; }
    public String multiplicitySource() { return multiplicitySource; }

    @Override public String toString() {
        return "PortInfo{dir=" + direction + " (" + directionSource + "), mult=" + multiplicity + " (" + multiplicitySource + ")}";
    }
}