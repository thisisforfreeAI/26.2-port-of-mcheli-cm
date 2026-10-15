package io.github.mcheliceport.content.aircraft;

import io.github.mcheliceport.content.ContentLocation;

/** Ship definition; upstream duplicates the plane fields and defaults, excluding exhaust flames. */
public class ShipInfo extends WingedAircraftInfo {
    public ShipInfo(ContentLocation location, String path, String parserIdentifier) {
        super(location, path, parserIdentifier);
    }

    @Override public String getDirectoryName() { return "ships"; }
    @Override public String getKindName() { return "ship"; }
}
