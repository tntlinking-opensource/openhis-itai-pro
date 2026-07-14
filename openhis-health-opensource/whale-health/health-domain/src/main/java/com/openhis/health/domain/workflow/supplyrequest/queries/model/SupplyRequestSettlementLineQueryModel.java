/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库房日结供应动账明细行读模型（**AS-INV-RPT-001** · **DM-WKF-006 §6.2.12**）。
 *
 * <p>仅承载日结汇总所需字段；品种级 SUM 与源/目的 ± 方向在应用层完成。</p>
 */
@Getter
@Setter
public class SupplyRequestSettlementLineQueryModel implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 供应请求明细主键。 */
    private Long id;

    /** 业务单号。 */
    private String busNo;

    /** 供应类型持久化码。 */
    private String typeEnum;

    /** 源库位主键。 */
    private Long sourceLocationId;

    /** 目的库位主键。 */
    private Long purposeLocationId;

    /** 物品主表名。 */
    private String itemTable;

    /** 物品主键（品种级汇总键）。 */
    private Long itemId;

    /** 行数量（拆零最小单位）。 */
    private BigDecimal itemQuantity;

    /** 批号进价（包装单位口径，行冻结）。 */
    private BigDecimal price;

    /** 行进价总额（冻结）。 */
    private BigDecimal totalPrice;

    /** 盘点/损益行金额（有符号；其它类型常为 null）。 */
    private BigDecimal profitLossPrice;

    /** 批号。 */
    private String lotNumber;

    /** 审批同意时刻（动账时间轴）。 */
    private LocalDateTime approvalTime;
}
