// detection/api/ProfileSummary.java
package org.rhapsodychecker.rhapsodyruleverifier.detection.api;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rezultatul detecției profilurilor încărcate în proiect.
 * hasAsilHints = true dacă vreun profil conține cuvinte cheie ISO26262/ASIL.
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
