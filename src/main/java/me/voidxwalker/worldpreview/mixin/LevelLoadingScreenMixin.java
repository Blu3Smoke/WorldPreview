package me.voidxwalker.worldpreview.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import me.voidxwalker.worldpreview.PreviewRenderer;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.Matrix4f;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.apache.logging.log4j.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.util.Iterator;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {

    private boolean drawingPreview=false;

    private boolean showMenu;

    protected LevelLoadingScreenMixin(Text title) {
        super(title);
    }
    @Redirect(method = "render",at = @At(value = "INVOKE",target = "Lnet/minecraft/client/gui/screen/LevelLoadingScreen;renderBackground()V"))
    public void stopBackgroundRender(LevelLoadingScreen instance){
        if(!drawingPreview){
            instance.renderBackground();
        }
    }
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 2)
    public int moveLoadingScreen(int i){
        if(!drawingPreview){
            return i;
        }
        return getChunkMapPos().x;
    }
    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 3)
    public int moveLoadingScreen2(int i){
        if(!drawingPreview){
            return i;
        }
        return getChunkMapPos().y;
    }
    @Inject(method = "render",at=@At("HEAD"),cancellable = true)
    public void render(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if(WorldPreview.stop){
            drawingPreview=false;
        }
        if(WorldPreview.world!=null&& WorldPreview.clientWord!=null&&!WorldPreview.stop) {
            if(WorldPreview.worldRenderer==null){
                WorldPreview.worldRenderer=new PreviewRenderer(MinecraftClient.getInstance(), new BufferBuilderStorage());
            }
            if(WorldPreview.worldRenderer.world==null&& WorldPreview.player.calculatedSpawn){
                WorldPreview.worldRenderer.loadWorld(WorldPreview.clientWord);
                WorldPreview.showMenu=true;
                this.showMenu=true;
                this.initWidgets();
            }
            if (WorldPreview.worldRenderer.world!=null) {
                drawingPreview=true;
                if(this.showMenu!= WorldPreview.showMenu){
                    if(!WorldPreview.showMenu){
                        this.children.clear();
                    }
                    else {
                        this.initWidgets();

                    }
                    this.showMenu= WorldPreview.showMenu;
                }
                MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().update(0);
                if (WorldPreview.camera == null) {
                    WorldPreview.player.refreshPositionAndAngles(WorldPreview.player.getX(), WorldPreview.player.getY() + 1.5, WorldPreview.player.getZ(), 0.0F, 0.0F);
                    WorldPreview.camera = new Camera();
                    WorldPreview.camera.update(WorldPreview.world, WorldPreview.player, false, false, 0.2F);
                    WorldPreview.player.refreshPositionAndAngles(WorldPreview.player.getX(), WorldPreview.player.getY() - 1.5, WorldPreview.player.getZ(), 0.0F, 0.0F);
                    WorldPreview.inPreview=true;
                    WorldPreview.log(Level.INFO,"Starting Preview");
                }
                MatrixStack matrixStack = new MatrixStack();
                matrixStack.peek().getModel().multiply(this.getBasicProjectionMatrix());
                Matrix4f matrix4f = matrixStack.peek().getModel();
                RenderSystem.matrixMode(5889);
                RenderSystem.loadIdentity();
                RenderSystem.multMatrix(matrix4f);
                RenderSystem.matrixMode(5888);
                MatrixStack m = new MatrixStack();
                m.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(WorldPreview.camera.getPitch()));
                m.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(WorldPreview.camera.getYaw() + 180.0F));
                WorldPreview.worldRenderer.render(m, 0.2F, 1000000, false, WorldPreview.camera, MinecraftClient.getInstance().gameRenderer, MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager(), matrix4f);
                WorldPreview.worldRenderer.ticks++;
                Window window = this.minecraft.getWindow();
                RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
                RenderSystem.matrixMode(5889);
                RenderSystem.loadIdentity();
                RenderSystem.ortho(0.0D, (double) window.getFramebufferWidth() / window.getScaleFactor(), (double) window.getFramebufferHeight() / window.getScaleFactor(), 0.0D, 1000.0D, 3000.0D);
                RenderSystem.matrixMode(5888);
                RenderSystem.loadIdentity();
                RenderSystem.translatef(0.0F, 0.0F, -2000.0F);
                DiffuseLighting.enableGuiDepthLighting();
                this.renderPauseMenu(mouseX,mouseY,delta);

            }
        }
    }

    private void renderPauseMenu(int mouseX, int mouseY, float delta){
        if(WorldPreview.showMenu){
            Iterator<AbstractButtonWidget> iterator =this.buttons.listIterator();
            while(iterator.hasNext()){
                iterator.next().render(mouseX,mouseY,delta);
            }
        }
        else {
            this.drawCenteredString( minecraft.textRenderer, new TranslatableText("menu.paused").getString(), this.width / 2, 10, 16777215);
        }
    }


    private Point getChunkMapPos(){
        switch (WorldPreview.chunkMapPos){
            case 1:
                return new Point(this.width -45,this.height -75);
            case 2:
                return new Point(this.width -45,105);
            case 3:
                return new Point(45,105);
            default:
                return new Point(45,this.height -75);
        }
    }

    public Matrix4f getBasicProjectionMatrix() {
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.peek().getModel().loadIdentity();
        matrixStack.peek().getModel().multiply(Matrix4f.viewboxMatrix(minecraft.options.fov, (float)this.minecraft.getWindow().getFramebufferWidth() / (float)this.minecraft.getWindow().getFramebufferHeight(), 0.05F, this.minecraft.options.viewDistance*16 * 4.0F));
        return matrixStack.peek().getModel();
    }

    private void initWidgets(){
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 24 - 16, 204, 20, new TranslatableText("menu.returnToGame").getString(), (ignored) -> {}));
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 48 - 16, 98, 20, new TranslatableText("gui.advancements").getString(), (ignored) -> {}));
        this.addButton(new ButtonWidget(this.width / 2 + 4, this.height / 4 + 48 - 16, 98, 20, new TranslatableText("gui.stats").getString(), (ignored) -> {}));

        this .addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 72 - 16, 98, 20, new TranslatableText("menu.sendFeedback").getString(), (ignored) -> {}));
        this.addButton(new ButtonWidget(this.width / 2 + 4, this.height / 4 + 72 - 16, 98, 20, new TranslatableText("menu.reportBugs").getString(), (ignored) -> {}));
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 96 - 16, 98, 20, new TranslatableText("menu.options").getString(), (ignored) -> {}));
        this.addButton(new ButtonWidget(this.width / 2 + 4, this.height / 4 + 96 - 16, 98, 20, new TranslatableText("menu.shareToLan").getString(), (ignored) -> {}));
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 120 - 16, 204, 20, new TranslatableText("menu.returnToMenu").getString(), (buttonWidgetX) -> {
                WorldPreview.kill = -1;
                buttonWidgetX.active = false;
        }));
    }

    public void resize(MinecraftClient client, int width, int height) {
        this.init(client, width, height);
        this.initWidgets();
    }
}
