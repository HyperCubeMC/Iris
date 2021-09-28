package net.coderbot.iris.mixin.fantastic;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.CompositeState.class)
public interface CompositeStateAccessor {
	@Accessor("transparencyState")
	RenderStateShard.TransparencyStateShard getTransparencyState();
}
