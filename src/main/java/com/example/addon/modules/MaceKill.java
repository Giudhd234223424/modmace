package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

/**
 * MaceKill + Totem Bypass.
 *
 * Smash: prima del colpo manda pacchetti di movimento (su/giu, onGround=false)
 * cosi' il server accumula fallDistance e applica il danno smash della mace.
 *
 * Totem bypass: se il bersaglio ha un totem, il primo colpo (altezza minore)
 * fa "pop" del totem; subito dopo il colpo reale con altezza maggiore supera il
 * danno gia' subito nei frame di invulnerabilita' (viene applicata la differenza)
 * e uccide il bersaglio prima che il totem/assorbimento lo protegga.
 *
 * Target: Minecraft 1.21.x, mappings Yarn. Da 1.21.2 PositionAndOnGround
 * richiede anche horizontalCollision (vedi sendPos).
 */
public class MaceKill extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTotem = settings.createGroup("Totem Bypass");

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("Altezza simulata della caduta (anche altezza del colpo finale col bypass).")
        .defaultValue(40).min(2).sliderRange(2, 150).build());

    private final Setting<Boolean> onlyPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("only-players")
        .description("Applica l'effetto solo quando colpisci giocatori.")
        .defaultValue(false).build());

    private final Setting<Boolean> checkCeiling = sgGeneral.add(new BoolSetting.Builder()
        .name("check-ceiling")
        .description("Limita l'altezza se ci sono blocchi sopra la testa.")
        .defaultValue(true).build());

    private final Setting<Boolean> totemBypass = sgTotem.add(new BoolSetting.Builder()
        .name("totem-bypass")
        .description("Doppio colpo per superare il totem dell'immortalita'.")
        .defaultValue(true).build());

    private final Setting<Boolean> onlyIfTotem = sgTotem.add(new BoolSetting.Builder()
        .name("only-if-totem")
        .description("Usa il doppio colpo solo se il bersaglio ha un totem in mano.")
        .defaultValue(true).visible(totemBypass::get).build());

    private final Setting<Double> firstHeight = sgTotem.add(new DoubleSetting.Builder()
        .name("first-hit-height")
        .description("Altezza del primo colpo (deve far scoppiare il totem ed essere MINORE di 'height').")
        .defaultValue(15).min(2).sliderRange(2, 100).visible(totemBypass::get).build());

    private static final double STEP = 9.0; // limite ~10 blocchi per pacchetto

    public MaceKill() {
        super(AddonTemplate.CATEGORY, "mace-kill", "Smash con la mace da qualsiasi altezza + totem bypass.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || mc.getNetworkHandler() == null || mc.world == null) return;
        if (!mc.player.getMainHandStack().isOf(Items.MACE)) return;
        if (!(event.entity instanceof LivingEntity target)) return;
        if (onlyPlayers.get() && !(event.entity instanceof PlayerEntity)) return;

        double cap = checkCeiling.get() ? freeHeightAbove(height.get()) : height.get();
        double finalH = Math.min(height.get(), cap);
        if (finalH < 2) return;

        boolean doBypass = totemBypass.get() && (!onlyIfTotem.get() || hasTotem(target));

        if (doBypass) {
            double firstH = Math.min(firstHeight.get(), finalH - 1); // il secondo deve essere piu' forte
            if (firstH >= 2) {
                fakeFall(firstH);
                // Colpo extra: fa scoppiare il totem. Dopo lo smash la fallDistance torna a 0.
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        }

        // Caduta finale: subito dopo parte il pacchetto di attacco reale
        fakeFall(finalH);
    }

    /** Sale di "h" blocchi e riscende, tutto con onGround=false. */
    private void fakeFall(double h) {
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        double up = 0;
        while (up < h) { up = Math.min(h, up + STEP); sendPos(x, y + up, z); }
        double cur = h;
        while (cur > 0) { cur = Math.max(0, cur - STEP); sendPos(x, y + cur, z); }
    }

    private boolean hasTotem(Entity e) {
        return e instanceof LivingEntity le
            && (le.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) || le.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING));
    }

    private void sendPos(double x, double y, double z) {
        // 1.21 - 1.21.1:
        // mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false));
        // 1.21.2+:
        mc.getNetworkHandler().sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, mc.player.horizontalCollision));
    }

    private double freeHeightAbove(double max) {
        Box box = mc.player.getBoundingBox();
        double free = 0;
        while (free < max) {
            if (!mc.world.isSpaceEmpty(mc.player, box.offset(0, free + 1, 0))) break;
            free++;
        }
        return free;
    }
}
