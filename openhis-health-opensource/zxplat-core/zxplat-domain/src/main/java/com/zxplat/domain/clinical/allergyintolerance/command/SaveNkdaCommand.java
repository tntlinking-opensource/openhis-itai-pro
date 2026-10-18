/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.allergyintolerance.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 登记「无已知过敏」命令；患者当前不得存在有效过敏原。
 */
@Getter
@Setter
public class SaveNkdaCommand {
    /** 就诊主键（登记时所在就诊，仅审计）。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 验证状态编码。 */
    private String verificationStatusEnum;
    /** 记录时间；空则当前时刻。 */
    private LocalDateTime recordedDate;
    /** 记录者医务人员主键。 */
    private Long practitionerId;
}
