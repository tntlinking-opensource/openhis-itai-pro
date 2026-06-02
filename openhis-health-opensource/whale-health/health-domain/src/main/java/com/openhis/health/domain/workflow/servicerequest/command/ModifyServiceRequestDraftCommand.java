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

package com.openhis.health.domain.workflow.servicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 修改服务申请草稿命令。
 */
@Getter
@Setter
public class ModifyServiceRequestDraftCommand extends BatchCommand<ModifyServiceRequestDraftCommand.ModifyServiceRequestDraftItemCommand> {

    private Long operatorId;

    @Getter
    @Setter
    public static class ModifyServiceRequestDraftItemCommand {
        private Long id;
        private BigDecimal quantity;
        private String unitCode;
        /** 计划执行次数（临嘱；长嘱可空）。 */
        private Integer executionCount;
        private String categoryEnum;
        private String therapyEnum;
        private Long requesterId;
        private String reasonText;
        private Long performerId;
        private Long locationId;
        private Long orgId;
        private String remark;
        private String entrustContent;
        private List<DetailItemCommand> detailItems;
        private LocalDateTime occurrenceStartTime;
        private LocalDateTime occurrenceEndTime;
        /** 医生站列表展示排序号；非空时更新。 */
        private Integer sortNumber;
        /**
         * 诊断定义主键（草稿修改；非空且与存量不同时更新 {@code wkf_service_request.condition_definition_id}）。
         */
        private Long conditionDefinitionId;
        /**
         * 用药频次编码（长期诊疗必填；传空串表示清空 {@code rate_code}）。
         */
        private String rateCode;
    }

    /**
     * 明细行子命令（为本命令独立类型，避免跨命令类型依赖）。
     */
    @Getter
    @Setter
    public static class DetailItemCommand {
        private Long bodySiteId;
        private Long reasonCodeId;
        private String instruction;
        private Integer priority;
        private LocalDateTime scheduledTime;
        private Long performerId;
        private Long locationId;
    }
}
