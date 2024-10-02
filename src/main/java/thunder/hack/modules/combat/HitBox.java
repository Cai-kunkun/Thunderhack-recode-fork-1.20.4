package thunder.hack.modules.combat;

import thunder.hack.modules.Module;
import thunder.hack.setting.Setting;

import static thunder.hack.core.impl.ServerManager.round2;

public final class HitBox extends Module {
    private static HitBox instance;

    public HitBox() {
        super("HitBoxes", Category.COMBAT);
        instance = this;
    }

    public static final Setting<Float> XZExpand = new Setting<>("XZExpand", 1.0f, 0.0f, 5.0f);
    public static final Setting<Float> YExpand = new Setting<>("YExpand", 0.0f, 0.0f, 5.0f);
    public static final Setting<Boolean> affectToAura = new Setting<>("AffectToAura", false);

    @Override
    public String getDisplayInfo() {
        return "H: " + round2(XZExpand.getValue()) + " V: " + round2(YExpand.getValue());
    }

    public static HitBox getInstance() {
        return instance;
    }
}
