/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.registration.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 检查登记条打印 slip（与 {@code pacs-registration-slip-print} 模板 PrintData 契约一致）。
 *
 * <p>前端以此为 printData，再调 {@code printJob/preparePrint}（scenarioCode）出纸。</p>
 */
@Getter
@Setter
public class PacsRegistrationSlipPrintDto {

    /** 打印场景码。 */
    private String scenarioCode;

    /** 工单主键。 */
    private Long workItemId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 检查号。 */
    private String accessionNumber;

    /** 患者姓名（Patient JOIN，不落 WorkItem）。 */
    private String patientName;

    /** 性别展示文案。 */
    private String genderEnumText;

    /** 年龄展示文案。 */
    private String ageText;

    /** 执行/登记科室名称。 */
    private String organizationName;

    /** 模态码。 */
    private String modalityCode;

    /** 检查部位码。 */
    private String bodySiteCode;

    /** 计划检查时间展示。 */
    private String scheduledTimeText;

    /** 工单状态 code。 */
    private String statusEnum;
}
