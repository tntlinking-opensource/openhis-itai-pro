/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.document.entity;

import com.openhis.whale.einvoice.boss.domain.invoke.support.EinvBossLogRowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 金额调额审计日志（einv_boss_amount_adjust_log）。
 */
@Getter
@Setter
@Entity
@Table(name = "einv_boss_amount_adjust_log")
public class EinvBossAmountAdjustLog extends EinvBossLogRowEntity {

    /** 所属单据主键。 */
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** 操作人主键。 */
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    /** 调额前总额。 */
    @Column(name = "before_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal beforeTotal;

    /** 调额后总额。 */
    @Column(name = "after_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal afterTotal;

    /** 行级差额 JSON 快照。 */
    @Column(name = "diff_json", columnDefinition = "TEXT")
    private String diffJson;

    /** 调额原因。 */
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;
}
