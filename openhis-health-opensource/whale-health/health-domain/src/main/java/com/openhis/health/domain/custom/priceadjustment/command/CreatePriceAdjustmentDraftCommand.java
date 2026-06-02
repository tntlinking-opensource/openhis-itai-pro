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

package com.openhis.health.domain.custom.priceadjustment.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 创建价格调整草稿命令。
 */
@Getter
@Setter
public class CreatePriceAdjustmentDraftCommand {
    /**
     * 定义标识（药品/诊疗/耗材等定价对象的定义主键）。
     */
    private Long definitionId;
    /**
     * 价格类型（参见共享枚举 {@code PriceType} 的 code）。
     */
    private String priceType;
    /**
     * 批号（可空；用于批号粒度定价或追溯）。
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
     * 调整原因（参见共享枚举 {@code AdjustmentReason} 的 code）。
     */
    private String adjustmentReason;
    /**
     * 调整时间（业务发生时间）。
     */
    private Date adjustmentTime;
    /**
     * 操作人标识。
     */
    private Long operatorId;
    /**
     * 机构标识。
     */
    private Long orgId;
    /**
     * 库存数量（当批号维度要求校验/影响范围计算时使用；可空）。
     */
    private Integer stockQuantity;
}

