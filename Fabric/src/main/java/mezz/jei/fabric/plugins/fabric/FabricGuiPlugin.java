package mezz.jei.fabric.plugins.fabric;

import mezz.jei.api.IAsyncCompatiblePlugin;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.ModIds;
import mezz.jei.api.registration.IRuntimeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.fabric.startup.EventRegistration;
import mezz.jei.gui.events.GuiEventHandler;
import mezz.jei.gui.startup.JeiEventHandlers;
import mezz.jei.gui.startup.JeiGuiStarter;
import mezz.jei.gui.startup.ResourceReloadHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@JeiPlugin
public class FabricGuiPlugin implements IModPlugin, IAsyncCompatiblePlugin {
	private static final Logger LOGGER = LogManager.getLogger();
	private static @Nullable IJeiRuntime runtime;
	private static @Nullable ResourceReloadHandler resourceReloadHandler;
	private static @Nullable JeiEventHandlers pendingEventHandlers;

	private final EventRegistration eventRegistration = new EventRegistration();

	@Override
	public ResourceLocation getPluginUid() {
		return ResourceLocation.fromNamespaceAndPath(ModIds.JEI_ID, "fabric_gui");
	}

	@Override
	public void registerRuntime(IRuntimeRegistration registration) {
		JeiEventHandlers eventHandlers = JeiGuiStarter.start(registration);
		resourceReloadHandler = eventHandlers.resourceReloadHandler();

		pendingEventHandlers = eventHandlers;
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;

		if (pendingEventHandlers != null) {
			eventRegistration.setEventHandlers(pendingEventHandlers);

			GuiEventHandler guiEventHandler = pendingEventHandlers.guiEventHandler();
			Screen currentScreen = Minecraft.getInstance().screen;
			if (currentScreen != null) {
				guiEventHandler.onGuiInit(currentScreen);
				guiEventHandler.onGuiOpen(currentScreen);
			}

			pendingEventHandlers = null;
		}
	}

@Override
	public void onRuntimeUnavailable() {
		runtime = null;
		resourceReloadHandler = null;
		pendingEventHandlers = null;
		LOGGER.info("Stopping JEI GUI");
		eventRegistration.clear();
	}

	public static Optional<IJeiRuntime> getRuntime() {
		return Optional.ofNullable(runtime);
	}

	public static Optional<ResourceReloadHandler> getResourceReloadHandler() {
		return Optional.ofNullable(resourceReloadHandler);
	}

	@Override
	public boolean canExecuteAsync() {
		return false;
	}
}
