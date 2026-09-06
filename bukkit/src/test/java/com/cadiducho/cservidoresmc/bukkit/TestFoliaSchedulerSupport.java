package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.scheduler.CancellableTask;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFoliaSchedulerSupport {

    @Test
    void foliaDetectorIsFalseWithoutFoliaClasspath() {
        assertFalse(FoliaDetector.isFoliaServer());
    }

    @Test
    void pluginYmlDeclaresFoliaSupported() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertTrue(in != null, "plugin.yml must be on the test classpath");
            String yaml;
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                yaml = scanner.hasNext() ? scanner.next() : "";
            }
            assertTrue(yaml.contains("folia-supported: true"));
        }
    }

    @Test
    void foliaCancellableTaskCancelUsesReflection() throws Exception {
        FakeScheduledTask fake = new FakeScheduledTask();
        Method cancel = FakeScheduledTask.class.getMethod("cancel");
        Method isCancelled = FakeScheduledTask.class.getMethod("isCancelled");

        CancellableTask task = newFoliaCancellableTask(fake, cancel, isCancelled);
        assertFalse(task.isCancelled());

        task.cancel();
        task.cancel();

        assertTrue(task.isCancelled());
        assertTrue(fake.cancelled.get());
        assertEquals(1, fake.cancelCalls.get());
    }

    @Test
    void foliaCancellableTaskReadsRemoteCancelledState() throws Exception {
        FakeScheduledTask fake = new FakeScheduledTask();
        Method cancel = FakeScheduledTask.class.getMethod("cancel");
        Method isCancelled = FakeScheduledTask.class.getMethod("isCancelled");
        CancellableTask task = newFoliaCancellableTask(fake, cancel, isCancelled);

        fake.cancelled.set(true);

        assertTrue(task.isCancelled());
    }

    private static CancellableTask newFoliaCancellableTask(Object scheduled, Method cancel, Method isCancelled)
            throws Exception {
        Class<?> handleType = Class.forName(
                "com.cadiducho.cservidoresmc.bukkit.BukkitSchedulerAdapter$FoliaCancellableTask");
        Constructor<?> ctor = handleType.getDeclaredConstructor(Object.class, Method.class, Method.class);
        ctor.setAccessible(true);
        return (CancellableTask) ctor.newInstance(scheduled, cancel, isCancelled);
    }

    public static final class FakeScheduledTask {

        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicInteger cancelCalls = new AtomicInteger();

        public void cancel() {
            cancelCalls.incrementAndGet();
            cancelled.set(true);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
