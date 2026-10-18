/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.concurrency.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * SYS-IMP-004 并发集成测试辅助：双线程/多线程同时起跑，减少单测中的启动时差。
 *
 * <p>用法见 {@link com.zxplat.application.inpatient.concurrency.SysImp004AtdConcurrencyIntegrationTest}。</p>
 */
public final class ConcurrencyTestHelper {

    private static final long DEFAULT_AWAIT_SECONDS = 30;

    private ConcurrencyTestHelper() {
    }

    /**
     * 并发执行 {@code threadCount} 次 {@code task}，全部线程在起跑闸门释放后同时开始。
     *
     * <p>调用方在返回后应 {@code startGate.countDown()}，再 {@link #awaitAll(List, long, TimeUnit)}。</p>
     *
     * @param threadCount 并发度（≥ 1）
     * @param task        每个线程执行体
     * @return 起跑闸门与各线程 Future（顺序与提交一致）
     */
    public static <T> ConcurrentRun<T> startConcurrently(int threadCount, Callable<T> task)
        throws InterruptedException {
        if (threadCount < 1) {
            throw new IllegalArgumentException("threadCount must be >= 1");
        }
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                if (!startGate.await(DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("start gate not released within timeout");
                }
                return task.call();
            }));
        }
        if (!ready.await(DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            throw new IllegalStateException("not all workers reached start gate");
        }
        return new ConcurrentRun<>(executor, startGate, futures);
    }

    /**
     * 释放起跑闸门并等待全部 Future 完成。
     */
    public static <T> List<T> releaseAndAwaitAll(ConcurrentRun<T> run, long timeout, TimeUnit unit)
        throws Exception {
        run.startGate().countDown();
        List<T> results = awaitAll(run.futures(), timeout, unit);
        run.executor().shutdown();
        if (!run.executor().awaitTermination(timeout, unit)) {
            run.executor().shutdownNow();
        }
        return results;
    }

    /**
     * 收集并发结果，阻塞至全部完成或超时。
     */
    public static <T> List<T> awaitAll(List<Future<T>> futures, long timeout, TimeUnit unit)
        throws Exception {
        List<T> results = new ArrayList<>(futures.size());
        for (Future<T> future : futures) {
            results.add(future.get(timeout, unit));
        }
        return results;
    }

    /**
     * 一次并发运行的句柄。
     *
     * @param executor  线程池（由 {@link #releaseAndAwaitAll} 关闭）
     * @param startGate   起跑闸门
     * @param futures     任务 Future 列表
     */
    public record ConcurrentRun<T>(
        ExecutorService executor,
        CountDownLatch startGate,
        List<Future<T>> futures) {
    }
}
