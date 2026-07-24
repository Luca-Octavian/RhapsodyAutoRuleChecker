// detection/rhapsody/ProfileDetector.java
package org.rhapsodychecker.rhapsodyruleverifier.detection.rhapsody;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPProfile;

import org.rhapsodychecker.rhapsodyruleverifier.detection.SuggestionsService;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.ProfileSummary;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Citește profilurile active din proiectul Rhapsody.
 * Detectează heurisitic dacă modelul pare să folosească ISO26262/ASIL
 * pe baza numelui profilurilor găsite.
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
                    // Folosim SuggestionsService.isAsilProfileName pentru consistenta
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
