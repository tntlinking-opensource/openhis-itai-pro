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

package com.openhis.health.domain.workflow.slot.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量生成号源时段命令（DM-WKF-010）。
 */
@Getter
@Setter
public class ProvisionSlotBatchCommand extends BatchCommand<ProvisionSlotBatchCommand.ProvisionSlotBatchItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class ProvisionSlotBatchItemCommand {
        /** 号源日程标识。 */
        private Long scheduleId;

        /** 机构标识（冗余入参，用于批量出号时写入 Slot.organizationId）。 */
        private Long organizationId;

        /** 幂等键（可选）。 */
        private String idempotencyKey;

        /** 时段模板列表（每条描述一个待生成的 Slot 时间片）。 */
        private List<SlotTemplateItemCommand> slotTemplates;
    }

    @Getter
    @Setter
    public static class SlotTemplateItemCommand {
        /** 开始时刻。 */
        private LocalDateTime startTime;

        /** 结束时刻。 */
        private LocalDateTime endTime;

        /** 容量（可选，默认 1）。 */
        private Integer capacity;

        /** 服务大类快照 JSON（可选）。 */
        private String serviceCategoryJson;

        /** 服务类型快照 JSON（可选）。 */
        private String serviceTypeJson;

        /** 专科快照 JSON（可选）。 */
        private String specialtyJson;

        /** 预约类型/场景快照 JSON（可选）。 */
        private String appointmentTypeJson;

        /** 标识列表（可选）。 */
        private List<SlotIdentifierItemCommand> identifiers;
    }

    @Getter
    @Setter
    public static class SlotIdentifierItemCommand {
        /** 标识体系。 */
        private String idSystem;

        /** 标识值。 */
        private String idValue;

        /** 用途编码（可选）。 */
        private String useCode;
    }
}

