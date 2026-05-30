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
 * 供应发放摘要读模型（列表或门禁场景最小字段集）。
 *
 * <p>{@code statusEnum} 为 {@link com.openhis.health.domain.share.enums.DispenseStatus} 等持久化 {@code getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class SupplyDeliveryModel {

    /** 发放主键。 */
    private Long id;
    /** 发放状态持久化码。 */
    private String statusEnum;
    /** 上游供应请求行主键。 */
    private Long requestId;
}
