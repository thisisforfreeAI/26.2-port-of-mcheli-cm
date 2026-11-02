package io.github.mcheliceport.content.throwable;

import io.github.mcheliceport.content.BaseInfo;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.weapon.ArgbColor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Hand-thrown item definition (grenades, smoke grenades). Ported from upstream
 * {@code com.norwood.mcheli.throwable.MCH_ThrowableInfo}.
 *
 * <p>{@link #timeFuse}, {@link #aliveTime} and {@link #delayFuse} are in ticks. Legacy TXT files
 * give them in seconds and the TXT parser converts them. Upstream converted in {@code validate()}
 * instead, which the YAML parser also ran, and ran twice, so YAML throwables got times 400 times
 * too long (stock {@code m18.yml}: a 16000-tick smoke fuse instead of 40 ticks).
 */
public class ThrowableInfo extends BaseInfo {

    public final String name;
    public String displayName;
    /** Display names by language code, from {@code AddDisplayName}. */
    public final HashMap<String, String> displayNameLang = new HashMap<>();
    public int itemID = 0;
    /** Recipe lines from {@code AddRecipe}/{@code AddShapelessRecipe}, in the legacy TXT syntax. */
    public List<String> recipeString = new ArrayList<>();
    public boolean isShapedRecipe = true;
    public int power = 0;
    public float acceleration = 1.0F;
    public float accelerationInWater = 1.0F;
    public float dispenseAcceleration = 1.0F;
    public int explosion = 0;
    public int explosionBlock = -1;
    public float explosionRadius = 0.0F;
    public float explosionDamageRadius = 0.0F;
    public boolean isNewExplosionBreak = false;
    public int delayFuse = 0;
    public float bound = 0.2F;
    public int timeFuse = 0;
    public boolean flaming = false;
    public int stackSize = 1;
    public float soundVolume = 1.0F;
    public float soundPitch = 1.0F;
    public float proximityFuseDist = 0.0F;
    public float accuracy = 0.0F;
    /** Upstream's default of 10 seconds. */
    public int aliveTime = 200;
    public int bomblet = 0;
    public float bombletDiff = 0.3F;
    public float smokeSize = 10.0F;
    public int smokeNum = 0;
    public float smokeVelocityVertical = 1.0F;
    public float smokeVelocityHorizontal = 1.0F;
    public float gravity = 0.0F;
    public float gravityInWater = -0.04F;
    public String particleName = "explode";
    public boolean disableSmoke = true;
    public ArgbColor smokeColor = new ArgbColor();

    public ThrowableInfo(ContentLocation location, String path, String parserIdentifier) {
        super(location, path, parserIdentifier);
        this.name = location.path();
        this.displayName = location.path();
    }

    @Override
    public String toString() {
        return "ThrowableInfo{name='" + this.name + "', location=" + this.location + '}';
    }
}
