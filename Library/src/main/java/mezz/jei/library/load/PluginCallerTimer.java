package mezz.jei.library.load;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PluginCallerTimer implements AutoCloseable {
	private final ScheduledExecutorService executor;
	private final Set<PluginCallerTimerRunnable> runnables = Collections.newSetFromMap(new ConcurrentHashMap<>());

	public PluginCallerTimer() {
		this.executor = Executors.newSingleThreadScheduledExecutor();
		this.executor.scheduleAtFixedRate(this::run, 100, 100, TimeUnit.MILLISECONDS);
	}

	private void run() {
		for (PluginCallerTimerRunnable runnable : runnables) {
			runnable.check();
		}
	}

	public PluginCallerTimerRunnable begin(String title, ResourceLocation pluginUid) {
		PluginCallerTimerRunnable runnable = new PluginCallerTimerRunnable(title, pluginUid);
		runnables.add(runnable);
		return runnable;
	}

	public void end(PluginCallerTimerRunnable runnable) {
		if (runnable != null) {
			runnable.stop();
			runnables.remove(runnable);
		}
	}

	@Override
	public void close() {
		this.executor.shutdown();
	}
}
