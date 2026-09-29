/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.registration.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * IF-PACS-REG-04 本地补登记请求。
 */
@Getter
@Setter
public class CreateLocalRegistrationRequest {

    /** 患者主键。 */
    private Long patientId;

    /** 登记/执行科室。 */
    private Long organizationId;

    /** 模态码。 */
    private String modalityCode;

    /** 检查部位码。 */
    private String bodySiteCode;

    /** 可执行项目；非空时校验 capability。 */
    private Long activityId;

    /** 执行设备。 */
    private Long deviceId;

    /** 计划检查时间。 */
    private LocalDateTime scheduledTime;

    /** 已有就诊主键；空时由应用层解析/开立当日门诊就诊。 */
    private Long encounterId;

    /** 开立门诊就诊时的号源类型；encounterId 为空时必填。 */
    private Long serviceTypeId;

    /** 检查号；空则确认待摄时生成。 */
    private String accessionNumber;

    /** true=创建后立即确认待摄（默认 true）。 */
    private Boolean confirmSchedule;
}
