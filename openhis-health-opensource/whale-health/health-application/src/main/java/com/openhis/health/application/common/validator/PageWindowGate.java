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

package com.openhis.health.application.common.validator;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 分页窗口入站门控（B.10.11 / B.6.1 {@code common.validator}）。
 *
 * <p>仅校验 {@code pageNo}/{@code pageSize} 形态，不包含业务谓词；错误码与文案由调用方按 AS 契约传入。</p>
 */
@Component
public class PageWindowGate {

    /**
     * 要求 {@code pageNo}、{@code pageSize} 均为非 null 且 &ge; 1（对外 1-based 分页契约）。
     *
     * @param request   分页请求；可为继承 {@link AbstractPageQuery} 的 REST DTO
     * @param errorCode 业务错误码
     * @param message   非法时透出文案
     * @throws BusinessException {@code request} 为 null，或页码/页大小非法时
     */
    public void requirePositivePageWindow(AbstractPageQuery request, String errorCode, String message) {
        if (request == null) {
            throw new BusinessException(errorCode, message);
        }
        Integer pageNo = request.getPageNo();
        Integer pageSize = request.getPageSize();
        if (pageNo == null || pageNo < 1 || pageSize == null || pageSize < 1) {
            throw new BusinessException(errorCode, message);
        }
    }

    /**
     * 要求 {@code pageNo}、{@code pageSize} 均非 null（不校验 &ge; 1，供存量仅判空的 IF 收口用；新 IF 优先 {@link #requirePositivePageWindow}）。
     *
     * @param request   分页请求
     * @param errorCode 业务错误码
     * @param message   非法时透出文案
     * @throws BusinessException {@code request} 为 null，或页码/页大小为 null 时
     */
    public void requireNonNullPageWindow(AbstractPageQuery request, String errorCode, String message) {
        if (request == null) {
            throw new BusinessException(errorCode, message);
        }
        if (request.getPageNo() == null || request.getPageSize() == null) {
            throw new BusinessException(errorCode, message);
        }
    }
}
