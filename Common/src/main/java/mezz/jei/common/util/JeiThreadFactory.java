package mezz.jei.common.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import mezz.jei.common.config.DebugConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;

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
	private static final ExecutorService PLUGIN_LOADER_EXECUTOR = new ThreadPoolExecutor(
		Math.max(1, PLUGIN_LOADER_THREADS / 2),  // Core pool size
		PLUGIN_LOADER_THREADS,                   // Max pool size
		60L, TimeUnit.SECONDS,                   // Keep-alive time
		new LinkedBlockingQueue<>(200),          // Larger queue
		new ThreadFactoryBuilder()
			.setNameFormat("JEI Plugin Loader-%d")
			.setDaemon(true)
			.setUncaughtExceptionHandler((t, e) ->
				LOGGER.error("Uncaught exception in plugin loader thread {}", t.getName(), e))
			.build(),
		new ThreadPoolExecutor.CallerRunsPolicy()  // Backpressure when overloaded
	);

	// Cached thread pool for tooltip and rendering prep work
	private static final ExecutorService TOOLTIP_PREP_EXECUTOR = Executors.newCachedThreadPool(
		new ThreadFactoryBuilder()
			.setNameFormat("JEI Tooltip Prep-%d")
			.setDaemon(true)
			.setUncaughtExceptionHandler((t, e) ->
				LOGGER.error("Uncaught exception in tooltip prep thread {}", t.getName(), e))
			.build()
	);

	// Fork-join pool for parallel stream operations (search, filtering)
	private static ForkJoinPool searchForkJoinPool;

	// Scheduled executor for delayed/background tasks
	private static final ScheduledExecutorService SCHEDULED_EXECUTOR = new ScheduledThreadPoolExecutor(
		2,
		new ThreadFactoryBuilder()
			.setNameFormat("JEI Scheduler-%d")
			.setDaemon(true)
			.setUncaughtExceptionHandler((t, e) ->
				LOGGER.error("Uncaught exception in scheduler thread {}", t.getName(), e))
			.build()
	);

	private JeiThreadFactory() {}

	/**
	 * Get the plugin loader executor.
	 * Optimized for CPU-bound plugin registration work.
	 */
	public static ExecutorService getPluginLoaderExecutor() {
		return PLUGIN_LOADER_EXECUTOR;
	}

	/**
	 * Get the tooltip preparation executor.
	 * Optimized for mixed I/O and computation work.
	 */
	public static ExecutorService getTooltipPrepExecutor() {
		return TOOLTIP_PREP_EXECUTOR;
	}

	/**
	 * Get the search fork-join pool.
	 * Optimized for parallel stream operations.
	 */
	public static synchronized ForkJoinPool getSearchForkJoinPool() {
		if (searchForkJoinPool == null) {
			int threadCount = DebugConfig.getSearchThreadCount();
			LOGGER.info("Initializing JEI Search ForkJoinPool with {} threads", threadCount);
			searchForkJoinPool = new ForkJoinPool(
				threadCount,
				ForkJoinPool.defaultForkJoinWorkerThreadFactory,
				(t, e) -> LOGGER.error("Uncaught exception in search thread {}", t.getName(), e),
				true  // asyncMode
			);
		}
		return searchForkJoinPool;
	}

	/**
	 * Get the scheduled executor.
	 * For delayed and periodic tasks.
	 */
	public static ScheduledExecutorService getScheduledExecutor() {
		return SCHEDULED_EXECUTOR;
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
		}, PLUGIN_LOADER_EXECUTOR);
	}

	/**
	 * Submit a runnable task to the plugin loader executor.
	 */
	public static CompletableFuture<Void> submitPluginTask(Runnable task) {
		return CompletableFuture.runAsync(task, PLUGIN_LOADER_EXECUTOR);
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
		}, TOOLTIP_PREP_EXECUTOR);
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
	public static void shutdown() {
		LOGGER.info("Shutting down JEI thread pools...");

		PLUGIN_LOADER_EXECUTOR.shutdown();
		TOOLTIP_PREP_EXECUTOR.shutdown();
		synchronized (JeiThreadFactory.class) {
			if (searchForkJoinPool != null) {
				searchForkJoinPool.shutdown();
			}
		}
		SCHEDULED_EXECUTOR.shutdown();

		try {
			if (!PLUGIN_LOADER_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
				PLUGIN_LOADER_EXECUTOR.shutdownNow();
			}
			if (!TOOLTIP_PREP_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
				TOOLTIP_PREP_EXECUTOR.shutdownNow();
			}
			synchronized (JeiThreadFactory.class) {
				if (searchForkJoinPool != null && !searchForkJoinPool.awaitTermination(5, TimeUnit.SECONDS)) {
					searchForkJoinPool.shutdownNow();
				}
			}
			if (!SCHEDULED_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
				SCHEDULED_EXECUTOR.shutdownNow();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			PLUGIN_LOADER_EXECUTOR.shutdownNow();
			TOOLTIP_PREP_EXECUTOR.shutdownNow();
			synchronized (JeiThreadFactory.class) {
				if (searchForkJoinPool != null) {
					searchForkJoinPool.shutdownNow();
				}
			}
			SCHEDULED_EXECUTOR.shutdownNow();
		}

		LOGGER.info("JEI thread pools shut down");
	}
}
