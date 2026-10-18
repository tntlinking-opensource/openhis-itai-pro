/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.pagination;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * 兼容别名：真身字段在 {@link com.zxplat.framework.core.application.dto.PagedResult}。
 *
 * <p>必须<strong>直接</strong>继承 dto 真身，禁止再套一层空的 {@code paging.PagedResult}：
 * 空壳子类在 IDEA/Maven 增量编译时会被先删后编，启动窗口期出现
 * {@code NoClassDefFoundError: PagedResult}/{@code AbstractHealthPageRequest}。</p>
 *
 * @param <T> 分页行类型
 * @deprecated 新代码请改 import {@code com.zxplat.framework.core.application.dto.PagedResult}
 *     或 {@code com.zxplat.framework.core.application.paging.PagedResult}
 */
@Deprecated
public class PagedResult<T> extends com.zxplat.framework.core.application.dto.PagedResult<T> {

    /**
     * 与 dto 真身同口径的工厂方法，保证本类被引用时方法解析落在本 Class，不依赖空壳中间层。
     *
     * @param items      当前页
     * @param totalCount 总条数
     * @param pageNo     1-based 页码
     * @param pageSize   页大小
     * @param <T>        行类型
     * @return 分页结果
     */
    public static <T> PagedResult<T> of(List<T> items, long totalCount, int pageNo, int pageSize) {
        PagedResult<T> result = new PagedResult<>();
        result.setContent(items);
        result.setTotalCount(totalCount);
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        return result;
    }

    /**
     * 从 Spring Data {@link Page} 构建（页码转为 1-based）。
     *
     * @param page Spring 分页
     * @param <T>  行类型
     * @return 分页结果
     */
    public static <T> PagedResult<T> fromPage(Page<T> page) {
        PagedResult<T> result = new PagedResult<>();
        result.setContent(page.getContent());
        result.setTotalCount(page.getTotalElements());
        result.setPageNo(page.getNumber() + 1);
        result.setPageSize(page.getSize());
        return result;
    }
}
