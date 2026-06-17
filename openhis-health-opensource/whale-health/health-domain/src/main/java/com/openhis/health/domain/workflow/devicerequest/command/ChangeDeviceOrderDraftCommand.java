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
import java.time.LocalDateTime;

/**
 * 修改耗材草稿命令。
 */
@Getter
@Setter
public class ChangeDeviceOrderDraftCommand extends BatchCommand<ChangeDeviceOrderDraftCommand.ChangeDeviceOrderDraftItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 变更设备医嘱草稿项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ChangeDeviceOrderDraftItemCommand {
        /** 主键。 */
        private Long id;
        /** 数量。 */
        private BigDecimal quantity;
        /** unit编码。 */
        private String unitCode;
        /** 执行位置。 */
        private Long performLocation;
        /** 批号号。 */
        private String lotNumber;
        /** 追溯号。 */
        private String traceNo;
        /** remark。 */
        private String remark;
        /** use时间。 */
        private LocalDateTime useTime;
        /** use开始时间。 */
        private LocalDateTime useStartTime;
        /** use结束时间。 */
        private LocalDateTime useEndTime;
        /** usetiming。 */
        private String useTiming;
        /** 医生站列表展示排序号；非空时更新。 */
        private Integer sortNumber;
        /**
         * 诊断定义主键（草稿修改；非空且与存量不同时更新 {@code wkf_device_request.condition_definition_id}）。
         */
        private Long conditionDefinitionId;
        /**
         * 就诊诊断主键（草稿修改；非空且与存量不同时更新 {@code wkf_device_request.encounter_diagnosis_id}）。
         */
        private Long encounterDiagnosisId;
    }
}

