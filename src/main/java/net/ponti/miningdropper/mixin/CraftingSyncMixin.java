package net.ponti.miningdropper.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.ponti.miningdropper.MiningDropper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class CraftingSyncMixin {
    @Inject(method = "handlePlaceRecipe", at = @At("RETURN"))
    private void afterGhostRecipe(ClientboundPlaceGhostRecipePacket packet, CallbackInfo ci) {
        MiningDropper.afterRecipeGhost(packet.containerId(), packet.recipeDisplay());
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void afterContents(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        MiningDropper.afterMenuSync(packet.containerId(), true);
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void afterSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        MiningDropper.afterMenuSync(packet.getContainerId(), false);
    }
}
