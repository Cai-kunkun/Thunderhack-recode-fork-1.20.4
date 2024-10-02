package thunder.hack.modules.movement;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityInteraction;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import thunder.hack.ThunderHack;
import thunder.hack.events.impl.*;
import thunder.hack.gui.font.FontRenderer;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.gui.notification.Notification;
import thunder.hack.modules.Module;
import thunder.hack.modules.client.HudEditor;
import thunder.hack.modules.combat.Criticals;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;
import thunder.hack.setting.impl.BooleanParent;
import thunder.hack.utility.math.MathUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.MovementUtility;
import thunder.hack.utility.player.PlayerUtility;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.Render3DEngine;

import static thunder.hack.modules.client.ClientSettings.isRu;
import static thunder.hack.modules.player.ElytraSwap.getChestPlateSlot;

public class ElytraPlus extends Module {
    public ElytraPlus() {
        super("Elytra+", Category.MOVEMENT);
    }

    private final Setting<Mode> mode = new Setting<>("Mode", Mode.FireWork);
    private final Setting<AntiKick> antiKick = new Setting<>("AntiKick", AntiKick.Jitter, v -> mode.getValue() == Mode.FireWork);
    private final Setting<Float> xzSpeed = new Setting<>("XZSpeed", 1.55f, 0.1f, 10f, v -> mode.getValue() != Mode.Boost && mode.getValue() != Mode.Pitch40Infinite);
    private final Setting<Float> ySpeed = new Setting<>("YSpeed", 0.47f, 0f, 2f, v -> mode.getValue() == Mode.FireWork || mode.getValue() == Mode.SunriseOld);
    private final Setting<Integer> fireSlot = new Setting<>("FireSlot", 1, 1, 9, v -> mode.getValue() == Mode.FireWork);
    private final Setting<BooleanParent> accelerate = new Setting<>("Acceleration", new BooleanParent(false), v -> mode.getValue() == Mode.Control);
    private final Setting<Float> accelerateFactor = new Setting<>("AccelerateFactor", 9f, 0f, 100f, v -> mode.getValue() == Mode.Control).withParent(accelerate);
    private final Setting<Float> fireDelay = new Setting<>("FireDelay", 1.5f, 0f, 1.5f, v -> mode.getValue() == Mode.FireWork);
    private final Setting<Boolean> grim = new Setting<>("Grim", false, v -> mode.getValue() == Mode.FireWork);
    private final Setting<Boolean> stayMad = new Setting<>("GroundSafe", false, v -> mode.getValue() == Mode.FireWork);
    private final Setting<Boolean> keepFlying = new Setting<>("KeepFlying", false, v -> mode.getValue() == Mode.FireWork);
    private final Setting<Boolean> disableOnFlag = new Setting<>("DisableOnFlag", false, v -> mode.getValue() == Mode.FireWork);
    private final Setting<Boolean> allowFireSwap = new Setting<>("AllowFireSwap", false, v -> mode.getValue() == Mode.FireWork);
    private final Setting<Boolean> bowBomb = new Setting<>("BowBomb", false, v -> mode.getValue() == Mode.FireWork || mode.getValue() == Mode.SunriseOld);
    private final Setting<Bind> bombKey = new Setting<>("BombKey", new Bind(-1, false, false), v -> mode.getValue() == Mode.SunriseOld);
    private final Setting<Boolean> instantFly = new Setting<>("InstantFly", true, v -> (mode.getValue() == Mode.Boost || mode.getValue() == Mode.Control));
    private final Setting<Boolean> cruiseControl = new Setting<>("CruiseControl", false, v -> mode.getValue() == Mode.Boost);
    private final Setting<Float> factor = new Setting<>("Factor", 1.5f, 0.1f, 50.0f, v -> mode.getValue() == Mode.Boost);
    private final Setting<Float> upFactor = new Setting<>("UpFactor", 1.0f, 0.0f, 10.0f, v -> (mode.getValue() == Mode.Boost || mode.getValue() == Mode.Control));
    private final Setting<Float> downFactor = new Setting<>("Glide", 1.0f, 0.0f, 10.0f, v -> (mode.getValue() == Mode.Boost || mode.getValue() == Mode.Control));
    private final Setting<Boolean> stopMotion = new Setting<>("StopMotion", true, v -> mode.getValue() == Mode.Boost);
    private final Setting<Float> minUpSpeed = new Setting<>("MinUpSpeed", 0.5f, 0.1f, 5.0f, v -> mode.getValue() == Mode.Boost && cruiseControl.getValue());
    private final Setting<Boolean> forceHeight = new Setting<>("ForceHeight", false, v -> (mode.getValue() == Mode.Boost && cruiseControl.getValue()));
    private final Setting<Integer> manualHeight = new Setting<>("Height", 121, 1, 256, v -> mode.getValue() == Mode.Boost && cruiseControl.getValue() && forceHeight.getValue());
    private final Setting<Float> sneakDownSpeed = new Setting<>("DownSpeed", 1.0F, 0.1F, 10.0F, v -> mode.getValue() == Mode.Control);
    private final Setting<Boolean> speedLimit = new Setting<>("SpeedLimit", true, v -> (mode.getValue() == Mode.Boost || mode.getValue() == Mode.Control));
    private final Setting<Float> maxSpeed = new Setting<>("MaxSpeed", 2.5f, 0.1f, 10.0f, v -> (mode.getValue() == Mode.Boost || mode.getValue() == Mode.Control));
    private final Setting<Float> packetDelay = new Setting<>("Limit", 1F, 0.1F, 5F, v -> mode.getValue() == Mode.Boost);
    private final Setting<Float> staticDelay = new Setting<>("Delay", 5F, 0.1F, 20F, v -> mode.getValue() == Mode.Boost);
    private final Setting<Float> timeout = new Setting<>("Timeout", 0.5F, 0.1F, 1F, v -> mode.getValue() == Mode.Boost);
    private final Setting<Float> infiniteMaxSpeed = new Setting<>("InfiniteMaxSpeed", 150f, 50f, 170f, v -> mode.getValue() == Mode.Pitch40Infinite);
    private final Setting<Float> infiniteMinSpeed = new Setting<>("InfiniteMinSpeed", 25f, 10f, 70f, v -> mode.getValue() == Mode.Pitch40Infinite);
    private final Setting<Integer> infiniteMaxHeight = new Setting<>("InfiniteMaxHeight", 200, 50, 360, v -> mode.getValue() == Mode.Pitch40Infinite);

