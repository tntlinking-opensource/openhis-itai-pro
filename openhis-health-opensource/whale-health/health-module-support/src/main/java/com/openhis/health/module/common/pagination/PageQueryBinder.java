/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.common.pagination;

import com.openhis.health.domain.share.query.AbstractPageQuery;

/**
 * 入站分页工具（B.10.11）：将 HTTP 分页窗口绑定到 {@link AbstractPageQuery}，与 {@link PagedResultBuilder} 对称
 * （{@code PageQueryBinder}：请求 → 查询；{@link PagedResultBuilder}：{@code Page} → 响应）。
 *
 * <p>对外 REST 分页请求体应继承嵌套类型 {@link PageQueryBinder.AbstractHealthPageRequest}；{@link #bind} / {@link #copyToPlainQuery}
 * 仅做字段搬运，不包含门控或业务谓词。</p>
 */
public final class PageQueryBinder {

    private PageQueryBinder() {
        // 工具类禁止实例化
    }

    /**
     * 将 {@code source} 的分页窗口（页码、页大小、关键字、时段、排序、where）写入已存在的 {@code target}。
     *
     * @param source 可为 {@code null}（不修改 target）
     * @param target 可为 {@code null}（直接返回）
     */
    public static void bind(AbstractPageQuery source, AbstractPageQuery target) {
        if (source == null || target == null) {
            return;
        }
        target.setPageNo(source.getPageNo());
        target.setPageSize(source.getPageSize());
        target.setKeyword(source.getKeyword());
        target.setStartDate(source.getStartDate());
        target.setEndDate(source.getEndDate());
        target.setSortBy(source.getSortBy());
        target.setSortDirection(source.getSortDirection());
        target.setWhere(source.getWhere());
    }

    /**
     * 复印分页窗口到新的「纯」{@link AbstractPageQuery} 实例，供只接受领域 plain 类型的读模型入口使用。
     *
     * @param source 可为 {@code null}（返回空壳实例）
     * @return 新实例；字段与 {@code source} 一致（含 {@code null} 的显式拷贝）
     */
    public static AbstractPageQuery copyToPlainQuery(AbstractPageQuery source) {
        AbstractPageQuery target = new AbstractPageQuery();
        bind(source, target);
        return target;
    }

    /**
     * {@code health-application} 对外 REST 分页请求体基类（B.10.11）。
     *
     * <p>领域与读模型查询入参仍以 {@link AbstractPageQuery} 为语义真源（含 {@link AbstractPageQuery#springPageIndex()} 等）；
     * 凡暴露给前端的列表请求 DTO，应继承本类型而非直接继承 {@link AbstractPageQuery}。</p>
     */
    public static abstract class AbstractHealthPageRequest extends AbstractPageQuery {

        private static final long serialVersionUID = 1L;
    }
}
