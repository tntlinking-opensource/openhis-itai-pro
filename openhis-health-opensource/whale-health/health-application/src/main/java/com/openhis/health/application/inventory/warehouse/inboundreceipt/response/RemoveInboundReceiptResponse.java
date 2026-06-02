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
 * <p>表示同号逻辑删除是否被领域接受；底座将物理 delete 改写为 {@code isDeleted} 更新（B.13）。
 * 门禁不满足时可能抛 {@link com.openhis.whale.core.application.exception.BusinessException}（经 {@code WHS_IN_005} 映射）。</p>
 */
@Getter
@Setter
public class RemoveInboundReceiptResponse {

    /**
     * 逻辑删除是否成功；当前成功路径恒为 {@code true}，异常路径以抛错为主。
     */
    private Boolean success;
}
