package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.example.model.SensorType;
import org.example.sensor.SensorSource;

/** Helpers shared by the tests. */
public final class TestFixtures {

    private TestFixtures() {
    }

    /** Every reading is in range for every scheduled crop except temperature, which is too hot for all of them. */
    public static final Map<SensorType, Double> HOT_CHAMBER = Map.of(
            SensorType.TEMPERATURE, 35.0,
            SensorType.HUMIDITY, 75.0,
            SensorType.SOIL, 50.0,
            SensorType.LIGHT, 700.0,
            SensorType.NUTRIENT, 60.0);

    public static SensorSource fixed(Map<SensorType, Double> readings) {
        return readings::get;
    }

    /**
     * Runs the task on several threads at once. A latch releases them together
     * so they really overlap; any exception thrown by a thread fails the test.
     */
    public static void runConcurrently(int threads, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> running = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                running.add(pool.submit(() -> {
                    start.await();
                    task.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : running) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    throw new AssertionError("worker thread failed", e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
