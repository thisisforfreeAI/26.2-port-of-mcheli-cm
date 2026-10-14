package io.github.mcheliceport.content.parser;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.content.aircraft.ShipInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.content.hud.Hud;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Parses content files of one format into content definitions.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.helper.info.parsers.IParser}, without the dead
 * {@code item} content type. Parsers only fill in definitions; the content loader runs each
 * definition's {@code validate()} once afterwards.
 *
 * <p>The {@code reload} flag marks a re-parse for {@code /mcheli reconfig}, during which settings
 * that need a restart are skipped.
 */
public interface ContentParser {

    /** Identifier stored in parsed definitions, for example {@code txt}. */
    String getIdentifier();

    @Nullable HeliInfo parseHelicopter(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception;

    @Nullable PlaneInfo parsePlane(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception;

    @Nullable ShipInfo parseShip(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception;

    @Nullable TankInfo parseTank(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception;

    @Nullable VehicleInfo parseVehicle(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception;

    @Nullable WeaponInfo parseWeapon(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception;

    @Nullable ThrowableInfo parseThrowable(ContentLocation location, String filepath, List<String> lines,
            boolean reload) throws Exception;

    @Nullable Hud parseHud(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception;
}
