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

package com.openhis.health.domain.workflow.devicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 创建耗材草稿命令。
 */
@Getter
@Setter
public class CreateDeviceOrderDraftCommand extends BatchCommand<CreateDeviceOrderDraftCommand.CreateDeviceOrderDraftItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 创建设备医嘱草稿项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CreateDeviceOrderDraftItemCommand {
        /** 设备def主键。 */
        private Long deviceDefId;
        /** 数量。 */
        private BigDecimal quantity;
        /** unit编码。 */
        private String unitCode;
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键。 */
        private Long patientId;
        /** 执行位置。 */
        private Long performLocation;

        /** 生成来源持久化码（医生站与药品对称过滤）。 */
        private String generateSourceEnum;
        /** 医生站列表展示排序号（可选）。 */
        private Integer sortNumber;
        /**
         * 诊断定义主键（门诊医生站绑定诊断；持久化 {@code wkf_device_request.condition_definition_id}）。
         */
        private Long conditionDefinitionId;
        /**
         * 就诊诊断主键（医生站绑定诊断；持久化 {@code wkf_device_request.encounter_diagnosis_id}）。
         */
        private Long encounterDiagnosisId;
    }
}

