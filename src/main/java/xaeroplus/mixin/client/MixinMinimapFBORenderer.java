package xaeroplus.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.AXaeroMinimap;
import xaero.common.MinimapLogs;
import xaero.common.XaeroMinimapSession;
import xaero.common.graphics.CustomRenderTypes;
import xaero.common.graphics.CustomVertexConsumers;
import xaero.common.graphics.ImprovedFramebuffer;
import xaero.common.graphics.shader.MinimapShaders;
import xaero.common.minimap.MinimapInterface;
import xaero.common.minimap.MinimapProcessor;
import xaero.common.minimap.render.MinimapFBORenderer;
import xaero.common.minimap.render.MinimapRenderer;
import xaero.common.minimap.render.MinimapRendererHelper;
import xaero.common.minimap.waypoints.render.CompassRenderer;
import xaero.common.minimap.waypoints.render.WaypointsGuiRenderer;
import xaero.common.misc.OptimizedMath;
import xaeroplus.Globals;
import xaeroplus.feature.extensions.CustomMinimapFBORenderer;
import xaeroplus.feature.render.ColorHelper;
import xaeroplus.mixin.client.mc.AccessorGameOptions;
import xaeroplus.settings.XaeroPlusSettingRegistry;

@Mixin(value = MinimapFBORenderer.class, remap = false)
public abstract class MixinMinimapFBORenderer extends MinimapRenderer implements CustomMinimapFBORenderer {

    @Shadow
    private ImprovedFramebuffer scalingFramebuffer;
    @Shadow
    private ImprovedFramebuffer rotationFramebuffer;
    @Shadow
    private boolean loadedFBO;

    public MixinMinimapFBORenderer(final AXaeroMinimap modMain, final Minecraft mc, final WaypointsGuiRenderer waypointsGuiRenderer, final MinimapInterface minimapInterface, final CompassRenderer compassRenderer) {
        super(modMain, mc, waypointsGuiRenderer, minimapInterface, compassRenderer);
    }

    @ModifyConstant(method = "loadFrameBuffer", constant = @Constant(intValue = 512))
    public int overrideFrameBufferSize(int size) {
        return Globals.minimapScalingFactor * 512;
    }

    @Override
    public void reloadMapFrameBuffers() {
        if (!XaeroMinimapSession.getCurrentSession().getMinimapProcessor().canUseFrameBuffer()) {
            MinimapLogs.LOGGER.info("FBO mode not supported! Using minimap safe mode.");
        } else {
            if (this.scalingFramebuffer != null)
                this.scalingFramebuffer.destroyBuffers();
            if (this.rotationFramebuffer != null)
                this.rotationFramebuffer.destroyBuffers();
            final int scaledSize = Globals.minimapScalingFactor * 512;
            this.scalingFramebuffer = new ImprovedFramebuffer(scaledSize, scaledSize, false);
            this.rotationFramebuffer = new ImprovedFramebuffer(scaledSize, scaledSize, true);
            this.rotationFramebuffer.setFilterMode(9729);
            this.loadedFBO = this.scalingFramebuffer.frameBufferId != -1 && this.rotationFramebuffer.frameBufferId != -1;
        }
    }

    @ModifyArg(method = "renderChunks", at = @At(
        value = "INVOKE",
        target = "Lxaero/common/minimap/render/MinimapFBORenderer;renderChunksToFBO(Lxaero/common/XaeroMinimapSession;Lnet/minecraft/client/gui/GuiGraphics;Lxaero/common/minimap/MinimapProcessor;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;DDDDIIFFIZZZIDDZZLxaero/common/graphics/CustomVertexConsumers;)V"
    ), index = 10, remap = true)
    public int modifyViewW(final int viewW) {
        return viewW * Globals.minimapScalingFactor;
    }

