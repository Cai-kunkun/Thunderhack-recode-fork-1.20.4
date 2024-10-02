package thunder.hack.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import thunder.hack.ThunderHack;
import thunder.hack.core.impl.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.modules.Module;
import thunder.hack.setting.Setting;

public class Step extends Module {

    private final thunder.hack.utility.Timer stepTimer = new thunder.hack.utility.Timer();
    private final Setting<Boolean> strict = new Setting<>("Strict", false);
    private final Setting<Float> height = new Setting("Height", 2.0F, 1F, 2.5F, v -> !strict.getValue());
    private final Setting<Boolean> useTimer = new Setting<>("Timer", true);
    private final Setting<Boolean> pauseIfShift = new Setting<>("PauseIfShift", false);
    private final Setting<Integer> stepDelay = new Setting("StepDelay", 200, 0, 1000);
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.NCP);
    private boolean timer;

    public Step() {
        super("Step", Category.MOVEMENT);
    }

    @Override
    public void onDisable() {
        ThunderHack.TICK_TIMER = 1f;
        mc.player.setStepHeight(0.6F);
    }

    @Override
    public void onUpdate() {
        if (pauseIfShift.getValue() && mc.options.sneakKey.isPressed()) {
            mc.player.setStepHeight(0.6F);
            return;
        }

        if (mc.player.getAbilities().flying || ModuleManager.freeCam.isOn() || mc.player.isRiding() || mc.player.isTouchingWater()) {
            mc.player.setStepHeight(0.6F);
            return;
        }

        if (timer && mc.player.isOnGround()) {
            ThunderHack.TICK_TIMER = 1f;
            timer = false;
        }

        if (mc.player.isOnGround() && stepTimer.passedMs(stepDelay.getValue()))
            mc.player.setStepHeight(height.getValue());
        else mc.player.setStepHeight(0.6F);
    }

    @EventHandler
    public void onStep(EventSync event) {
        if (mode.getValue() == Mode.NCP) {
            double stepHeight = mc.player.getY() - mc.player.prevY;

            if (stepHeight <= 0.75 || stepHeight > height.getValue() || (strict.getValue() && stepHeight > 1))
                return;

            double[] offsets = getOffset(stepHeight);
            if (offsets != null && offsets.length > 1) {
                if (useTimer.getValue()) {
                    ThunderHack.TICK_TIMER = 1F / offsets.length;
                    timer = true;
                }
                for (double offset : offsets)
                    sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.prevX, mc.player.prevY + offset, mc.player.prevZ, false));
                if (strict.getValue())
                    sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.prevX, mc.player.prevY + stepHeight, mc.player.prevZ, false));
            }
            stepTimer.reset();
        }
    }

    public double[] getOffset(double h) {
        return switch ((int) (h * 10000)) {
            case 7500, 10000 -> new double[]{0.42, 0.753};
            case 8125, 8750 -> new double[]{0.39, 0.7};
            case 15000 -> new double[]{0.42, 0.75, 1.0, 1.16, 1.23, 1.2};
            case 20000 -> new double[]{0.42, 0.78, 0.63, 0.51, 0.9, 1.21, 1.45, 1.43};
            case 250000 -> new double[]{0.425, 0.821, 0.699, 0.599, 1.022, 1.372, 1.652, 1.869, 2.019, 1.907};
            default -> null;
        };
    }

    public enum Mode {NCP, VANILLA}
}