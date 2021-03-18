package fr.aym.acsguis.api;

import fr.aym.acsguis.component.panel.GuiFrame;
import fr.aym.acsguis.cssengine.CssGuisManager;
import fr.aym.acsguis.cssengine.CssHudHandler;
import fr.aym.acsguis.event.CssReloadEvent;
import fr.aym.acsguis.utils.CssReloadOrigin;
import fr.aym.acslib.ACsPlatform;
import fr.aym.acslib.services.ACsRegisteredService;
import fr.aym.acslib.services.ACsService;
import fr.aym.acslib.services.thrload.ModLoadingSteps;
import fr.aym.acslib.services.thrload.ThreadedLoadingService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLStateEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.lang.model.type.ErrorType;
import java.util.concurrent.Callable;

@SideOnly(Side.CLIENT)
@ACsRegisteredService(name = ACsGuiApi.RES_LOC_ID, version = ACsGuiApi.VERSION, sides = Side.CLIENT)
public class ACsGuiApi implements ACsService
{
    public static final String RES_LOC_ID = "acsguis";
    public static final String VERSION = "1.0.1";
    public static final Logger log = LogManager.getLogger("ACsGuis");

    private static final CssGuisManager manager = new CssGuisManager();

    @Override
    public String getName() {
        return RES_LOC_ID;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public void initService() {
        log.info("Initializing ACsGuis API by Aym', version "+VERSION);
        MinecraftForge.EVENT_BUS.register(ACsGuiApi.getHudHandler());
    }

    @Override
    public void onFMLStateEvent(FMLStateEvent event) {
        if(event instanceof FMLInitializationEvent) {
            ((SimpleReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new CssGuisManager());
        }
    }

    /**
     * Register a css style sheet to (re)load when resources packs are loaded <br>
     *     Register all the sheets that you are using here, before api initialization
     *
     * @param location The style sheet to load
     */
    public static void registerStyleSheetToPreload(ResourceLocation location) {
        manager.registerStyleSheetToPreload(location);
    }

    /**
     * Loads a GuiFrame in another thread, then shows it <br>
     *     Note : the css fonts are loaded in the client thread (needs open gl)
     *
     * @param guiName The gui name, used for log messages
     * @param guiInstance A function returning the gui, called by the external thread
     */
    public static void asyncLoadThenShowGui(String guiName, Callable<GuiFrame> guiInstance) {
        manager.asyncLoadThenShowGui(guiName, guiInstance);
    }

    /**
     * Loads a GuiFrame in another thread, then shows it on the HUD <br>
     *     Note : the css fonts are loaded in the client thread (needs open gl)
     *
     * @param guiName The gui name, used for log messages
     * @param guiInstance A function returning the gui, called by the external thread
     * @see CssHudHandler
     */
    public static void asyncLoadThenShowHudGui(String guiName, Callable<GuiFrame> guiInstance) {
        manager.asyncLoadThenShowHudGui(guiName, guiInstance);
    }

    /**
     * Forces reload of all styles and fonts
     * @param frame If not null, will handle error gui
     */
    public static void reloadCssStyles(@Nullable GuiFrame frame) {
        CssReloadEvent.Pre event = new CssReloadEvent.Pre(frame != null ? new CssReloadOrigin.HotCssReloadOrigin(manager, frame) : new CssReloadOrigin(manager, false));
        if(MinecraftForge.EVENT_BUS.post(event)) return;

        ACsPlatform.<ThreadedLoadingService>provideService("ThrLoad").addTask(ModLoadingSteps.FINISH_LOAD, "css_load", () -> {
            try {
                event.getReloadOrigin().loadStyles();
            } catch (Exception e) { //This should not happen with our reload origin
                throw new RuntimeException("Fatal error while loading css sheets", e);
            }
            long time = System.currentTimeMillis();
            while (Minecraft.getMinecraft().fontRenderer == null) { //Don't listen idea, it can be null because we do this in pre initialization
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            log.debug("Font renderer wait took " + (System.currentTimeMillis() - time) + " ms");
        }, () -> {
            event.getReloadOrigin().loadFonts();
            event.getReloadOrigin().postLoad();
        });
    }

    public static CssHudHandler getHudHandler() {
        return manager.getHud();
    }
}
