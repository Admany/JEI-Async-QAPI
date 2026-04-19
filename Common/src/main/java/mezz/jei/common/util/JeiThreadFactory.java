package mezz.jei.common.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import mezz.jei.common.config.DebugConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Optimized thread pool factory for JEI operations.
 * Provides specialized thread pools for different types of work:
 * - Plugin loading: CPU-intensive, bounded parallelism
 * - Tooltip preparation: I/O and computation, cached threads
 * - Search/filtering: Short-lived tasks, fork-join for parallel streams
 */
public final class JeiThreadFactory {
	private static final Logger LOGGER = LogManager.getLogger();

	// Core thread pool for plugin loading - bounded by CPU cores
	// Use availableProcessors - 1 to leave room for the server thread in single-player
	private static final int PLUGIN_LOADER_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

	private static ExecutorService pluginLoaderExecutor;
	private static ExecutorService tooltipPrepExecutor;
	private static ForkJoinPool searchForkJoinPool;
	private static ScheduledExecutorService scheduledExecutor;

	private JeiThreadFactory() {}

	/**
	 * Get the plugin loader executor.
	 * Optimized for CPU-bound plugin registration work.
	 */
	public static synchronized ExecutorService getPluginLoaderExecutor() {
		if (pluginLoaderExecutor == null || pluginLoaderExecutor.isShutdown()) {
			ThreadPoolExecutor executor = new ThreadPoolExecutor(
				Math.max(1, PLUGIN_LOADER_THREADS / 2),  // Core pool size
				PLUGIN_LOADER_THREADS,                   // Max pool size
				10L, TimeUnit.MILLISECONDS,                   // Keep-alive time
				new LinkedBlockingQueue<>(200),          // Larger queue
				new ThreadFactoryBuilder()
					.setNameFormat("JEI Plugin Loader-%d")
					.setDaemon(true)
					.setUncaughtExceptionHandler((t, e) ->
						LOGGER.error("Uncaught exception in plugin loader thread {}", t.getName(), e))
					.build(),
				new ThreadPoolExecutor.CallerRunsPolicy()  // Backpressure when overloaded
			);
			executor.allowCoreThreadTimeOut(true);
			pluginLoaderExecutor = executor;
		}
		return pluginLoaderExecutor;
	}

	/**
	 * Get the tooltip preparation executor.
	 * Optimized for mixed I/O and computation work.
	 */
	public static synchronized ExecutorService getTooltipPrepExecutor() {
		if (tooltipPrepExecutor == null || tooltipPrepExecutor.isShutdown()) {
			tooltipPrepExecutor = Executors.newCachedThreadPool(
				new ThreadFactoryBuilder()
					.setNameFormat("JEI Tooltip Prep-%d")
					.setDaemon(true)
					.setUncaughtExceptionHandler((t, e) ->
						LOGGER.error("Uncaught exception in tooltip prep thread {}", t.getName(), e))
					.build()
			);
		}
		return tooltipPrepExecutor;
	}

	/**
	 * Get the search fork-join pool.
	 * Optimized for parallel stream operations.
	 */
	public static synchronized ForkJoinPool getSearchForkJoinPool() {
		if (searchForkJoinPool == null || searchForkJoinPool.isShutdown()) {
			int threadCount = DebugConfig.getSearchThreadCount();
			LOGGER.info("Initializing JEI Search ForkJoinPool with {} threads", threadCount);
			searchForkJoinPool = new ForkJoinPool(
				threadCount, // parallelism
				createSearchForkJoinWorkerThreadFactory(), // custom factory for naming
				(t, e) -> LOGGER.error("Uncaught exception in search thread {}", t.getName(), e), // exception handler
				true,  // asyncMode
				0, // corePoolSize: allow all threads to time out
				threadCount, // maximumPoolSize
				0, // minRunnable
				null, // keepAliveAction
				10L, TimeUnit.MILLISECONDS // keepAliveTime
			);
		}
		return searchForkJoinPool;
	}

