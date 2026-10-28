package com.atom.chat.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.GlCommandEncoder;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Drops the encoder's cached program/pipeline after raw GL work. */
@Mixin(GlCommandEncoder.class)
public interface GlCommandEncoderAccessor {
    @Accessor("currentPipeline")
    void atomchat$setCurrentPipeline(RenderPipeline pipeline);

    @Accessor("currentProgram")
    void atomchat$setCurrentProgram(ShaderProgram program);
}
