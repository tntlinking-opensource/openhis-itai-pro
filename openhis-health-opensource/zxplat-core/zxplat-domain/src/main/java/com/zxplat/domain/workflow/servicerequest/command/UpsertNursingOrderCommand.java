/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 维护护理医嘱命令。
 */
@Getter
@Setter
public class UpsertNursingOrderCommand extends BatchCommand<UpsertNursingOrderCommand.UpsertNursingOrderItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 写入护理医嘱项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class UpsertNursingOrderItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 类别枚举编码。 */
        private String categoryEnum;
        /** content。 */
        private String content;
    }
}
