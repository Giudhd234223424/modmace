package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * MaceKill + Totem Bypass (versione con mappings Mojang).
 *
 * Smash: prima del colpo manda pacchetti di movimento (su/giu, onGround=false)
 * cosi' il server accumula fallDistance e applica il danno smash della mace.
 *
 * Totem bypass: se il bersaglio ha un totem, il primo colpo (altezza minore)
 * fa scoppiare il totem; subito dopo il colpo reale con altezza maggiore
 * supera il danno gia' subito e applica la differenza.
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
        .description("Altezza del primo colpo (deve essere MINORE di 'height').")
        .defaultValue(15).min(2).sliderRange(2, 100).visible(totemBypass::get).build());

    private static final double STEP = 9.0; // limite ~10 blocchi per pacchetto

    public MaceKill() {
        super(AddonTemplate.CATEGORY, "mace-kill", "Smash con la mace da qualsiasi altezza + totem bypass.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || mc.getConnection() == null || mc.level == null) return;
        if (!mc.player.getMainHandItem().is(Items.MACE)) return;
        if (!(event.entity instanceof LivingEntity target)) return;
        if (onlyPlayers.get() && !(event.entity instanceof Player)) return;

        double cap = checkCeiling.get() ? freeHeightAbove(height.get()) : height.get();
        double finalH = Math.min(height.get(), cap);
        if (finalH < 2) return;

        boolean doBypass = totemBypass.get() && (!onlyIfTotem.get() || hasTotem(target));

        if (doBypass) {
            double firstH = Math.min(firstHeight.get(), finalH - 1); // il secondo deve essere piu' forte
            if (firstH >= 2) {
                fakeFall(firstH);
                // Colpo extra: fa scoppiare il totem. Dopo lo smash la fallDistance torna a 0.
                mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(target, mc.player.isShiftKeyDown()));
                mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
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
            && (le.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || le.getOffhandItem().is(Items.TOTEM_OF_UNDYING));
    }

    private void sendPos(double x, double y, double z) {
        mc.getConnection().send(
            new ServerboundMovePlayerPacket.Pos(x, y, z, false, mc.player.horizontalCollision));
    }

    private double freeHeightAbove(double max) {
        AABB box = mc.player.getBoundingBox();
        double free = 0;
        while (free < max) {
            if (!mc.level.noCollision(mc.player, box.move(0, free + 1, 0))) break;
            free++;
        }
        return free;
    }
}
