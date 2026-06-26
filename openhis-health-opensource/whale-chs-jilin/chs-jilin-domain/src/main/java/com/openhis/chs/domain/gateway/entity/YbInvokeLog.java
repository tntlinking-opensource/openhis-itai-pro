/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.gateway.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IExcludeFromAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 医保平台网关调用日志（2206/2207 等 request/response 全文）。 */
@Getter
@Setter
@Entity
@Table(name = "yb_invoke_log")
public class YbInvokeLog extends BaseAuditedEntity implements IExcludeFromAudit {

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "contract_bus_no", length = 64)
    private String contractBusNo;

    @Column(name = "infno", nullable = false, length = 16)
    private String infno;

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

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "request_json", columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;
}
