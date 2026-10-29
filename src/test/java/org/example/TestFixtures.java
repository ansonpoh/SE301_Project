package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.example.crop.CsvCropRepository;
import org.example.model.CropProfile;

/** Shared test data and helpers. */
public final class TestFixtures {

    /** Tomato: temp 22-28, humidity 60-80, soil 45-60, light 500-1000, nutrient 40-90. */
    public static final List<String> TOMATO_ROW =
            List.of("Tomato", "22", "28", "60", "80", "45", "60", "500", "1000", "40", "90");

    private TestFixtures() {
    }

    public static CropProfile tomato() {
        return new CsvCropRepository(List.of(TOMATO_ROW)).findByName("Tomato").orElseThrow();
    }

    /** Runs {@code task} on {@code threads} threads released at the same instant, then waits for all. */
    public static void runConcurrently(int threads, Runnable task) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            worker.start();
            workers.add(worker);
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }
    }
}
