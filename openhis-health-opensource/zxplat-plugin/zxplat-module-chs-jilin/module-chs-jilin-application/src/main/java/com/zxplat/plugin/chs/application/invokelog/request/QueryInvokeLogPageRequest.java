/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.invokelog.request;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 医保调用日志分页请求（POST /api/app/insuranceInvokeLog/queryInvokeLogPage）。 */
@Getter
@Setter
@Accessors(chain = true)
public class QueryInvokeLogPageRequest {

    /** 创建时间起（含）；必填 */
    private LocalDateTime createdFrom;

    /** 创建时间止（含）；必填 */
    private LocalDateTime createdTo;

    /** 交易编号 */
    private String infno;

    /** true=成功(infcode=0)；false=失败；null=不限 */
    private Boolean success;

    /** 发送方报文 ID */
    private String msgid;

    /** 契约业务号 */
    private String contractBusNo;

    /** 院内就诊号（拼装解析为 encounterId，禁止 SQL JOIN） */
    private String encounterBusNo;

    /** 患者姓名（模糊；拼装：姓名→患者→就诊→筛日志，禁止 SQL JOIN） */
    private String patientName;

    /** 就诊主键（与 encounterBusNo 同时传时以 busNo 解析结果为准） */
    private Long encounterId;

    /** 收费批次号 */
    private String chrgBchno;

    /** 支付单 ID */
    private Long paymentDraftId;

    /** 结算回执 ID */
    private Long settlementRecordId;

    /** 错误信息关键字 */
    private String errMsgKeyword;

    private Integer pageNo;

    private Integer pageSize;
}
