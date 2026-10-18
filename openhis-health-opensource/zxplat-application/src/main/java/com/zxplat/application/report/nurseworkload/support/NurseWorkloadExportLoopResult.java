/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.nurseworkload.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 护士工作量导出循环拉取结果（IF-RPT-NWL-06 · NWL-SDD-R-005）。
 *
 * @param <T> 明细行 DTO 类型
 */
public class NurseWorkloadExportLoopResult<T> {

    /** 聚合后的明细行。 */
    private final List<T> rows;

    /** 是否因触顶 {@code EXPORT_MAX_ROWS} 而截断（§6.3 NFR）。 */
    private final boolean truncated;

    /**
     * 构造导出循环结果。
     *
     * @param rows      明细行列表
     * @param truncated 是否截断
     */
    public NurseWorkloadExportLoopResult(List<T> rows, boolean truncated) {
        this.rows = rows;
        this.truncated = truncated;
    }

    /**
     * 返回聚合明细行（非 null）。
     *
     * @return 明细行列表
     */
    public List<T> getRows() {
        return this.rows;
    }

    /**
     * 是否触顶截断。
     *
     * @return {@code true} 表示未拉取完所有命中行
     */
    public boolean isTruncated() {
        return this.truncated;
    }

    /**
     * 空结果快捷构造。
     *
     * @param <T> 行类型
     * @return 空列表且未截断
     */
    public static <T> NurseWorkloadExportLoopResult<T> empty() {
        return new NurseWorkloadExportLoopResult<>(new ArrayList<>(), false);
    }
}
