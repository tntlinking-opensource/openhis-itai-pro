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

package com.openhis.health.domain.workflow.supplydelivery.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 发放校验读模型：某供应请求行是否已存在完成态发放（用于编辑/撤销等门控）。
 *
 * <p>「完成」判定依赖读服务实现，通常与 {@code status_enum} 终态持久化 code 比对（B.10.8.1）。</p>
 */
@Getter
@Setter
public class SupplyDeliveryValidationModel {

    /** 供应请求行主键。 */
    private Long requestId;
    /** 是否已存在至少一条完成态发放。 */
    private Boolean hasCompletedDelivery;
}
