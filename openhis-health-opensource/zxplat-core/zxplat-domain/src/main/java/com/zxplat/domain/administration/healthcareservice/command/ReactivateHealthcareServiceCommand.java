/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.healthcareservice.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 重新激活医疗服务命令（批量）。
 * <p>
 * 将因暂停、退役等原因不可用的医疗服务恢复为可对外提供状态（与业务状态机一致）。
 */
@Getter
@Setter
public class ReactivateHealthcareServiceCommand extends BatchCommand<ReactivateHealthcareServiceCommand.Item> {
    /** 执行本次批量重新激活的操作人标识。 */
    private Long operatorId;
    /** 单条激活项：指定待重新激活的医疗服务。 */
    @Getter
    @Setter
    public static class Item {
        /** 待重新激活的医疗服务主键。 */
        private Long serviceId;
    }
}
