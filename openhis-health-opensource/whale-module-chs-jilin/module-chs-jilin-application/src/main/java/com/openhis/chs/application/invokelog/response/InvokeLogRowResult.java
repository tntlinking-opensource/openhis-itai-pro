/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.invokelog.response;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 医保调用日志列表行（不含完整报文，仅摘要）。 */
@Getter
@Setter
@Accessors(chain = true)
public class InvokeLogRowResult {

    private Long id;

    private LocalDateTime createdAt;

    private String infno;

    private String infname;

    private String msgid;

    /** 平台返回码 */
    private String infcode;

    /** 是否成功（infcode=0） */
    private Boolean success;

    private String errMsg;

    private Integer durationMs;

    private Long contractId;

    private String contractBusNo;

    private Long encounterId;

    /** 拼装回填的就诊号 */
    private String encounterBusNo;

    /** 拼装回填的患者姓名 */
    private String patientName;

    private String chrgBchno;

    private Long paymentDraftId;

    private Long preSettleId;

    private Long settlementRecordId;

    /** 请求报文摘要（截断） */
    private String requestPreview;

    /** 响应报文摘要（截断） */
    private String responsePreview;
}
