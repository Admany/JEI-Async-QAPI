package mezz.jei.common.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
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

	// Lazy-initialized executors to ensure they use configured values
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
			int threadCount = Math.max(4, Runtime.getRuntime().availableProcessors());
			LOGGER.info("Initializing JEI Plugin Loader Executor with {} threads", threadCount);
			ThreadPoolExecutor executor = new ThreadPoolExecutor(
				threadCount,                   // Core pool size
				threadCount,                   // Max pool size
				60L, TimeUnit.SECONDS,         // Keep-alive time
				new LinkedBlockingQueue<>(200), // Work queue
				new ThreadFactoryBuilder()
					.setNameFormat("JEI Plugin Loader-%d")
					.setDaemon(true)
					.setUncaughtExceptionHandler((t, e) ->
						LOGGER.error("Uncaught exception in plugin loader thread {}", t.getName(), e))
					.build(),
				new ThreadPoolExecutor.CallerRunsPolicy() // Backpressure when overloaded
			);
			executor.allowCoreThreadTimeOut(true); // Allow core threads to time out after startup
			executor.prestartAllCoreThreads();    // Ensure threads are ready immediately
			pluginLoaderExecutor = executor;
		}
		return pluginLoaderExecutor;
	}

	/**
	 * Get the tooltip preparation executor.
	 * Optimized for mixed I/O and computation work.
	 */
	public static synchronized ExecutorService getTooltipPrepExecutor() {
		if (tooltipPrepExecutor == null) {
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
		if (searchForkJoinPool == null) {
			int threadCount = Math.max(4, Runtime.getRuntime().availableProcessors());
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
	public static synchronized ScheduledExecutorService getScheduledExecutor() {
		if (scheduledExecutor == null) {
			int threadCount = Math.max(4, Runtime.getRuntime().availableProcessors());
			LOGGER.info("Initializing JEI Scheduler with {} threads", threadCount);
			scheduledExecutor = new ScheduledThreadPoolExecutor(
				threadCount,
				new ThreadFactoryBuilder()
					.setNameFormat("JEI Scheduler-%d")
					.setDaemon(true)
					.setUncaughtExceptionHandler((t, e) ->
						LOGGER.error("Uncaught exception in scheduler thread {}", t.getName(), e))
					.build()
			);
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

		if (pluginLoaderExecutor != null) pluginLoaderExecutor.shutdown();
		if (tooltipPrepExecutor != null) tooltipPrepExecutor.shutdown();
		if (searchForkJoinPool != null) searchForkJoinPool.shutdown();
		if (scheduledExecutor != null) scheduledExecutor.shutdown();

		try {
			if (pluginLoaderExecutor != null && !pluginLoaderExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
				pluginLoaderExecutor.shutdownNow();
			}
			if (tooltipPrepExecutor != null && !tooltipPrepExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
				tooltipPrepExecutor.shutdownNow();
			}
			if (searchForkJoinPool != null && !searchForkJoinPool.awaitTermination(5, TimeUnit.SECONDS)) {
				searchForkJoinPool.shutdownNow();
			}
			if (scheduledExecutor != null && !scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
				scheduledExecutor.shutdownNow();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			if (pluginLoaderExecutor != null) pluginLoaderExecutor.shutdownNow();
			if (tooltipPrepExecutor != null) tooltipPrepExecutor.shutdownNow();
			if (searchForkJoinPool != null) searchForkJoinPool.shutdownNow();
			if (scheduledExecutor != null) scheduledExecutor.shutdownNow();
		}

		LOGGER.info("JEI thread pools shut down");
	}
}
