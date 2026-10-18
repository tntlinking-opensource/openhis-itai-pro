/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.slot.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新时段服务覆盖快照命令（DM-WKF-010）。
 */
@Getter
@Setter
public class UpdateOverrideServiceSnapshotsCommand extends BatchCommand<UpdateOverrideServiceSnapshotsCommand.UpdateOverrideServiceSnapshotsItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class UpdateOverrideServiceSnapshotsItemCommand {
        /** 号源时段标识。 */
        private Long slotId;

        /** 服务大类快照 JSON（可选）。 */
        private String serviceCategoryJson;

        /** 服务类型快照 JSON（可选）。 */
        private String serviceTypeJson;

        /** 专科快照 JSON（可选）。 */
        private String specialtyJson;

        /** 预约类型/场景快照 JSON（可选）。 */
        private String appointmentTypeJson;
    }
}
