/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitioner.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 停用医疗卫生人员命令（批量）。
 * <p>
 * 将人员置为停用状态（如短期离岗），需记录原因，通常不影响历史就诊数据关联。
 */
@Getter
@Setter
public class DeactivatePractitionerCommand extends BatchCommand<DeactivatePractitionerCommand.DeactivatePractitionerItemCommand> {
    /** 执行本次批量停用的操作人标识。 */
    private Long operatorId;

    /** 单条停用项：目标人员及停用原因。 */
    @Getter
    @Setter
    public static class DeactivatePractitionerItemCommand {
        /** 待停用的医疗卫生人员主键。 */
        private Long practitionerId;
        /** 停用原因说明。 */
        private String reason;
    }
}
