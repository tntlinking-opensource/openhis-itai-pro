/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * MWL 待扫工单行（供设备同步脚本 / Orthanc worklists 插件消费）。
 */
@Getter
@Setter
public class ModalityWorklistRowDto {

    /** 工单主键。 */
    private Long workItemId;

    /** 检查号。 */
    private String accessionNumber;

    /** 患者主键。 */
    private Long patientId;

    /** 患者姓名；可空（获取失败不阻断）。 */
    private String patientName;

    /** 模态码。 */
    private String modalityCode;

    /** 科室。 */
    private Long organizationId;

    /** 计划检查时间。 */
    private LocalDateTime scheduledTime;

    /** 检查执行态枚举 code。 */
    private String statusEnum;

    /** 部位码。 */
    private String bodySiteCode;
}
