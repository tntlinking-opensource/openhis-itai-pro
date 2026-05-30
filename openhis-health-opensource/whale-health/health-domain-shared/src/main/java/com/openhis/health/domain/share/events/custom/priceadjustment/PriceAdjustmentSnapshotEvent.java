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

package com.openhis.health.domain.share.events.custom.priceadjustment;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 价格调整事件快照基类（包含完整调整信息，供下游核算/对账/审计使用）。
 */
@Getter
@Setter
public class PriceAdjustmentSnapshotEvent extends DomainEvent {
    private Long definitionId;
    private String priceType;
    private String lotNumber;
    private BigDecimal oldAmount;
    private BigDecimal newAmount;
    private String adjustmentReason;
    private Date adjustmentTime;
    private String statusEnum;
    private Long submitterId;
    private Date submitTime;
    private Long approverId;
    private Date approveTime;
    private String approveComment;
    private Long operatorId;
    private Long orgId;
    private Integer stockQuantity;
    private BigDecimal adjustmentDelta;
    private BigDecimal totalImpact;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public PriceAdjustmentSnapshotEvent() {
        super();
    }

    /**
     * 处理Long adjustmentId逻辑。
     * @return 返回结果
     */
    public PriceAdjustmentSnapshotEvent(Long adjustmentId) {
        super(adjustmentId, AggregateTypeConstants.PRICE_ADJUSTMENT);
    }
}

