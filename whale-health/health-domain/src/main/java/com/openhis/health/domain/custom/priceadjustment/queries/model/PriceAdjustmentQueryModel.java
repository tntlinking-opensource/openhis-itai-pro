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

package com.openhis.health.domain.custom.priceadjustment.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 价格调整查询模型（覆盖多查询场景字段集合）。
 */
@Getter
@Setter
public class PriceAdjustmentQueryModel {
    /**
     * 调整单标识。
     */
    private Long id;
    /**
     * 状态枚举值（草稿/已提交/已通过/已驳回/已撤回等）。
     */
    private String statusEnum;
    /**
     * 定义标识（药品/诊疗/耗材等定价对象的定义主键）。
     */
    private Long definitionId;
    /**
     * 价格类型（{@code PriceType} 的 code）。
     */
    private String priceType;
    /**
     * 批号（可空）。
     */
    private String lotNumber;
    /**
     * 调整前金额。
     */
    private BigDecimal oldAmount;
    /**
     * 调整后金额。
     */
    private BigDecimal newAmount;
    /**
     * 调整原因（{@code AdjustmentReason} 的 code）。
     */
    private String adjustmentReason;
    /**
     * 调整时间（业务发生时间）。
     */
    private Date adjustmentTime;
    /**
     * 提交人标识（提交审核时写入）。
     */
    private Long submitterId;
    /**
     * 提交时间。
     */
    private Date submitTime;
    /**
     * 审批人标识（审批通过/驳回时写入）。
     */
    private Long approverId;
    /**
     * 审批时间。
     */
    private Date approveTime;
    /**
     * 审批意见（可空）。
     */
    private String approveComment;
    /**
     * 操作人标识（产生该记录的最后操作者或创建者，口径以查询场景为准）。
     */
    private Long operatorId;
    /**
     * 机构标识。
     */
    private Long orgId;
    /**
     * 库存数量（可空）。
     */
    private Integer stockQuantity;
    /**
     * 单次调整差额（newAmount-oldAmount）。
     */
    private BigDecimal adjustmentDelta;
    /**
     * 影响总额（adjustmentDelta * stockQuantity；当 stockQuantity 为空时可为空）。
     */
    private BigDecimal totalImpact;
}