    public enum Mode {FireWork, SunriseOld, Boost, Control, Pitch40Infinite, SunriseNew}

    public enum AntiKick {Off, Jitter, Glide}

    private final thunder.hack.utility.Timer instantFlyTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer staticTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer strictTimer = new thunder.hack.utility.Timer();
    private final thunder.hack.utility.Timer pingTimer = new thunder.hack.utility.Timer();

    private boolean hasElytra, infiniteFlag, hasTouchedGround, elytraEquiped, flying, startFallFlying;
    private float acceleration, accelerationY, height, prevClientPitch, infinitePitch, lastInfinitePitch;
    public static long lastStartFalling;

    private ItemStack prevArmorItemCopy, getStackInSlotCopy;
    private Item prevArmorItem = Items.AIR;
    private Item prevItemInHand = Items.AIR;
    private Vec3d flightZonePos;
    private int prevElytraSlot = -1;
    private int slotWithFireWorks = -1;
    private long lastFireworkTime;
    private int ticksInAir;

    @Override
    public void onEnable() {
        if (mc.player.getY() < infiniteMaxHeight.getValue() && mode.getValue() == Mode.Pitch40Infinite) {
            disable(
                    isRu() ?
                            "Поднимись выше " + Formatting.AQUA + infiniteMaxHeight.getValue() + Formatting.GRAY + " высоты!" :
                            "Go above " + Formatting.AQUA + infiniteMaxHeight.getValue() + Formatting.GRAY + " height!"
            );
        }

        flying = false;
        reset();

        infiniteFlag = false;
        acceleration = 0;
        accelerationY = 0;

        if (mc.player != null) {
            height = (float) mc.player.getY();
            if (!mc.player.isCreative()) mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().flying = false;
        }
        hasElytra = false;
        pingTimer.reset();

        if (mode.getValue() == Mode.FireWork) fireworkOnEnable();
    }

    @EventHandler
    public void modifyVelocity(EventTravel e) {
        if (mode.getValue() == Mode.Pitch40Infinite) {
            if (e.isPre()) {
                prevClientPitch = mc.player.getPitch();
                mc.player.setPitch(lastInfinitePitch);
            } else mc.player.setPitch(prevClientPitch);
        }
        if (mode.getValue() == Mode.FireWork) {
            if (ThunderHack.playerManager.ticksElytraFlying < 4) {
                if (e.isPre()) {
                    prevClientPitch = mc.player.getPitch();
                    mc.player.setPitch(-45f);
                } else mc.player.setPitch(prevClientPitch);
            }
        }
        if (mode.getValue() == Mode.SunriseNew) {
            if (mc.options.jumpKey.isPressed()) {
                if (e.isPre()) {
                    prevClientPitch = mc.player.getPitch();
                    mc.player.setPitch(-45f);
                } else mc.player.setPitch(prevClientPitch);
            } else if (mc.options.sneakKey.isPressed()) {
                if (e.isPre()) {
                    prevClientPitch = mc.player.getPitch();
                    mc.player.setPitch(45f);
                } else mc.player.setPitch(prevClientPitch);
            }
        }
    }

