// detection/api/ProfileSummary.java
package org.rhapsodychecker.rhapsodyruleverifier.detection.api;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Result of detecting profiles loaded in the project.
 * hasAsilHints = true if any profile contains ISO26262/ASIL keywords.
 */
public final class ProfileSummary {

    private final Set<String> profileNames;
    private final boolean     hasAsilHints;

    public ProfileSummary(Set<String> profileNames, boolean hasAsilHints) {
        this.profileNames = Collections.unmodifiableSet(new TreeSet<>(profileNames));
        this.hasAsilHints = hasAsilHints;
    }

    public Set<String> profileNames() { return profileNames; }
    public boolean     hasAsilHints() { return hasAsilHints; }

    @Override
    public String toString() {
        return "ProfileSummary{profiles=" + profileNames + ", asilHints=" + hasAsilHints + "}";
    }
}
