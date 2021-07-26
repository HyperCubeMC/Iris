package net.coderbot.iris.mixin;

import net.coderbot.iris.Iris;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
	@Inject(method = "setWorld", at = @At("TAIL"))
	public void iris$setWorld(ClientWorld world, CallbackInfo ci) {
		reloadIris();
	}

	private void reloadIris() {
		try {
			Iris.reload();
		} catch (IOException e) {
			Iris.logger.error("An error occurred attempting to reload shaders while joining the world!");
			e.printStackTrace();
		}
	}
@Mixin(MinecraftClient.class)
@Environment(EnvType.CLIENT)
public class MixinMinecraftClient {
	@Inject(method = "setWorld", at = @At("RETURN"))
	private void iris$dispose(ClientWorld world, CallbackInfo ci) {
		Iris.getPipelineManager().destroyPipeline();
	}
}