    @EventHandler
    public void onSync(EventSync e) {
        switch (mode.getValue()) {
            case SunriseOld -> {
                doSunrise();
            }
            case SunriseNew -> {
                doSunriseNew();
            }
            case Boost, Control -> {
                doPreLegacy();
            }
            case FireWork -> {
                fireworkOnSync();
            }
            case Pitch40Infinite -> {
                ItemStack is = mc.player.getEquippedStack(EquipmentSlot.CHEST);
                if (is.isOf(Items.ELYTRA)) mc.player.setPitch(lastInfinitePitch);
                if (is.isOf(Items.ELYTRA) && is.getDamage() > 380 && mc.player.age % 100 == 0) {
                    ThunderHack.notificationManager.publicity("Elytra+", isRu() ? "Элитра скоро сломается!" : "Elytra's about to break!", 2, Notification.Type.WARNING);
                    mc.world.playSound(mc.player, mc.player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.AMBIENT, 10.0f, 1.0F);
                }
            }
        }
    }

    private void doSunriseNew() {
        if (mc.player.horizontalCollision)
            acceleration = 0;

        int elytra = InventoryUtility.getElytra();
        if (elytra == -1) return;
        if (mc.player.isOnGround()) {
            mc.player.jump();
            acceleration = 0;
            return;
        }

        if (mc.player.fallDistance <= 0)
            return;

        if (mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed()) {
            acceleration = 0;
            takeOnElytra();
        } else {
            takeOnChestPlate();
            if (mc.player.age % 8 == 0)
                matrixDisabler(elytra);

            MovementUtility.setMotion(Math.min((acceleration = (acceleration + 8.0F / xzSpeed.getValue())) / 100.0F, xzSpeed.getValue()));
            if (!MovementUtility.isMoving()) acceleration = 0;
            mc.player.setVelocity(mc.player.getVelocity().getX(), -0.005F, mc.player.getVelocity().getZ());
        }
    }

