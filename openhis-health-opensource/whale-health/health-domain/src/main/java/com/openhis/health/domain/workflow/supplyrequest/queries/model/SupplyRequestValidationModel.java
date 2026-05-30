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

package com.openhis.health.domain.workflow.supplyrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 供应请求简单校验读模型：某业务单号下是否已存在「已同意」行（用于重复同意等门控）。
 *
 * <p>{@code hasAgreed} 由仓储 {@code existsByBusNoAndStatusEnum(busNo, SupplyStatus.AGREE.getCode())} 推导，与库表 {@code status_enum} 持久化 code 一致（B.10.8.1）。</p>
 */
@Getter
@Setter
public class SupplyRequestValidationModel {

    /** 业务单号。 */
    private String busNo;
    /** 是否已存在至少一行 {@code status_enum} 为已同意（{@link com.openhis.health.domain.share.enums.SupplyStatus#AGREE} 的 {@code getCode()}）。 */
    private Boolean hasAgreed;
}
