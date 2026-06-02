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
 * 创建服务申请草稿命令。
 */
@Getter
@Setter
public class CreateServiceRequestDraftCommand extends BatchCommand<CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand> {

    private Long operatorId;

    @Getter
    @Setter
    public static class CreateServiceRequestDraftItemCommand {
        private Long encounterId;
        private Long patientId;
        private Long activityId;
        private BigDecimal quantity;
        private String unitCode;
        /** 计划执行次数（临嘱必填；长嘱可空，按频次执行）。 */
        private Integer executionCount;
        private String categoryEnum;
        private String therapyEnum;
        private Long requesterId;
        private List<DetailItemCommand> detailItems;
        private String note;
        /** 生成来源持久化码（医生站列表与药品/耗材对称过滤）。 */
        private String generateSourceEnum;
        /** 医生站列表展示排序号（可选）。 */
        private Integer sortNumber;
        /**
         * 诊断定义主键（门诊医生站绑定诊断；持久化 {@code wkf_service_request.condition_definition_id}）。
         */
        private Long conditionDefinitionId;
        /** 用药频次编码（长期诊疗必填；持久化 {@code wkf_service_request.rate_code}）。 */
        private String rateCode;
        /** 执行科室（持久化 {@code wkf_service_request.org_id}）。 */
        private Long orgId;
        /** 执行地点（持久化 {@code wkf_service_request.location_id}）。 */
        private Long locationId;
        /** 费用性质覆盖（持久化 {@code wkf_service_request.contract_id}）。 */
        private Long contractId;
    }

    /**
     * 明细行子命令（用于 detailJson 组装或结构化承载）。
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
