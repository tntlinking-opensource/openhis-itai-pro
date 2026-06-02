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

package com.openhis.health.application.inventory.warehouse.purchaseorder.response;

import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderDetailLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 订货单详情响应。
 *
 * <p>与 {@link com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel} 对齐：{@code sourceTypeEnum}、
 * {@code purposeTypeEnum}、{@code categoryEnum} 为各枚举 {@code getCode()} 持久化字符串（B.10.8.1）；{@code typeEnum}、
 * {@code statusEnum} 仍为列表/详情历史兼容用的 sortOrder 数值。</p>
 */
@Getter
@Setter
public class GetPurchaseOrderDetailResponse {

    private String busNo;
    private Integer typeEnum;
    private Integer statusEnum;
    private Long supplierId;
    private Long sourceLocationId;
    private Long purposeLocationId;
    /** {@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}，可为 null。 */
    private String sourceTypeEnum;
    /** {@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}。 */
    private String purposeTypeEnum;
    /** {@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()}。 */
    private String categoryEnum;
    private List<PurchaseOrderDetailLineDto> lines;
}
