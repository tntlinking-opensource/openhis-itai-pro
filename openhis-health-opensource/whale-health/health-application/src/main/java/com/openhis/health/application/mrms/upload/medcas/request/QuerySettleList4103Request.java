/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.medcas.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 【4103】结算清单平台查询入参。
 *
 * <p>优先直接传 {@code psnNo}+{@code setlId}；也可只传 {@code caseRecordId}/{@code medcasno}
 * 由院内结算上下文解析。</p>
 */
@Getter
@Setter
public class QuerySettleList4103Request {

    /** 费用性质合同业务号；按病案解析时可省略。 */
    private String contractBusNo;

    /** 病案主档主键（可选，用于解析 psn_no/setl_id 并写查询流水）。 */
    private Long caseRecordId;

    /** 院内病案号（可选，精确匹配后解析上下文）。 */
    private String medcasno;

    /** 人员编号 psn_no（与 setlId 成对）。 */
    private String psnNo;

    /** 结算 ID setl_id（非 setl_list_id）。 */
    private String setlId;
}
