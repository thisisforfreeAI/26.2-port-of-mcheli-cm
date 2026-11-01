package io.github.mcheliceport.content.hud;

import io.github.mcheliceport.content.BaseInfo;
import io.github.mcheliceport.content.ContentLocation;
import java.util.ArrayList;
import java.util.List;

/**
 * A HUD script: the list of drawing commands for one seat's view.
 *
 * <p>Ported from the data part of upstream {@code com.norwood.mcheli.hud.MCH_Hud}. Upstream loaded
 * HUDs only on the client; the port parses them on both sides, since they are plain data, so that
 * vehicle definitions resolve HUD names the same way everywhere.
 */
public class Hud extends BaseInfo {

    /** Name meaning "no HUD", as upstream's {@code MCH_Hud.NoDisp}. */
    public static final String NONE_NAME = "none";

    public final String name;
    public final String fileName;
    /** Parse state of the TXT format: an {@code If} is waiting for its {@code Endif}. */
    public boolean isWaitEndif;
    /** Draw at 1:1 GUI scale even when the HUD is automatically scaled. */
    public boolean ignoreAutoScale;
    public final List<HudItem> list = new ArrayList<>();

    public Hud(ContentLocation location, String filePath, String parserIdentifier) {
        super(location, filePath, parserIdentifier);
        this.name = location.path();
        this.fileName = filePath;
        this.ignoreAutoScale = false;
    }

    @Override
    public boolean validate() {
        if (this.isWaitEndif) {
            throw new IllegalStateException("Endif not found!");
        }
        return true;
    }

    @Override
    public String toString() {
        return "Hud{name='" + this.name + "', items=" + this.list.size() + ", location=" + this.location + '}';
    }
}