	/**
	 * Creates a custom ForkJoinWorkerThreadFactory to name threads and ensure they are daemon.
	 */
	private static ForkJoinPool.ForkJoinWorkerThreadFactory createSearchForkJoinWorkerThreadFactory() {
		return new ForkJoinPool.ForkJoinWorkerThreadFactory() {
			private final AtomicInteger threadNumber = new AtomicInteger(1);

			@Override
			public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
				ForkJoinWorkerThread worker = new ForkJoinWorkerThread(pool) {
					@Override
					protected void onTermination(Throwable exception) {
						super.onTermination(exception);
					}
				};
				worker.setName("JEI Search-" + threadNumber.getAndIncrement());
				worker.setDaemon(true); // Ensure it's a daemon thread
				return worker;
			}
		};
	}

	/**
	 * Get the scheduled executor.
	 * For delayed and periodic tasks.
	 */
	public static synchronized ScheduledExecutorService getScheduledExecutor() {
		if (scheduledExecutor == null || scheduledExecutor.isShutdown()) {
			ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
				2,
				new ThreadFactoryBuilder()
					.setNameFormat("JEI Scheduler-%d")
					.setDaemon(true)
					.setUncaughtExceptionHandler((t, e) ->
						LOGGER.error("Uncaught exception in scheduler thread {}", t.getName(), e))
					.build()
			);
			executor.setKeepAliveTime(60L, TimeUnit.SECONDS);
			executor.allowCoreThreadTimeOut(true);
			scheduledExecutor = executor;
		}
		return scheduledExecutor;
	}

	/**
	 * Submit a task to the plugin loader executor and get a CompletableFuture.
	 */
	public static <T> CompletableFuture<T> submitPluginTask(Callable<T> task) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return task.call();
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		}, getPluginLoaderExecutor());
	}

	/**
	 * Submit a runnable task to the plugin loader executor.
	 */
	public static CompletableFuture<Void> submitPluginTask(Runnable task) {
		return CompletableFuture.runAsync(task, getPluginLoaderExecutor());
	}

	/**
	 * Submit a task to the tooltip prep executor.
	 */
	public static <T> CompletableFuture<T> submitTooltipTask(Callable<T> task) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return task.call();
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		}, getTooltipPrepExecutor());
	}

	/**
	 * Execute a task in the fork-join pool for parallel processing.
	 */
	public static <T> T executeInForkJoinPool(Callable<T> task) {
		try {
			return getSearchForkJoinPool().submit(task).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Task interrupted", e);
		} catch (ExecutionException e) {
			throw new RuntimeException("Task failed", e.getCause());
		}
	}

	/**
	 * Shutdown all executors gracefully.
	 */
	public static synchronized void shutdown() {
		LOGGER.info("Shutting down JEI thread pools...");

		if (pluginLoaderExecutor != null) {
			pluginLoaderExecutor.shutdown();
		}
		if (tooltipPrepExecutor != null) {
			tooltipPrepExecutor.shutdown();
		}
		if (searchForkJoinPool != null) {
			searchForkJoinPool.shutdown();
		}
		if (scheduledExecutor != null) {
			scheduledExecutor.shutdown();
		}

		try {
			if (pluginLoaderExecutor != null && !pluginLoaderExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
				pluginLoaderExecutor.shutdownNow();
			}
			if (tooltipPrepExecutor != null && !tooltipPrepExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
				tooltipPrepExecutor.shutdownNow();
			}
			if (searchForkJoinPool != null && !searchForkJoinPool.awaitTermination(2, TimeUnit.SECONDS)) {
				searchForkJoinPool.shutdownNow();
			}
			if (scheduledExecutor != null && !scheduledExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
				scheduledExecutor.shutdownNow();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			if (pluginLoaderExecutor != null) pluginLoaderExecutor.shutdownNow();
			if (tooltipPrepExecutor != null) tooltipPrepExecutor.shutdownNow();
			if (searchForkJoinPool != null) searchForkJoinPool.shutdownNow();
			if (scheduledExecutor != null) scheduledExecutor.shutdownNow();
		}

		pluginLoaderExecutor = null;
		tooltipPrepExecutor = null;
		searchForkJoinPool = null;
		scheduledExecutor = null;

		LOGGER.info("JEI thread pools shut down");
	}
}
