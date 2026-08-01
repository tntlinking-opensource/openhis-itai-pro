/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.tracenumber.support;

import com.openhis.health.domain.custom.tracenumber.entity.TraceNumberRecord;
import com.openhis.health.domain.share.enums.TraceDirection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 子表库位轧差净量计算（DM-SDD §15 · BR-TNR-013）。
 *
 * <p>读侧直接 SUM 落库 {@code split_quantity}，禁止跨聚合查药品目录（B.6.4）。</p>
 */
public final class TraceNumberLocationNetSupport {

    private TraceNumberLocationNetSupport() {
    }

    /**
     * 按追溯码聚合指定库位下净拆零量。
     *
     * @param records 库位记录（含 IN/OUT 全类别）
     * @return traceNo → 净拆零量
     */
    public static Map<String, BigDecimal> aggregateNetSplitByTraceNo(List<TraceNumberRecord> records) {
        Map<String, BigDecimal> netByTraceNo = new HashMap<>();
        if (records == null || records.isEmpty()) {
            return netByTraceNo;
        }
        for (TraceNumberRecord record : records) {
            if (record == null || Boolean.TRUE.equals(record.getIsDeleted())) {
                continue;
            }
            BigDecimal splitQty = record.getSplitQuantity();
            if (splitQty == null) {
                continue;
            }
            BigDecimal current = netByTraceNo.get(record.getTraceNo());
            if (current == null) {
                current = BigDecimal.ZERO;
            }
            if (TraceDirection.IN.getCode().equals(record.getRecordDirectionEnum())) {
                netByTraceNo.put(record.getTraceNo(), current.add(splitQty));
            } else {
                netByTraceNo.put(record.getTraceNo(), current.subtract(splitQty));
            }
        }
        return netByTraceNo;
    }

    /**
     * 按追溯码解析当前仍在库的库位（各库位轧差净量 &gt; 0）。
     *
     * @param records 子表流水（可跨多库位）
     * @return traceNo → 当前所在库位 id 列表（保持首次出现顺序）
     */
    public static Map<String, List<Long>> resolveCurrentLocationIdsByTraceNo(List<TraceNumberRecord> records) {
        Map<String, Map<Long, BigDecimal>> netByTraceNoAndLocation = new HashMap<>();
        if (records == null || records.isEmpty()) {
            return Map.of();
        }
        for (TraceNumberRecord record : records) {
            if (record == null || Boolean.TRUE.equals(record.getIsDeleted())) {
                continue;
            }
            if (record.getLocationId() == null || record.getTraceNo() == null) {
                continue;
            }
            BigDecimal splitQty = record.getSplitQuantity();
            if (splitQty == null) {
                continue;
            }
            Map<Long, BigDecimal> netByLocation = netByTraceNoAndLocation.get(record.getTraceNo());
            if (netByLocation == null) {
                netByLocation = new LinkedHashMap<>();
                netByTraceNoAndLocation.put(record.getTraceNo(), netByLocation);
            }
            BigDecimal current = netByLocation.get(record.getLocationId());
            if (current == null) {
                current = BigDecimal.ZERO;
            }
            if (TraceDirection.IN.getCode().equals(record.getRecordDirectionEnum())) {
                netByLocation.put(record.getLocationId(), current.add(splitQty));
            } else {
                netByLocation.put(record.getLocationId(), current.subtract(splitQty));
            }
        }
        Map<String, List<Long>> result = new HashMap<>();
        for (Map.Entry<String, Map<Long, BigDecimal>> entry : netByTraceNoAndLocation.entrySet()) {
            List<Long> locationIds = new ArrayList<>();
            for (Map.Entry<Long, BigDecimal> locationEntry : entry.getValue().entrySet()) {
                BigDecimal net = locationEntry.getValue();
                if (net != null && net.compareTo(BigDecimal.ZERO) > 0) {
                    locationIds.add(locationEntry.getKey());
                }
            }
            if (!locationIds.isEmpty()) {
                result.put(entry.getKey(), locationIds);
            }
        }
        return result;
    }
}
