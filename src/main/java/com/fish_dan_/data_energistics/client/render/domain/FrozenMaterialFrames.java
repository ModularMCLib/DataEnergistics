package com.fish_dan_.data_energistics.client.render.domain;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/** Render-thread snapshots shared by one domain. Live atlases keep animating outside its draw calls. */
final class FrozenMaterialFrames implements AutoCloseable {

    private final Int2IntOpenHashMap textures = new Int2IntOpenHashMap();

    int texture(int source) {
        if (source == 0) return 0;
        int existing = textures.get(source);
        if (existing != 0) return existing;
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int framebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int scratch = GlStateManager.glGenFramebuffers();
        int copy = TextureUtil.generateTextureId();
        boolean complete = false;
        try {
            GlStateManager._bindTexture(source);
            int min = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            int mag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
            int wrapS = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S);
            int wrapT = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T);
            int max = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, scratch);
            int levels = 0;
            for (int level = 0; level <= max; level++) {
                GlStateManager._bindTexture(source);
                int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
                int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
                if (width == 0 || height == 0) break;
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, source, level);
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Cannot freeze texture " + source);
                GlStateManager._bindTexture(copy);
                GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, 0, 0, width, height, 0);
                levels = level;
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapS);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
            textures.put(source, copy);
            complete = true;
            return copy;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GlStateManager._bindTexture(binding);
            GlStateManager._glDeleteFramebuffers(scratch);
            if (!complete) TextureUtil.releaseTextureId(copy);
        }
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        textures.values().forEach(TextureUtil::releaseTextureId);
        textures.clear();
    }
}