    @Inject(method = "renderChunksToFBO", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;pose()Lcom/mojang/blaze3d/vertex/PoseStack;"
    ), remap = true)
    public void modifyScaledSize(final XaeroMinimapSession minimapSession, final GuiGraphics guiGraphics, final MinimapProcessor minimap, final Player player, final Entity renderEntity, final double playerX, final double playerZ, final double playerDimDiv, final double mapDimensionScale, final int bufferSize, final int viewW, final float sizeFix, final float partial, final int level, final boolean retryIfError, final boolean useWorldMap, final boolean lockedNorth, final int shape, final double ps, final double pc, final boolean cave, final boolean circle, final CustomVertexConsumers cvc, final CallbackInfo ci,
                            @Share("scaledSize") LocalIntRef scaledSize) {
        scaledSize.set(256 * Globals.minimapScalingFactor);
        Globals.minimapDrawContext = guiGraphics; // storing this for later use in SupportXaeroWorldMap rendering
    }

    @Inject(method = "renderChunksToFBO", at = @At("TAIL"), remap = true)
    public void removeDrawContextRef(final XaeroMinimapSession minimapSession, final GuiGraphics guiGraphics, final MinimapProcessor minimap, final Player player, final Entity renderEntity, final double playerX, final double playerZ, final double playerDimDiv, final double mapDimensionScale, final int bufferSize, final int viewW, final float sizeFix, final float partial, final int level, final boolean retryIfError, final boolean useWorldMap, final boolean lockedNorth, final int shape, final double ps, final double pc, final boolean cave, final boolean circle, final CustomVertexConsumers cvc, final CallbackInfo ci) {
        Globals.minimapDrawContext = null;
    }

    @Redirect(method = "renderChunksToFBO", at = @At(
        value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",
        ordinal = 0
    ), remap = true)
    public void modifyShaderMatrixStackTranslate(final PoseStack instance, final float x, final float y, final float z,
                                                 @Share("scaledSize") LocalIntRef scaledSize) {
        instance.translate(scaledSize.get(), scaledSize.get(), -2000.0F);
    }

    @Redirect(method = "renderChunksToFBO", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"
    ), remap = true)
    public void modifyMMBackgroundFill(final GuiGraphics guiGraphics, final int x1, final int y1, final int x2, final int y2, final int color,
                                       @Share("scaledSize") LocalIntRef scaledSize) {
        if (!XaeroPlusSettingRegistry.transparentMinimapBackground.getValue())
            guiGraphics.fill(-scaledSize.get(), -scaledSize.get(), scaledSize.get(), scaledSize.get(), ColorHelper.getColor(0, 0, 0, 255));
        else
            guiGraphics.fill(-scaledSize.get(), -scaledSize.get(), scaledSize.get(), scaledSize.get(), ColorHelper.getColor(0, 0, 0, 0));
    }

    @ModifyArg(method = "renderChunksToFBO", at = @At(
        value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderSystem;lineWidth(F)V"
    ), remap = true)
    public float modifyChunkGridLineWidth(final float original) {
        return original * Globals.minimapScalingFactor;
    }

    @Inject(method = "renderChunksToFBO", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V",
        ordinal = 0
    ), remap = true)
    public void drawRenderDistanceSquare(final XaeroMinimapSession minimapSession, final GuiGraphics guiGraphics, final MinimapProcessor minimap, final Player player, final Entity renderEntity, final double playerX, final double playerZ, final double playerDimDiv, final double mapDimensionScale, final int bufferSize, final int viewW, final float sizeFix, final float partial, final int level, final boolean retryIfError, final boolean useWorldMap, final boolean lockedNorth, final int shape, final double ps, final double pc, final boolean cave, final boolean circle, final CustomVertexConsumers cvc, final CallbackInfo ci,
                                         @Local(name = "xFloored") int xFloored,
                                         @Local(name = "zFloored") int zFloored,
                                         @Local(name = "renderTypeBuffers") MultiBufferSource.BufferSource renderTypeBuffers,
                                         @Local(name = "matrixStack") PoseStack matrixStack
    ) {
        final boolean isDimensionSwitched = Globals.getCurrentDimensionId() != Minecraft.getInstance().level.dimension();
        if (XaeroPlusSettingRegistry.showRenderDistanceSetting.getValue() && !isDimensionSwitched) {
            double actualPlayerX = minimap.getEntityRadar().getEntityX(mc.player, partial);
            double actualPlayerZ = minimap.getEntityRadar().getEntityZ(mc.player, partial);
            int actualXFloored = OptimizedMath.myFloor(actualPlayerX);
            int actualZFloored = OptimizedMath.myFloor(actualPlayerZ);
            final int viewDistance = ((AccessorGameOptions) mc.options).getServerRenderDistance();
            int width = viewDistance * 2 + 1;
            // origin of the chunk we are standing in
            final int middleChunkX = -(actualXFloored & 15);
            final int middleChunkZ = -(actualZFloored & 15);
            int distanceFlooredX = actualXFloored - xFloored;
            int distanceFlooredZ = actualZFloored - zFloored;

            final int x0 = distanceFlooredX + middleChunkX - (width / 2) * 16;
            final int z0 = distanceFlooredZ + middleChunkZ - (width / 2) * 16;
            final int x1 = x0 + width * 16;
            final int z1 = z0 + width * 16;
            VertexConsumer lineBufferBuilder = renderTypeBuffers.getBuffer(CustomRenderTypes.MAP_LINES);
            MinimapShaders.FRAMEBUFFER_LINES.setFrameSize((float) scalingFramebuffer.viewWidth, (float) scalingFramebuffer.viewHeight);
            RenderSystem.lineWidth((float) modMain.getSettings().chunkGridLineWidth * Globals.minimapScalingFactor);
            PoseStack.Pose matrices = matrixStack.last();

            helper.addColoredLineToExistingBuffer(
                matrices,
                lineBufferBuilder,
                x0,
                z0,
                x1,
                z0,
                1.0f,
                1.0f,
                0.0f,
                0.8f
            );
            helper.addColoredLineToExistingBuffer(
                matrices,
                lineBufferBuilder,
                x1,
                z0,
                x1,
                z1,
                1.0f,
                1.0f,
                0.0f,
                0.8f
            );
            helper.addColoredLineToExistingBuffer(
                matrices,
                lineBufferBuilder,
                x1,
                z1,
                x0,
                z1,
                1.0f,
                1.0f,
                0.0f,
                0.8f
            );
            helper.addColoredLineToExistingBuffer(
                matrices,
                lineBufferBuilder,
                x0,
                z0,
                x0,
                z1,
                1.0f,
                1.0f,
                0.0f,
                0.8f
            );
        }
    }

    @Redirect(method = "renderChunksToFBO", at = @At(
        value = "INVOKE",
        target = "Lxaero/common/minimap/render/MinimapRendererHelper;drawMyTexturedModalRect(Lcom/mojang/blaze3d/vertex/PoseStack;FFIIFFFF)V"
    ), remap = true)
    public void redirectModelViewDraw(final MinimapRendererHelper instance, final PoseStack matrixStack, final float x, final float y, final int textureX, final int textureY, final float width, final float height, final float theight, final float factor,
                                      @Share("scaledSize") LocalIntRef scaledSize) {
        final float scaledSizeM = Globals.minimapScalingFactor * 512f;
        this.helper.drawMyTexturedModalRect(matrixStack, -scaledSize.get(), -scaledSize.get(), 0, 0, scaledSizeM, scaledSizeM, scaledSizeM, scaledSizeM);
    }
}
