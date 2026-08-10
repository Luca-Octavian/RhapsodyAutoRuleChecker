package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import java.awt.Color;

/**
 * Catalog of supported application appearances.
 *
 * <p>The catalog owns palette construction only. Selecting and applying an
 * appearance remains the responsibility of {@link AppTheme}, keeping palette
 * definitions independent from Swing's UIManager.</p>
 */
public final class UiPalettes {

    private static final UiPalette LIGHT = new UiPalette(
            color("#F7F5FC"), color("#FFFCFF"), color("#F0ECF7"),
            color("#DDD5E8"), color("#292433"), color("#736B80"),
            color("#6A5FC4"), color("#E6E0FA"), color("#FF6B3D"),
            color("#FFE2D8"), color("#D86A68"), color("#FBE2E1"),
            color("#B97916"), color("#3C78B5"), color("#E9E4FA"),
            color("#292433"), color("#FBF9FD"));

    private static final UiPalette DARK = new UiPalette(
            color("#272331"), color("#332E42"), color("#423B57"),
            color("#585067"), color("#EDEBF2"), color("#B5ADBE"),
            color("#B9A9FF"), color("#4B4168"), color("#FF9A74"),
            color("#4D2D29"), color("#FFAAA5"), color("#503032"),
            color("#F6C76B"), color("#86BFFF"), color("#5A4B7C"),
            color("#EDEBF2"), color("#3A354C"));

    private UiPalettes() {}

    public static UiPalette light() {
        return LIGHT;
    }

    public static UiPalette dark() {
        return DARK;
    }

    private static Color color(String hex) {
        return Color.decode(hex);
    }
}