/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.practitioner.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 退休/注销医疗卫生人员命令（批量）。
 * <p>
 * 人员永久退出执业岗位（退休、离职注销等），需记录原因；与临时停用区分。
 */
@Getter
@Setter
public class RetirePractitionerCommand extends BatchCommand<RetirePractitionerCommand.RetirePractitionerItemCommand> {
    /** 执行本次批量退休/注销的操作人标识。 */
    private Long operatorId;

    /** 单条退休项：目标人员及原因。 */
    @Getter
    @Setter
    public static class RetirePractitionerItemCommand {
        /** 待退休/注销的医疗卫生人员主键。 */
        private Long practitionerId;
        /** 退休或注销原因说明。 */
        private String reason;
    }
}
