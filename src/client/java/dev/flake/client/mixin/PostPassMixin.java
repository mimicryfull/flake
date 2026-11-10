package dev.flake.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import dev.flake.client.ShockwaveRenderState;
import java.nio.ByteOrder;
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostPass.class)
abstract class PostPassMixin {
	@Shadow @Final private Map<String, GpuBuffer> customUniforms;

	@Inject(method = "addToFrame", at = @At("HEAD"))
	private void flake$updateShockwave(CallbackInfo info) {
		GpuBuffer buffer = customUniforms.get("ShockwaveConfig");
		if (buffer == null) return;
		if ((buffer.usage() & GpuBuffer.USAGE_MAP_WRITE) == 0 || buffer.size() != ShockwaveRenderState.UNIFORM_BYTES) {
			GpuBuffer replacement = RenderSystem.getDevice().createBuffer(() -> "Flake shockwave uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, ShockwaveRenderState.UNIFORM_BYTES);
			customUniforms.put("ShockwaveConfig", replacement);
			buffer.close();
			buffer = replacement;
		}
		float[] values = ShockwaveRenderState.uniforms();
		try (var mapped = buffer.map(false, true)) {
			var data = mapped.data().order(ByteOrder.nativeOrder());
			for (float value : values) data.putFloat(value);
		}
	}
}