    private void takeOnElytra() {
        int elytra = InventoryUtility.getElytra();
        if (elytra == -1) return;
        elytra = elytra >= 0 && elytra < 9 ? elytra + 36 : elytra;
        if (elytra != -2) {
            clickSlot(elytra);
            clickSlot(6);
            clickSlot(elytra);
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
    }

    private void takeOnChestPlate() {
        int slot = getChestPlateSlot();
        if (slot == -1) return;
        if (slot != -2) {
            clickSlot(slot);
            clickSlot(6);
            clickSlot(slot);
        }
    }

    private float getInfinitePitch() {
        if (mc.player.getY() < infiniteMaxHeight.getValue()) {
            if (ThunderHack.playerManager.currentPlayerSpeed * 72f < infiniteMinSpeed.getValue() && !infiniteFlag)
                infiniteFlag = true;
            if (ThunderHack.playerManager.currentPlayerSpeed * 72f > infiniteMaxSpeed.getValue() && infiniteFlag)
                infiniteFlag = false;
        } else infiniteFlag = true;

        if (infiniteFlag) infinitePitch += 3;
        else infinitePitch -= 3;

        infinitePitch = MathUtility.clamp(infinitePitch, -40, 40);
        return infinitePitch;
    }

    @Override
    public void onDisable() {
        ThunderHack.TICK_TIMER = 1.0f;
        hasElytra = false;
        if (mc.player != null) {
            if (!mc.player.isCreative()) mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().flying = false;
        }
        if (mode.getValue() == Mode.FireWork) fireworkOnDisable();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(EventMove e) {
        if (mode.getValue() == Mode.Control || mode.getValue() == Mode.Boost) doLegacy(e);
        if (mode.getValue() == Mode.FireWork) fireworkOnMove(e);
    }

    @EventHandler
    public void onPacketSend(PacketEvent.SendPost event) {
        if (fullNullCheck()) return;
        if (event.getPacket() instanceof ClientCommandC2SPacket command && mode.getValue() == Mode.FireWork)
            if (command.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING)
                doFireWork(false);

        if(event.getPacket() instanceof PlayerInteractEntityC2SPacket p) {
            if (mode.is(Mode.FireWork) && grim.getValue() && flying && flightZonePos != null && Criticals.getEntity(p).age < (pingTimer.getPassedTimeMs() / 50f)) {
                sendMessage(Formatting.RED + (isRu() ? "В этом режиме нельзя бить сущностей которые появились после включения модуля!" : "In this mode, you cannot hit entities that spawned after the module was turned on!"));
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (e.getPacket() instanceof PlayerPositionLookS2CPacket) {
            acceleration = 0;
            accelerationY = 0;
            if (disableOnFlag.getValue() && mode.getValue() == Mode.FireWork)
                disable(isRu() ? "Выключен из-за флага!" : "Disabled due to flag!");
            pingTimer.reset();
        }
        if (e.getPacket() instanceof CommonPingS2CPacket && mode.is(Mode.FireWork) && grim.getValue() && flying) {
            if (!pingTimer.passedMs(50000)) {
                if (pingTimer.passedMs(1000) && mc.player.squaredDistanceTo(flightZonePos) < 7000)
                    e.cancel();
            } else pingTimer.reset();
        }
    }

    @EventHandler
    public void onPlayerUpdate(PlayerUpdateEvent e) {
        if (mode.getValue() == Mode.FireWork) {
            fireWorkOnPlayerUpdate();
        }
        if (mode.getValue() == Mode.Pitch40Infinite) {
            lastInfinitePitch = PlayerUtility.fixAngle(getInfinitePitch());
        }
    }

    private void doPreLegacy() {
        if (fullNullCheck()) return;
        if (mc.player.isOnGround()) hasTouchedGround = true;
        if (!cruiseControl.getValue()) height = (float) mc.player.getY();

        for (ItemStack is : mc.player.getArmorItems()) {
            if (is.getItem() instanceof ElytraItem) {
                hasElytra = true;
                break;
            } else hasElytra = false;
        }

        if (strictTimer.passedMs(1500) && !strictTimer.passedMs(2000))
            ThunderHack.TICK_TIMER = 1.0f;

        if (!mc.player.isFallFlying()) {
            if (hasTouchedGround && !mc.player.isOnGround() && mc.player.fallDistance > 0 && instantFly.getValue())
                ThunderHack.TICK_TIMER = 0.3f;

            if (!mc.player.isOnGround() && instantFly.getValue() && mc.player.getVelocity().getY() < 0D) {
                if (!instantFlyTimer.passedMs((long) (1000 * timeout.getValue()))) return;
                instantFlyTimer.reset();
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                hasTouchedGround = false;
                strictTimer.reset();
            }
        }
    }

    public void onRender3D(MatrixStack stack) {
        if (mode.is(Mode.FireWork) && grim.getValue() && flying && flightZonePos != null) {
            stack.push();
            Render3DEngine.setupRender();
            RenderSystem.disableCull();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferBuilder = tessellator.getBuffer();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            bufferBuilder.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            float cos;
            float sin;
            for (int i = 0; i <= 30; i++) {
                cos = (float) ((flightZonePos.getX() - mc.getEntityRenderDispatcher().camera.getPos().getX()) + Math.cos(i * (Math.PI * 2f) / 30f) * 95);
                sin = (float) ((flightZonePos.getZ() - mc.getEntityRenderDispatcher().camera.getPos().getZ()) + Math.sin(i * (Math.PI * 2f) / 30f) * 95);
                bufferBuilder.vertex(stack.peek().getPositionMatrix(), cos, (float) ((float) -mc.getEntityRenderDispatcher().camera.getPos().getY()), sin).color(Render2DEngine.injectAlpha(HudEditor.getColor(i), 255).getRGB()).next();
                bufferBuilder.vertex(stack.peek().getPositionMatrix(), cos, (float) ((float) 128 - mc.getEntityRenderDispatcher().camera.getPos().getY()), sin).color(Render2DEngine.injectAlpha(HudEditor.getColor(i), 0).getRGB()).next();
            }
            tessellator.draw();
            RenderSystem.enableCull();
            Render3DEngine.endRender();
            stack.pop();
        }
    }

    public void onRender2D(DrawContext context) {
        if (mode.is(Mode.FireWork) && grim.getValue() && flying) {
            if (!pingTimer.passedMs(50000)) {
                if (pingTimer.passedMs(1000)) {
                    int timeS = (int) MathUtility.round2(((float) (50000 - pingTimer.getPassedTimeMs()) / 1000f));
                    int dist = (int) (83f - Math.sqrt(mc.player.squaredDistanceTo(flightZonePos)));
                    FontRenderers.sf_bold.drawCenteredString(context.getMatrices(), isRu() ? ("Осталось " + timeS + " секунд и " + dist + " метров") : (timeS + " seconds and " + dist + " meters left"),
                            mc.getWindow().getScaledWidth() / 2f, mc.getWindow().getScaledHeight() / 2f + 30f, -1);
                }
            }
        }
    }

    private void doSunrise() {
        if (mc.player.horizontalCollision)
            acceleration = 0;
        if (mc.player.verticalCollision) {
            acceleration = 0;
            mc.player.setVelocity(mc.player.getVelocity().getX(), 0.41999998688697815, mc.player.getVelocity().getZ());
        }

        int elytra = InventoryUtility.getElytra();
        if (elytra == -1) return;
        if (mc.player.isOnGround()) mc.player.jump();
        if (System.currentTimeMillis() - lastStartFalling > 80L) matrixDisabler(elytra);

        if (mc.player.fallDistance > 0.25f) {
            MovementUtility.setMotion(Math.min((acceleration = (acceleration + 11.0F / xzSpeed.getValue())) / 100.0F, xzSpeed.getValue()));
            if (!MovementUtility.isMoving()) acceleration = 0;

            if (InputUtil.isKeyPressed(mc.getWindow().getHandle(), bombKey.getValue().getKey())) {
                MovementUtility.setMotion(0.8f);
                mc.player.setVelocity(mc.player.getVelocity().getX(), mc.player.age % 2 == 0 ? 0.41999998688697815 : -0.41999998688697815, mc.player.getVelocity().getZ());
                acceleration = 70;
            } else
                mc.player.setVelocity(mc.player.getVelocity().getX(), -0.01F - (mc.player.age % 2 == 0 ? 1.0E-4F : 0.006F), mc.player.getVelocity().getZ());


            if (!mc.player.isSneaking() && mc.options.jumpKey.isPressed())
                mc.player.setVelocity(mc.player.getVelocity().getX(), ySpeed.getValue(), mc.player.getVelocity().getZ());

            if (mc.options.sneakKey.isPressed())
                mc.player.setVelocity(mc.player.getVelocity().getX(), -ySpeed.getValue(), mc.player.getVelocity().getZ());
        }
    }

    private void doLegacy(EventMove e) {
        if (fullNullCheck() || !hasElytra || !mc.player.isFallFlying()) return;
        if (!mc.player.isTouchingWater() || mc.player != null && mc.player.getAbilities().flying && !mc.player.isInLava() || mc.player.getAbilities().flying && mc.player.isFallFlying()) {
            e.cancel();
            if (mode.getValue() == Mode.Control) doControl(e);
            else doBoost(e);
        }
    }

    private void doBoost(EventMove e) {
        float moveForward = mc.player.input.movementForward;

        if (cruiseControl.getValue()) {
            if (mc.options.jumpKey.isPressed()) height += upFactor.getValue() * 0.5f;
            else if (mc.options.sneakKey.isPressed()) height -= downFactor.getValue() * 0.5f;

            if (forceHeight.getValue()) height = manualHeight.getValue();

            double horizSpeed = ThunderHack.playerManager.currentPlayerSpeed;
            double horizPct = MathHelper.clamp(horizSpeed / 1.7, 0.0, 1.0);
            double heightPct = 1 - Math.sqrt(horizPct);
            double minAngle = 0.6;

            if (horizSpeed >= minUpSpeed.getValue() && instantFlyTimer.passedMs((long) (2000 * packetDelay.getValue()))) {
                double pitch = -((45 - minAngle) * heightPct + minAngle);

                double diff = (height + 1 - mc.player.getY()) * 2;
                double heightDiffPct = MathHelper.clamp(Math.abs(diff), 0.0, 1.0);
                double pDist = -Math.toDegrees(Math.atan2(Math.abs(diff), horizSpeed * 30.0)) * Math.signum(diff);

                double adjustment = (pDist - pitch) * heightDiffPct;

                mc.player.setPitch((float) pitch + (float) adjustment);
                mc.player.prevPitch = mc.player.getPitch();
            } else {
                mc.player.setPitch(0.25F);
                mc.player.prevPitch = 0.25F;
                moveForward = 1F;
            }
        }

        Vec3d vec3d = mc.player.getRotationVec(mc.getTickDelta());

        float f = mc.player.getPitch() * 0.017453292F;

        double d6 = Math.sqrt(vec3d.x * vec3d.x + vec3d.z * vec3d.z);
        double d8 = Math.sqrt(e.getX() * e.getX() + e.getZ() * e.getZ());
        double d1 = vec3d.length();
        float f4 = MathHelper.cos(f);
        f4 = (float) ((double) f4 * (double) f4 * Math.min(1.0D, d1 / 0.4D));

        e.setY(e.getY() + (-0.08D + (double) f4 * 0.06D));

        if (e.getY() < 0.0D && d6 > 0.0D) {
            double d2 = e.getY() * -0.1D * (double) f4;
            e.setY(e.getY() + d2);
            e.setX(e.getX() + vec3d.x * d2 / d6);
            e.setZ(e.getZ() + vec3d.z * d2 / d6);
            mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
        }

        if (f < 0.0F) {
            double d10 = d8 * (double) (-MathHelper.sin(f)) * 0.04D;
            e.setY(e.getY() + d10 * 3.2D);
            e.setX(e.getX() - vec3d.x * d10 / d6);
            e.setZ(e.getZ() - vec3d.z * d10 / d6);
            mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
        }

        if (d6 > 0.0D) {
            e.setX(e.getX() + (vec3d.x / d6 * d8 - e.getX()) * 0.1D);
            e.setZ(e.getZ() + (vec3d.z / d6 * d8 - e.getZ()) * 0.1D);
            mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
        }

        float yaw = mc.player.getYaw() * 0.017453292F;

        if (f > 0F && e.getY() < 0D) {
            if (moveForward != 0F && instantFlyTimer.passedMs((long) (2000 * packetDelay.getValue())) && staticTimer.passedMs((long) (1000 * staticDelay.getValue()))) {
                if (stopMotion.getValue()) {
                    e.setX(0);
                    e.setZ(0);
                }
                instantFlyTimer.reset();
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            } else if (!instantFlyTimer.passedMs((long) (2000 * packetDelay.getValue()))) {
                e.setX(e.getX() - moveForward * Math.sin(yaw) * factor.getValue() / 20F);
                e.setZ(e.getZ() + moveForward * Math.cos(yaw) * factor.getValue() / 20F);
                mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
                staticTimer.reset();
            }
        }

        double finalDist = Math.sqrt(e.getX() * e.getX() + e.getZ() * e.getZ());

        if (speedLimit.getValue() && finalDist > maxSpeed.getValue()) {
            e.setX(e.getX() * maxSpeed.getValue() / finalDist);
            e.setZ(e.getZ() * maxSpeed.getValue() / finalDist);
            mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
        }
    }

    private void doControl(EventMove e) {
        if (mc.options.jumpKey.isPressed()) {
            e.setY(upFactor.getValue());
        } else if (mc.options.sneakKey.isPressed()) {
            e.setY(-sneakDownSpeed.getValue());
        } else if (!mc.options.jumpKey.isPressed()) {
            e.setY(-0.08D * downFactor.getValue());
        }

        if (!mc.options.jumpKey.isPressed()) {
            double[] dir = MovementUtility.forward(xzSpeed.getValue() * (accelerate.getValue().isEnabled() ? Math.min((acceleration += accelerateFactor.getValue()) / 100.0f, 1.0f) : 1f));
            e.setX(dir[0]);
            e.setZ(dir[1]);
        }

        if (!MovementUtility.isMoving())
            acceleration = 0;

        double speed = Math.hypot(e.getX(), e.getZ());

        if (speedLimit.getValue() && speed > maxSpeed.getValue()) {
            e.setX(e.getX() * maxSpeed.getValue() / speed);
            e.setZ(e.getZ() * maxSpeed.getValue() / speed);
        }
    }

    public static void matrixDisabler(int elytra) {
        elytra = elytra >= 0 && elytra < 9 ? elytra + 36 : elytra;
        if (elytra != -2) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, elytra, 1, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 1, SlotActionType.PICKUP, mc.player);
        }
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        if (elytra != -2) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 1, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, elytra, 1, SlotActionType.PICKUP, mc.player);
        }
        lastStartFalling = System.currentTimeMillis();
    }

