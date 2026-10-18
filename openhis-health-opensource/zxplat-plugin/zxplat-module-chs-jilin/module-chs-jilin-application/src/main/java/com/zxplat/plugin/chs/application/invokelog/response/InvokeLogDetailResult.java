/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.invokelog.response;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 医保调用日志详情（含完整请求/响应原文）。 */
@Getter
@Setter
@Accessors(chain = true)
public class InvokeLogDetailResult {

    private Long id;

    private LocalDateTime createdAt;

    private String infno;

    private String infname;

    private String msgid;

    private String infcode;

    private Boolean success;

    private String errMsg;

    private Integer durationMs;

    private Long contractId;

    private String contractBusNo;

    private Long encounterId;

    private String encounterBusNo;

    private String patientName;

    private String chrgBchno;

    private Long paymentDraftId;

    private Long preSettleId;

    private Long settlementRecordId;

    private String requestJson;

    private String responseJson;
}
