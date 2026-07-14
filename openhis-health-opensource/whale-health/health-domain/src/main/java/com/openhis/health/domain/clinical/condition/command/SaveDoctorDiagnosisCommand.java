/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.condition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 保存西医诊断命令。
 */
@Getter
@Setter
public class SaveDoctorDiagnosisCommand extends BatchCommand<SaveDoctorDiagnosisCommand.DoctorDiagnosisItemCommand> {
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 操作人主键。 */
    private Long operatorId;
    /**
     * 入院诊断场景：为 true 时，对本次新增的病情事实额外发布
     * {@link com.openhis.health.domain.share.events.clinical.condition.AdmissionDiagnosisCreatedEvent}。
     */
    private Boolean admissionDiagnosisFlow;

/**
 * 医生诊断项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class DoctorDiagnosisItemCommand {
        /** 主键。 */
        private Long id;
        /** 目录主键。 */
        private Long definitionId;
        /** 核销状态枚举编码。 */
        private String verificationStatusEnum;
        /** 临床状态枚举编码。 */
        private String clinicalStatusEnum;
        /** 类别枚举编码。 */
        private String categoryEnum;
        /** 严重程度枚举编码。 */
        private String severityEnum;
        /** description。 */
        private String description;
        /** 起病datetime。 */
        private LocalDateTime onsetDatetime;
        /** 记录datetime。 */
        private LocalDateTime recordedDatetime;
        /** 记录人主键。 */
        private Long recorderId;
        /** 医保号。 */
        private String ybNo;
    }
}
