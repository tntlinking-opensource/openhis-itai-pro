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
 * 调价单事件快照基类（DM-CUS-011 v2 单据模型）。
 *
 * <p>载荷字段与 {@code cus_price_adjustment} 锚点行对齐，供投影与集成消费；发布时机为 beforeCommit。</p>
 */
@Getter
@Setter
public class PriceAdjustmentSnapshotEvent extends DomainEvent {

    /** 调价单号 {@code bus_no}。 */
    private String busNo;

    /** 项目物理表名（药品/耗材定义表）。 */
    private String itemTable;

    /** 项目定义主键。 */
    private Long itemId;

    /** 目标进价（主行）。 */
    private BigDecimal newPurchaseAmount;

    /** 目标售价（主行）。 */
    private BigDecimal newSellingAmount;

    /** 调价原因。 */
    private String adjustmentReason;

    /** 制单/申请时间。 */
    private Date adjustmentTime;

    /** 审批状态 code（{@link com.openhis.health.domain.share.enums.PriceAdjustmentStatus}）。 */
    private String statusEnum;

    /** 提交人。 */
    private Long submitterId;

    /** 提交时间。 */
    private Date submitTime;

    /** 审批人。 */
    private Long approverId;

    /** 审批时间。 */
    private Date approveTime;

    /** 审批意见（驳回时必填）。 */
    private String approveComment;

    /** 操作人。 */
    private Long operatorId;

    /** 机构主键。 */
    private Long orgId;

    /** 本次提交加锁的库存行数（仅提交/再次提交事件填充）。 */
    private Integer lockedInventoryCount;

    /**
     * 反序列化/框架用默认构造。
     */
    public PriceAdjustmentSnapshotEvent() {
        super();
    }

    /**
     * 按聚合锚点主键构造。
     *
     * @param aggregateId 调价单首行主键
     */
    public PriceAdjustmentSnapshotEvent(Long aggregateId) {
        super(aggregateId, AggregateTypeConstants.PRICE_ADJUSTMENT);
    }
}
