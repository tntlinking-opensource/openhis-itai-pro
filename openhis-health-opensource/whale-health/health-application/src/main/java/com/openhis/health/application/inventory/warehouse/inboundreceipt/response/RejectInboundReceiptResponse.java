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

package com.openhis.health.application.inventory.warehouse.inboundreceipt.response;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>状态迁移与驳回原因落库由领域完成；本对象表示领域命令是否被接受并成功持久化。
 * 失败时亦可能直接抛出 {@link com.openhis.whale.core.application.exception.BusinessException}（经 {@code WHS_IN_003} 映射）。</p>
 */
@Getter
@Setter
public class RejectInboundReceiptResponse {

    /**
     * 驳回是否成功落库；与 Mapper {@code toRejectInboundReceiptResponse} 装配值一致。
     */
    private Boolean success;
}
