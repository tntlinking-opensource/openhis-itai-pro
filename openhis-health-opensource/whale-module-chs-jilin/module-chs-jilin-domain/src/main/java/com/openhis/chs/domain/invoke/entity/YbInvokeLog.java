/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.invoke.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 医保平台网关调用日志（全 infno request/response；V12 增 infname、err_msg） */
@Getter
@Setter
@Entity
@Table(name = "yb_invoke_log")
public class YbInvokeLog extends BaseAuditedEntity {

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "contract_bus_no", length = 64)
    private String contractBusNo;

    @Column(name = "infno", nullable = false, length = 16)
    private String infno;

    @Column(name = "infname", length = 128)
    private String infname;

    @Column(name = "msgid", length = 64)
    private String msgid;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "payment_draft_id")
    private Long paymentDraftId;

    @Column(name = "chrg_bchno", length = 30)
    private String chrgBchno;

    @Column(name = "pre_settle_id")
    private Long preSettleId;

    @Column(name = "settlement_record_id")
    private Long settlementRecordId;

    @Column(name = "infcode", length = 8)
    private String infcode;

    @Column(name = "err_msg", length = 512)
    private String errMsg;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "request_json", columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;
}
