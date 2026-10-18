/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.domain.event;

/**
 * 读模型投影器对领域事件 {@code eventId} 的单调序判断工具。
 *
 * <p>框架默认 {@link DomainEvent} 的 {@code eventId} 为 UUID 字符串，无法按十进制长整型比较先后；
 * 若将解析失败统一为同一哨兵值再做 {@code <=} 比较，会把第二条及以后事件全部误判为乱序。</p>
 */
public final class ProjectorEventOrderUtils {

    private ProjectorEventOrderUtils() {
    }

    /**
     * 将事件 ID 解析为 {@code long}；{@code null} 或非十进制整数字符串（如 UUID）时返回 {@code -1L}。
     *
     * @param eventId 事件 ID
     * @return {@link Long#parseLong(String)} 的结果；{@code null} 或无法解析（如 UUID）时为 {@code -1L}
     */
    public static long parseLongEventIdOrMinusOne(String eventId) {
        if (eventId == null) {
            return -1L;
        }
        try {
            return Long.parseLong(eventId);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * 当且仅当两个 eventId 均可解析为长整型（解析结果 {@code >= 0}）且 current &lt;= last 时，视为按数字序应跳过（乱序或旧序重复）。
     *
     * <p>任一方为 UUID 等不可解析串时返回 {@code false}，调用方仅应依赖「同一 eventId 字符串」做幂等去重。</p>
     *
     * @param lastEventId    读模型已记录的最后事件 ID
     * @param currentEventId 当前领域事件 ID
     * @return {@code true} 表示应按数字序丢弃当前事件
     */
    public static boolean isNumericEventOutOfOrder(String lastEventId, String currentEventId) {
        long lastId = parseLongEventIdOrMinusOne(lastEventId);
        long currentId = parseLongEventIdOrMinusOne(currentEventId);
        return lastId >= 0L && currentId >= 0L && currentId <= lastId;
    }
}
