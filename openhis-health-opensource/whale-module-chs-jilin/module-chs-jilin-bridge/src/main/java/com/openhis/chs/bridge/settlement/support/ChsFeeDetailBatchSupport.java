/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.settlement.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 2204/2301 {@code feedetail} 分批工具。
 *
 * <p>长春等地平台 schema 限制单次明细条数（报错：
 * {@code expected maximum item count: 100}）。</p>
 */
public final class ChsFeeDetailBatchSupport {

    /** 平台 feedetail 单次最大行数（长春市校验上限）。 */
    public static final int PLATFORM_MAX_FEEDTAIL_ROWS = 100;

    private ChsFeeDetailBatchSupport() {
    }

    /**
     * 按固定大小切分列表（末批可不足 size）。
     *
     * @param source 源列表（可空）
     * @param size   每批大小；非法时按 {@link #PLATFORM_MAX_FEEDTAIL_ROWS}
     * @param <T>    元素类型
     * @return 分批后的只读视图列表；源空则空列表
     */
    public static <T> List<List<T>> partition(List<T> source, int size) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        int batchSize = size > 0 ? size : PLATFORM_MAX_FEEDTAIL_ROWS;
        if (source.size() <= batchSize) {
            return List.of(source);
        }
        List<List<T>> batches = new ArrayList<>((source.size() + batchSize - 1) / batchSize);
        for (int from = 0; from < source.size(); from += batchSize) {
            int to = Math.min(from + batchSize, source.size());
            batches.add(Collections.unmodifiableList(source.subList(from, to)));
        }
        return batches;
    }
}
