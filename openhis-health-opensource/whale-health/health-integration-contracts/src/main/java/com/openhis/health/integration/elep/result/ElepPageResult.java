/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.elep.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 分页结果（避免 contracts 依赖 Spring Data Page）。 */
@Data
@Accessors(chain = true)
public class ElepPageResult<T> {

    private List<T> content = new ArrayList<>();
    private long total;
    private int page;
    private int pageSize;

    public static <T> ElepPageResult<T> empty() {
        return new ElepPageResult<T>()
                .setContent(Collections.emptyList())
                .setTotal(0)
                .setPage(0)
                .setPageSize(0);
    }
}