    private int getFireWorks(boolean hotbar) {
        if (hotbar) {
            return InventoryUtility.findItemInHotBar(Items.FIREWORK_ROCKET).slot();
        } else return InventoryUtility.findItemInInventory(Items.FIREWORK_ROCKET).slot();
    }

    private void noFireworks() {
        disable(isRu() ? "Нету фейерверков в инвентаре!" : "No fireworks in the hotbar!");
        flying = false;
        ticksInAir = 0;
    }

    private void noElytra() {
        disable(isRu() ? "Нету элитр в инвентаре!" : "No elytras found in the inventory!");
        flying = false;
        ticksInAir = 0;
    }

    private void reset() {
        slotWithFireWorks = -1;
        prevItemInHand = Items.AIR;
        getStackInSlotCopy = null;
        ticksInAir = 0;
    }

    private void resetPrevItems() {
        prevElytraSlot = -1;
        prevArmorItem = Items.AIR;
        prevArmorItemCopy = null;
    }

    private void moveFireworksToHotbar(int n2) {
        clickSlot(n2);
        clickSlot(fireSlot.getValue() - 1 + 36);
        clickSlot(n2);
    }

    private void returnItem() {
        if (slotWithFireWorks == -1 || getStackInSlotCopy == null || prevItemInHand == Items.FIREWORK_ROCKET || prevItemInHand == Items.AIR) {
            return;
        }
        int n2 = findInInventory(getStackInSlotCopy, prevItemInHand);
        n2 = n2 < 9 && n2 != -1 ? n2 + 36 : n2;
        clickSlot(n2);
        clickSlot(fireSlot.getValue() - 1 + 36);
        clickSlot(n2);
    }

