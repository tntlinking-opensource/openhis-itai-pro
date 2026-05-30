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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库房单据明细查询行（查 {@code wkf_supply_request}；替代 {@code WarehouseDocumentDetailReadModel}）。
 */
@Getter
@Setter
public class WarehouseDocumentDetailQueryModel {

    private Long id;
    private String busNo;
    private String originalBusNo;
    private String typeEnum;
    private String statusEnum;
    private String categoryEnum;
    private Long summaryId;
    private Long deliverId;
    private String sourceTypeEnum;
    private Long sourceLocationId;
    private String purposeTypeEnum;
    private Long purposeLocationId;
    private String itemTable;
    private Long itemId;
    private BigDecimal itemQuantity;
    private String unitCode;
    private String lotNumber;
    private String traceNo;
    private BigDecimal price;
    private BigDecimal totalPrice;
    private Long supplierId;
    private Long applicantId;
    private LocalDateTime applyTime;
    private Long approverId;
    private LocalDateTime approvalTime;
    private Long practitionerId;
    private LocalDateTime occurrenceTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String reason;
    private String remake;
}
