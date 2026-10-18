/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.queries;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库房期初期末明细期间供应动账批量读入参（**AS-INV-RPT-001** · **DM-WKF-006 §6.2.12**）。
 *
 * <p>时间轴为 {@code approval_time}；双闭 {@code LocalDateTime} 边界由
 * {@code DailySettlementWindowResolver}（**BR-INV-SET-004**）换算后写入
 * {@link #approvalWindowStartInclusive} / {@link #approvalWindowEndInclusive}。</p>
 *
 * <p>{@code typeEnumWhitelist} 须由应用层按库位形态与 **BR-INV-SET-007/010** 组装；本聚合读侧不做壳单排除业务推断。</p>
 */
@Getter
@Setter
public class SupplyRequestSettlementWindowQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 选中库房（源或目的命中其一即返回，方向由应用层按 BR-INV-SET-008 判定）。 */
    private Long warehouseId;

    /**
     * 物品主表名（药品/耗材 Tab 对应的 {@code item_table} 持久化值）；空白时读侧返回空列表。
     */
    private String itemTable;

    /**
     * 动账类型白名单（{@link com.zxplat.domain.share.enums.SupplyType#getCode()} 持久化字符串集合）；
     * {@code null} 或空时读侧返回空列表。
     */
    private List<String> typeEnumWhitelist;

    /** 审批时刻下界（含）；对应 {@code wkf_supply_request.approval_time}。 */
    private LocalDateTime approvalWindowStartInclusive;

    /** 审批时刻上界（含）；对应 {@code wkf_supply_request.approval_time}。 */
    private LocalDateTime approvalWindowEndInclusive;

    /** 可选租户；非空时等值过滤 {@code tenant_id}。 */
    private Long tenantId;
}