    public static int findInInventory(ItemStack stack, Item item) {
        if (stack == null) {
            return -1;
        }
        for (int i2 = 0; i2 < 45; ++i2) {
            ItemStack is = mc.player.getInventory().getStack(i2);
            if (!ItemStack.areItemsEqual(is, stack) || is.getItem() != item) continue;
            return i2;
        }
        return -1;
    }

    private int getFireworks() {
        if (mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET) {
            return -2;
        }
        int firesInHotbar = getFireWorks(true);
        int firesInInventory = getFireWorks(false);
        if (firesInInventory == -1) {
            noFireworks();
            return -1;
        }
        if (firesInHotbar == -1) {
            if (!allowFireSwap.getValue()) {
                disable(isRu() ? "Нет фейерверков!" : "No fireworks!");
                return fireSlot.getValue() - 1;
            }
            moveFireworksToHotbar(firesInInventory);
            return fireSlot.getValue() - 1;
        }
        return firesInHotbar;
    }

    private boolean canFly() {
        if (shouldSwapToElytra()) return false;
        return getFireworks() != -1;
    }

    private boolean shouldSwapToElytra() {
        ItemStack is = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (is.getItem() != Items.ELYTRA) {
            return true;
        }
        return !ElytraItem.isUsable(is);
    }

