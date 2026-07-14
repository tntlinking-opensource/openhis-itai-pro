/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.id;

import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 雪花算法 ID 生成器。
 * <p>64 位结构：1 位符号位 + 41 位时间戳 + 10 位机器 ID + 12 位序列号。</p>
 * <p>JPA 实体通过 {@link SnowflakeId} + {@link SnowflakeIdentifierGenerator} 在 insert 时发号。
 * 非 JPA 场景（JDBC 批量写入、业务单号后缀等）可调用 {@link #nextId()}；
 * 继承 {@link com.openhis.whale.core.domain.AuditedEntityBase} 的实体 insert 前勿手动 {@code setId}。</p>
 */
public final class SnowflakeIdGenerator {

    private static final SnowflakeIdGenerator SHARED = new SnowflakeIdGenerator();

    // 起始时间戳（2024-01-01 00:00:00）
    private static final long START_TIMESTAMP = 1704067200000L;

    // 机器ID占用的位数
    private static final long WORKER_ID_BITS = 5L;
    // 数据中心ID占用的位数
    private static final long DATACENTER_ID_BITS = 5L;
    // 序列号占用的位数
    private static final long SEQUENCE_BITS = 12L;

    // 机器ID最大值
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    // 数据中心ID最大值
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    // 机器ID左移位数
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    // 数据中心ID左移位数
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    // 时间戳左移位数
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    // 序列号掩码
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long workerId;
    private final long datacenterId;
    private final AtomicLong sequence = new AtomicLong(0L);
    private volatile long lastTimestamp = -1L;

    private SnowflakeIdGenerator() {
        this.workerId = getWorkerId();
        this.datacenterId = getDatacenterId();
    }

    /**
     * 生成下一个全局唯一 ID（JPA 与业务代码共用同一发号器）。
     */
    public static long nextId() {
        return SHARED.generateNextId();
    }

    /**
     * 获取机器ID（基于MAC地址）
     */
    private long getWorkerId() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                byte[] mac = networkInterface.getHardwareAddress();
                if (mac != null) {
                    ByteBuffer bb = ByteBuffer.wrap(mac);
                    return Math.abs(bb.getLong()) % (MAX_WORKER_ID + 1);
                }
            }
        } catch (Exception e) {
            // 忽略异常，使用默认值
        }
        return 1L;
    }

    /**
     * 获取数据中心ID（基于进程ID）
     */
    private long getDatacenterId() {
        long pid = ProcessHandle.current().pid();
        return pid % (MAX_DATACENTER_ID + 1);
    }

    private synchronized long generateNextId() {
        long timestamp = System.currentTimeMillis();

        // 时钟回拨检查
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨，拒绝生成ID");
        }

        // 同一毫秒内，序列号递增
        if (timestamp == lastTimestamp) {
            long seq = sequence.incrementAndGet() & SEQUENCE_MASK;
            // 序列号溢出，等待下一毫秒
            if (seq == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 新的毫秒，序列号重置
            sequence.set(0L);
        }

        lastTimestamp = timestamp;

        // 生成ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence.get();
    }

    /**
     * 等待下一毫秒
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
