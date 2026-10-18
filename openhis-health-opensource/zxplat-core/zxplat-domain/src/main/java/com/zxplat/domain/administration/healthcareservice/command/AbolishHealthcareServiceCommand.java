/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.healthcareservice.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 废止医疗服务命令（批量）。
 * <p>
 * 将已发布或可用的医疗服务在业务上标记为废止，通常需记录操作人与原因。
 */
@Getter
@Setter
public class AbolishHealthcareServiceCommand extends BatchCommand<AbolishHealthcareServiceCommand.Item> {
    /** 执行本次批量操作的操作人标识。 */
    private Long operatorId;
    /**
     * 单条废止项：指定待废止的医疗服务及原因。
     */
    @Getter
    @Setter
    public static class Item {
        /** 待废止的医疗服务主键。 */
        private Long serviceId;
        /** 废止原因说明（如监管、业务下线等）。 */
        private String reason;
    }
}
