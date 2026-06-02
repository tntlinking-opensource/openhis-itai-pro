/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
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
