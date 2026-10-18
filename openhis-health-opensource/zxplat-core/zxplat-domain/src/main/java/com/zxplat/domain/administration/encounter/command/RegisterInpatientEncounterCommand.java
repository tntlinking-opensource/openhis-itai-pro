/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 住院登记命令：完成入院登记并记录登记人与时间。
 */
@Getter
@Setter
public class RegisterInpatientEncounterCommand extends BatchCommand<RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 患者主键（无申请直登登记时必填）。 */
    private Long patientId;

    /** 单条登记项。 */
    @Getter
    @Setter
    public static class RegisterInpatientEncounterItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 入院科室主键。 */
        private Long departmentId;
        /** 入院病区位置主键（{@code LocationForm.WARD}）。 */
        private Long wardId;
        /** 登记员主键。 */
        private Long registrarId;
        /** 登记时间（正式入院时间）。 */
        private LocalDateTime registerTime;
        /** 费用性质业务编码（{@code fin_contract.bus_no}，登记时写入就诊账户）。 */
        private String contractNo;
        /**
         * 险种类型码（310 职工 / 390 城乡居民等）：登记开医保户时写入 {@code adm_account.insutype}。
         */
        private String insutype;
        /** 入院来源编码。 */
        private String admitSourceCode;
        /** 入院途径编码。 */
        private String inWayCode;
        /** 病情优先级编码。 */
        private String priorityCode;
        /**
         * 入院诊断：无申请直登时必填并由 {@code registerDirectInpatientEncounter} 落库；
         * 有申请登记时应为空（诊断在 {@code requestInpatientAdmission} 已写入住院线，BR-IP-REG-007）。
         */
        private List<AdmissionConditionItem> admissionDiagnoses;
    }
}
