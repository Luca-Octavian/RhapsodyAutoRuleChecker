// detection/rhapsody/ProfileDetector.java
package org.rhapsodychecker.rhapsodyruleverifier.detection.rhapsody;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPProfile;

import org.rhapsodychecker.rhapsodyruleverifier.detection.SuggestionsService;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.ProfileSummary;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads active profiles from the Rhapsody project.
 * Heuristically detects whether the model appears to use ISO26262/ASIL
 * based on the names of the discovered profiles.
 */
public final class ProfileDetector {

    private static final Logger LOG = Logger.getLogger(ProfileDetector.class.getName());
    public ProfileSummary detect(IRPApplication app) {
        Set<String> names     = new LinkedHashSet<>();
        boolean     asilHints = false;

        try {
            IRPCollection profiles = app.activeProject().getProfiles();
            for (int i = 1; i <= profiles.getCount(); i++) {
                IRPProfile profile = (IRPProfile) profiles.getItem(i);
                String name = profile.getName();
                if (name != null && !name.trim().isEmpty()) {
                    names.add(name);
                    // Use SuggestionsService.isAsilProfileName for consistency
                    if (SuggestionsService.isAsilProfileName(name)) {
                        asilHints = true;
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not read profiles from Rhapsody project", e);
        }

        return new ProfileSummary(names, asilHints);
    }

}
