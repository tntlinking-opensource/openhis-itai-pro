/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 领域分页结果（对齐 AS Response 的 total + records）。
 *
 * @param <T> 行类型
 */
@Data
public class NoticePageResult<T> {

    /** 总条数。 */
    private long total;

    /** 当前页记录。 */
    private List<T> records = new ArrayList<>();

    /**
     * 工厂方法。
     *
     * @param total   总条数
     * @param records 当前页
     * @param <T>     行类型
     * @return 分页结果
     */
    public static <T> NoticePageResult<T> of(long total, List<T> records) {
        NoticePageResult<T> result = new NoticePageResult<>();
        result.setTotal(total);
        if (records != null) {
            result.setRecords(records);
        }
        return result;
    }
}
