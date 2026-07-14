/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.cashiersettlement.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 提交收费员交账命令。
 */
@Getter
@Setter
public class SubmitCashierSettlementCommand {

    /** 交账类型：OUTPATIENT / INPATIENT。 */
    private String typeEnum;

    /** 交账所属科室。 */
    private Long orgId;

    /** 收费窗口，可空。 */
    private Long locationId;

    /** 交账经办人（默认纳入 enterer_id 等于该值的 payment）。 */
    private Long cashierId;

    /**
     * payment {@code enterer_id} 匹配白名单（Practitioner 主键 + 绑定用户主键）；提交锁单与预览共用。
     */
    private List<Long> entererMatchIds;

    /** 业务日起（查询窗，不落库）。 */
    private LocalDate dateFrom;

    /** 业务日止（查询窗，不落库）。 */
    private LocalDate dateTo;

    /** 当前操作人（审计）。 */
    private Long operatorId;

    /**
     * 本次勾选挂载的 payment 主键集合（去重后须 ⊆ 锁定候选集）。
     *
     * <p>{@code null} 时回落挂载全量未交账候选（兼容存量应用层整批交账）；非空且空列表拒绝提交。</p>
     */
    private List<Long> selectedPaymentIds;
}
