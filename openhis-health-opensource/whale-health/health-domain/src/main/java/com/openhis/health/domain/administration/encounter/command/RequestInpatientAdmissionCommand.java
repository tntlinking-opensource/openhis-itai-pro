/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 发起住院申请命令：由门诊发起拟收治科室、入院诊断与来源信息，INSERT 新住院就诊。
 */
@Getter
@Setter
public class RequestInpatientAdmissionCommand extends BatchCommand<RequestInpatientAdmissionCommand.RequestInpatientAdmissionItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条申请项。 */
    @Getter
    @Setter
    public static class RequestInpatientAdmissionItemCommand {
        /** 源门诊就诊主键（写入住院头表 {@code amb_encounter_id}）。 */
        private Long ambEncounterId;
        /** 患者主键。 */
        private Long patientId;
        /** 拟收治科室主键。 */
        private Long targetDepartmentId;
        /** 申请医师主键。 */
        private Long requestDoctorId;
        /** 意向病区位置主键（{@code LocationForm.WARD}）。 */
        private Long wardId;
        /** 入院来源编码（转诊等）。 */
        private String admitSourceCode;
        /** 入院途径编码。 */
        private String inWayCode;
        /** 病情优先级编码。 */
        private String priorityCode;
        /** 入院诊断明细（一律为住院线新建关联）。 */
        private List<AdmissionConditionItem> admissionDiagnoses;
    }
}
