// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.osm.visitor.paint;

import static org.openstreetmap.josm.tools.I18n.marktr;

import java.awt.Color;
import java.util.List;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Preferences.ColorKey;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles.MapPaintSylesUpdateListener;
import org.openstreetmap.josm.gui.mappaint.StyleSource;

public enum PaintColors implements ColorKey {

    INACTIVE(marktr("inactive"), Color.darkGray),
    SELECTED(marktr("selected"), Color.red),
    RELATIONSELECTED(marktr("Relation: selected"), Color.magenta),
    NODE(marktr("Node: standard"), Color.yellow),
    CONNECTION(marktr("Node: connection"), Color.yellow),
    TAGGED(marktr("Node: tagged"), new Color(204, 255, 255)), // light cyan
    DEFAULT_WAY(marktr("way"),  new Color(0,0,128)), // dark blue
    RELATION(marktr("relation"), new Color(0,128,128)), // teal
    UNTAGGED_WAY(marktr("untagged way"), new Color(0,128,0)), // dark green
    INCOMPLETE_WAY(marktr("incomplete way"), new Color(0,0,96)), // darker blue
    BACKGROUND(marktr("background"), Color.BLACK),
    HIGHLIGHT(marktr("highlight"), new Color(0, 255, 186)), // lighteal

    UNTAGGED(marktr("untagged"),Color.GRAY),
    TEXT(marktr("text"), Color.WHITE),
    AREA_TEXT(marktr("areatext"), Color.LIGHT_GRAY);

    private final String name;
    private final Color defaultColor;

    private static Color backgroundColorCache = null;

    private static final MapPaintSylesUpdateListener styleOverrideListener = new MapPaintSylesUpdateListener() {

        public void mapPaintStylesUpdated() {
            backgroundColorCache = null;
        }

        public void mapPaintStyleEntryUpdated(int idx) {
            mapPaintStylesUpdated();
        }
    };

    static {
        MapPaintStyles.addMapPaintSylesUpdateListener(styleOverrideListener);
    }

    private PaintColors(String name, Color defaultColor) {
        this.name = name;
        this.defaultColor = defaultColor;
    }

    public String getColorName() {
        return name;
    }

    public Color getDefault() {
        return defaultColor;
    }

    public String getSpecialName() {
        return null;
    }

    public Color get() {
        return Main.pref.getColor(this);
    }

    public static void getColors() {
        for (PaintColors c:values()) {
            c.get();
        }
    }

    public static Color getBackgroundColor() {
        if (backgroundColorCache != null)
            return backgroundColorCache;
        List<StyleSource> sources = MapPaintStyles.getStyles().getStyleSources();
        for (StyleSource s : sources) {
            if (!s.active) {
                continue;
            }
            Color backgroundColorOverride = s.getBackgroundColorOverride();
            if (backgroundColorOverride != null) {
                backgroundColorCache = backgroundColorOverride;
            }
        }
        if (backgroundColorCache == null) {
            backgroundColorCache = BACKGROUND.get();
        }
        return backgroundColorCache;
    }
}
