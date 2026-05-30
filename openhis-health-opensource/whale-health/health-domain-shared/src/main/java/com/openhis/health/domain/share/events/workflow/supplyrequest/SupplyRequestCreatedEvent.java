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

package com.openhis.health.domain.share.events.workflow.supplyrequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SupplyRequestCreatedEvent 领域事件。
 *
 * <p>须在发布前写入与 {@code wkf_supply_request} 首行一致的明细快照（见 {@code SupplyRequestDomainService#publishCreated}）。</p>
 */
@Getter
@Setter
public class SupplyRequestCreatedEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 单据类型。 */
    private String typeEnum;

    /** 单据状态（初态）。 */
    private String statusEnum;

    /** 来源单据号（执行单场景可选）。 */
    private String originalBusNo;

    /** 汇总编号（药品汇总单可选）。 */
    private Long summaryId;

    /** 患者标识（药品汇总单可选）。 */
    private Long patientId;

    /** 明细行数（同 busNo 聚合的行数，可选）。 */
    private Integer lineCount;

    /** 操作人标识。 */
    private Long operatorId;

    /** 供应类别编码。 */
    private String categoryEnum;

    private String sourceTypeEnum;

    private Long sourceLocationId;

    private String purposeTypeEnum;

    private Long purposeLocationId;

    private String itemTable;

    private Long itemId;

    private BigDecimal itemQuantity;

    private String unitCode;

    private String lotNumber;

    private BigDecimal price;

    private BigDecimal totalPrice;

    private Long supplierId;

    private Long applicantId;

    private LocalDateTime applyTime;

    private String reason;

    private String remake;

    private LocalDateTime occurrenceTime;

    /** 效期区间起点（与 {@code wkf_supply_request.start_time} 对齐）。 */
    private LocalDateTime startTime;

    /** 效期区间止点（与 {@code wkf_supply_request.end_time} 对齐）。 */
    private LocalDateTime endTime;

    private Long practitionerId;

    private String traceNo;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public SupplyRequestCreatedEvent() {
        super();
    }

    public SupplyRequestCreatedEvent(
        Long aggregateId,
        String busNo,
        String typeEnum,
        String statusEnum,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLY_REQUEST);
        this.busNo = busNo;
        this.typeEnum = typeEnum;
        this.statusEnum = statusEnum;
        this.operatorId = operatorId;
    }
}