    private void doFireWork(boolean started) {
        if (started && (float) (System.currentTimeMillis() - lastFireworkTime) < fireDelay.getValue() * 1000.0f)
            return;

        if (grim.getValue() && started && pingTimer.passedMs(200) && flightZonePos != null && mc.player.squaredDistanceTo(flightZonePos) < 7000)
            return;

        if (started && !mc.player.isFallFlying()) return;
        if (!started && ticksInAir > 1) return;

        int n2 = getFireworks();
        if (n2 == -1) {
            slotWithFireWorks = -1;
            return;
        }
        slotWithFireWorks = n2;
        boolean inOffhand = mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET;
        if (!inOffhand) sendPacket(new UpdateSelectedSlotC2SPacket(n2));
        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(inOffhand ? Hand.OFF_HAND : Hand.MAIN_HAND, id));
        if (!inOffhand) sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
        ++ticksInAir;
        flying = true;
        lastFireworkTime = System.currentTimeMillis();
        pingTimer.reset();
        flightZonePos = mc.player.getPos();
    }

    private void pickPrevElytraSlot() {
        if (prevElytraSlot != -1) clickSlot(prevElytraSlot);
    }

    private void equipElytra() {
        int elytraSlot = InventoryUtility.getElytra();
        if (elytraSlot == -1 && mc.player.currentScreenHandler.getCursorStack().getItem() != Items.ELYTRA) {
            noElytra();
            return;
        }
        if (!shouldSwapToElytra()) return;
        if (prevElytraSlot == -1) {
            ItemStack is = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            prevElytraSlot = elytraSlot;
            prevArmorItem = is.getItem();
            prevArmorItemCopy = is.copy();
        }

        clickSlot(elytraSlot);
        clickSlot(6);
        pickPrevElytraSlot();

        elytraEquiped = true;
    }

    private void returnChestPlate() {
        if (prevElytraSlot != -1 && prevArmorItem != Items.AIR) {
            if (!elytraEquiped) {
                return;
            }
            ItemStack is = mc.player.getInventory().getStack(prevElytraSlot);
            boolean bl2 = is != ItemStack.EMPTY && !ItemStack.areItemsEqual(is, prevArmorItemCopy);
            int n2 = findInInventory(prevArmorItemCopy, prevArmorItem);
            n2 = n2 < 9 && n2 != -1 ? n2 + 36 : n2;
            if (mc.player.currentScreenHandler.getCursorStack().getItem() != Items.AIR) {
                clickSlot(6);
                pickPrevElytraSlot();
                return;
            }
            if (n2 == -1) return;

            clickSlot(n2);
            clickSlot(6);
            if (!bl2) {
                clickSlot(n2);
            } else {
                int n4 = findEmpty(false);
                if (n4 != -1) {
                    clickSlot(n4);
                }
            }
        }
        resetPrevItems();
    }

    public static int findEmpty(boolean hotbar) {
        for (int i2 = hotbar ? 0 : 9; i2 < (hotbar ? 9 : 45); ++i2) {
            if (!mc.player.getInventory().getStack(i2).isEmpty()) continue;
            return i2;
        }
        return -1;
    }

    public void fireWorkOnPlayerUpdate() {
        boolean inAir = mc.world.isAir(BlockPos.ofFloored(mc.player.getPos()));
        boolean aboveLiquid = isAboveLiquid(0.1f) && inAir && mc.player.getVelocity().getY() < 0.0;
        if (mc.player.fallDistance > 0.0f && inAir || aboveLiquid) {
            equipElytra();
        } else if (mc.player.isOnGround()) {
            startFallFlying = false;
            ticksInAir = 0;
            return;
        }

        if (!MovementUtility.isMoving())
            acceleration = 0;
        if (!canFly()) return;

        if (!mc.player.isFallFlying() && !startFallFlying && mc.player.getVelocity().getY() < 0.0) {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            startFallFlying = true;
        }
        if (ThunderHack.playerManager.ticksElytraFlying < 4) {
            mc.options.jumpKey.setPressed(false);
        }
        doFireWork(true);
    }

    public void fireworkOnSync() {
        if (grim.getValue()) {
            if (mc.options.jumpKey.isPressed() && mc.player.isFallFlying() && flying)
                mc.player.setPitch(-45f);

            if (mc.options.sneakKey.isPressed() && mc.player.isFallFlying() && flying)
                mc.player.setPitch(45f);

            mc.player.setYaw(MovementUtility.getMoveDirection());
        }

        if (!MovementUtility.isMoving() && mc.options.jumpKey.isPressed() && mc.player.isFallFlying() && flying)
            mc.player.setPitch(-90f);

        if (ThunderHack.playerManager.ticksElytraFlying < 5 && !mc.player.isOnGround())
            mc.player.setPitch(-45f);
    }

    public void fireworkOnMove(EventMove e) {
        if (mc.player.isFallFlying() && flying) {
            if (mc.player.horizontalCollision || mc.player.verticalCollision) {
                acceleration = 0;
                accelerationY = 0;
            }

            if (ThunderHack.playerManager.ticksElytraFlying < 4) {
                e.setY(0.2f);
                e.cancel();
                return;
            }

            if (mc.options.jumpKey.isPressed()) {
                e.setY(ySpeed.getValue() * Math.min((accelerationY += 9) / 100.0f, 1.0f));
            } else if (mc.options.sneakKey.isPressed()) {
                e.setY(-ySpeed.getValue() * Math.min((accelerationY += 9) / 100.0f, 1.0f));
            } else if (bowBomb.getValue() && checkGround(2.0f)) {
                e.setY(mc.player.age % 2 == 0 ? 0.42f : -0.42f);
            } else {
                switch (antiKick.getValue()) {
                    case Jitter -> e.setY(mc.player.age % 2 == 0 ? 0.08f : -0.08f);
                    case Glide -> e.setY(-0.08f);
                    case Off -> e.setY(0f);
                }
            }

            if (!MovementUtility.isMoving())
                acceleration = 0;

            if (mc.player.input.movementSideways > 0) {
                mc.player.input.movementSideways = 1;
            } else if (mc.player.input.movementSideways < 0) {
                mc.player.input.movementSideways = -1;
            }

            MovementUtility.modifyEventSpeed(e, xzSpeed.getValue() * Math.min((acceleration += 9) / 100.0f, 1.0f));
            if (stayMad.getValue() && !checkGround(3.0f) && ThunderHack.playerManager.ticksElytraFlying > 10)
                e.setY(0.42f);
            e.cancel();
        }
    }

    public static boolean checkGround(float f2) {
        if (mc.player.getY() < 0.0) return false;
        return !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0.0, -f2, 0.0)).iterator().hasNext();
    }

    public static boolean isAboveLiquid(float offset) {
        if (mc.player == null) return false;
        return mc.world.getBlockState(BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - (double) offset, mc.player.getZ())).getBlock() instanceof FluidBlock;
    }

    public void fireworkOnEnable() {
        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA && mc.player.currentScreenHandler.getCursorStack().getItem() != Items.ELYTRA && InventoryUtility.getElytra() == -1) {
            noElytra();
            return;
        }
        if (getFireWorks(false) == -1) {
            noFireworks();
            return;
        }
        if (getFireWorks(true) != -1) return;
        getStackInSlotCopy = mc.player.getInventory().getStack(fireSlot.getValue() - 1).copy();
        prevItemInHand = mc.player.getInventory().getStack(fireSlot.getValue() - 1).getItem();
    }

    public void fireworkOnDisable() {
        startFallFlying = false;
        if (keepFlying.getValue()) return;
        mc.player.setVelocity(0, mc.player.getVelocity().getY(), 0);
        new Thread(() -> {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            ThunderHack.TICK_TIMER = 0.1f;
            returnItem();
            reset();
            try {
                Thread.sleep(200L);
            } catch (InterruptedException interruptedException) {
                ThunderHack.TICK_TIMER = 1f;
                interruptedException.printStackTrace();
            }
            returnChestPlate();
            resetPrevItems();
            ThunderHack.TICK_TIMER = 1f;
        }).start();
    }
}