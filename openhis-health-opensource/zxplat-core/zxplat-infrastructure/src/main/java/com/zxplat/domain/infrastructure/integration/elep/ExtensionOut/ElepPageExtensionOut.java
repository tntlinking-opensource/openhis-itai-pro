/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 电子处方分页结果（避免 contracts 依赖 Spring Data {@code Page}）。
 *
 * @param <T> 行类型
 */
@Data
@Accessors(chain = true)
public class ElepPageExtensionOut<T> {

    /** 当前页数据行。 */
    private List<T> content = new ArrayList<>();

    /** 总记录数。 */
    private long total;

    /** 当前页码，从 0 起。 */
    private int page;

    /** 每页条数。 */
    private int pageSize;

    /**
     * 构造空分页结果。
     *
     * @param <T> 行类型
     * @return 无数据的第 0 页
     */
    public static <T> ElepPageExtensionOut<T> empty() {
        return new ElepPageExtensionOut<T>()
                .setContent(Collections.emptyList())
                .setTotal(0)
                .setPage(0)
                .setPageSize(0);
    }
}
