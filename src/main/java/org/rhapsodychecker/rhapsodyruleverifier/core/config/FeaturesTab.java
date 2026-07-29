package org.rhapsodychecker.rhapsodyruleverifier.core.config;

/**
 * Maps Rhapsody Features dialog tab indices.
 *
 * Tab indices passed to {@code IRPModelElement.openFeaturesDialog(int)}.
 * The exact indices vary by element metaclass, but the common ones are
 * consistent across most element types in Rhapsody 8.x–10.x.
 *
 * Use {@link #GENERAL} as the safe default — it exists for all element types.
 */
public enum FeaturesTab {

    /** General tab — stereotypes, name, metaclass info. Always index 0. */
    GENERAL(0),

    /** Description tab — free-text description. Typically index 1. */
    DESCRIPTION(1),

    /** Tags tab — tagged values. Typically index 2 for elements that support them. */
    TAGS(2);

    private final int index;

    FeaturesTab(int index) {
        this.index = index;
    }

    /** The numeric tab index for {@code openFeaturesDialog(int)}. */
    public int index() {
        return index;
    }
}