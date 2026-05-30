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

package com.openhis.health.application.common.pagination;

import com.openhis.whale.core.application.dto.PagedResult;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;

/**
 * PagedResult 构建工具（B.10.11）。
 *
 * <p>提供统一的分页响应构建方法，避免在 DTO/Response 类中编写业务逻辑，
 * 同时保持 Response 类的纯粹性（仅作为类型标记继承 {@link PagedResult}）。</p>
 *
 * <p>分页请求侧对称使用 {@link PageQueryBinder}（含嵌套请求基类 {@link PageQueryBinder.AbstractHealthPageRequest}）与 {@link PagedResultBuilder}（入站 / 出站）。</p>
 */
public final class PagedResultBuilder {

    private PagedResultBuilder() {
        // 工具类禁止实例化
    }

    /**
     * 构建分页响应（完整参数）。
     *
     * <p>B.10.11 规范：pageNo 为 1-based，pageSize 为实际每页条数。</p>
     *
     * @param response   响应对象实例（继承 PagedResult）
     * @param content    当前页数据列表
     * @param totalCount 总记录数
     * @param pageNo     当前页码（1-based）
     * @param pageSize   每页条数
     * @return 构建完成的响应对象
     */
    public static <T, R extends PagedResult<T>> R build(
            R response,
            List<T> content,
            long totalCount,
            int pageNo,
            int pageSize) {
        response.setContent(Objects.requireNonNullElse(content, List.of()));
        response.setTotalCount(totalCount);
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        return response;
    }

    /**
     * 用 Spring Data {@link Page} 的分页元数据 + 已映射的 {@code content} 构建响应。
     *
     * <p>典型场景：{@code Page&lt;ReadModel&gt;} 经循环映射为 {@code List&lt;Dto&gt;}，总条数与页码仍以原 {@code Page} 为准。</p>
     *
     * <p><strong>B.10.11：</strong>{@code page == null} 时语义为「无分页壳」：{@code totalCount=0}、{@code pageNo=1}、{@code pageSize=0}；
     * Spring 页码换算<strong>仅</strong>在 Builder 内完成，禁止在 Mapper/AppService 手写 {@code getNumber() + 1}。</p>
     *
     * @param response 响应对象实例（继承 PagedResult）
     * @param content  当前页业务数据（可与 page.getContent() 类型不同）
     * @param page       Spring Data 分页壳；可为 {@code null}
     * @return 构建完成的响应对象
     */
    public static <T, R extends PagedResult<T>> R build(R response, List<T> content, Page<?> page) {
        if (page == null) {
            return build(response, content, 0L, 1, 0);
        }
        return build(
                response,
                content,
                page.getTotalElements(),
                springZeroBasedNumberToOneBasedPageNo(page.getNumber()),
                page.getSize());
    }

    /**
     * Spring Data {@link Page#getNumber()} 为 0-based；对外 API {@code pageNo} 为 1-based。
     * <p>项目内唯一换算计入口，禁止在其它类重复实现。</p>
     */
    private static int springZeroBasedNumberToOneBasedPageNo(int springZeroBasedPageNumber) {
        return springZeroBasedPageNumber + 1;
    }

    /**
     * 构建分页响应（从 Spring Data Page 转换）。
     *
     * <p>内容与元数据均取自 {@code page}；页码换算见 {@link #build(PagedResult, List, Page)}。</p>
     *
     * @param response 响应对象实例
     * @param page     Spring Data Page 对象；为 {@code null} 时等价于 {@link #buildEmpty}
     * @return 构建完成的响应对象
     */
    public static <T, R extends PagedResult<T>> R buildFromPage(R response, Page<T> page) {
        if (page == null) {
            return buildEmpty(response);
        }
        return build(response, page.getContent(), page);
    }

    /**
     * 构建全量列表响应（不分页）。
     *
     * <p>适用于返回全部数据的场景，默认 pageNo=1, pageSize=totalCount。</p>
     *
     * @param response 响应对象实例
     * @param content  全部数据列表
     * @return 构建完成的响应对象（pageNo=1, pageSize=content.size()）
     */
    public static <T, R extends PagedResult<T>> R buildList(R response, List<T> content) {
        long total;
        if (content != null) {
            total = content.size();
        } else {
            total = 0;
        }
        return build(response, content, total, 1, (int) total);
    }

    /**
     * 构建空列表响应。
     *
     * @param response 响应对象实例
     * @return 构建完成的空响应对象（content=[], totalCount=0, pageNo=1, pageSize=0）
     */
    public static <T, R extends PagedResult<T>> R buildEmpty(R response) {
        return build(response, List.of(), 0L, 1, 0);
    }
}
