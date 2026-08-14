/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工作量明细：按 payment–chargeItem 链路展开的读模型（DM-ADM-011 §6.2.28；AS-RPT-001 IF-RPT-WKL-03/04）。
 *
 * <p>一行 = 一条收费/退费 payment 快照内的 chargeItem；金额已带正负号（BR-RPT-WKL-011）。
 * 不含挂号 chargeItem；开单科室展示名由应用层 Organization enrich（BR-RPT-WKL-017）。</p>
 */
@Getter
@Setter
public class PractitionerWorkloadRequestDetailQueryModel {

    /** chargeItem 主键（明细行唯一键之一）。 */
    private Long chargeItemId;

    /** 关联 payment 主键（明细行唯一键之一）。 */
    private Long paymentId;

    /** 医嘱锚点表名（三类 request 表之一）。 */
    private String serviceTable;

    /** 医嘱锚点主键 {@code service_id}。 */
    private Long serviceId;

    /** 开单科室 {@code adm_charge_item.requesting_org_id}。 */
    private Long requestingOrgId;

    /** 开单医生（应用层自 Request {@code requester_id} 回填；非 ChargeItem.enterer_id）。 */
    private Long entererId;

    /** 开立时间（应用层自 Request {@code req_opened_time} 回填）。 */
    private LocalDateTime enteredDate;

    /** payment 结账时间 {@code bill_date}（排序辅助）。 */
    private LocalDateTime billDate;

    /** 应收（本链路行，已含退费负号）。 */
    private BigDecimal receivableAmountTotal = BigDecimal.ZERO;

    /** 折后应收（本链路行，已含退费负号）。 */
    private BigDecimal discountedAmountTotal = BigDecimal.ZERO;

    /** 行金额 total_price（本链路行，已含退费负号）。 */
    private BigDecimal lineAmountTotal = BigDecimal.ZERO;
}
