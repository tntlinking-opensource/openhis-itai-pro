/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitioner.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 激活医疗卫生人员命令（批量）。
 * <p>
 * 将处于停用等状态的医师/卫技人员恢复为可执业、可排班状态。
 */
@Getter
@Setter
public class ActivatePractitionerCommand extends BatchCommand<ActivatePractitionerCommand.ActivatePractitionerItemCommand> {
    /** 执行本次批量激活的操作人标识。 */
    private Long operatorId;

    /** 单条激活项：指定待激活的医疗卫生人员。 */
    @Getter
    @Setter
    public static class ActivatePractitionerItemCommand {
        /** 待激活的医疗卫生人员主键。 */
        private Long practitionerId;
    }
}
