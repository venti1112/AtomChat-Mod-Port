package com.atom.chat.mixin;

import com.atom.chat.render.RenderTargetOverride;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.5 resolves the framebuffer for every layer draw through
 * {@code RenderPhase.Target#get()}. While an override is pushed, vanilla item
 * models render into the offscreen icon framebuffer instead of the main target.
 */
@Mixin(RenderPhase.Target.class)
public abstract class RenderPhaseTargetMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void atomchat$useOverride(CallbackInfoReturnable<Framebuffer> cir) {
        Framebuffer override = RenderTargetOverride.current();
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
