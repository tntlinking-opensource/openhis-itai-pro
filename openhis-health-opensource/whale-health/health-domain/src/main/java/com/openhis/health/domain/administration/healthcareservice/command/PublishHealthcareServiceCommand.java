/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.healthcareservice.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 发布医疗服务命令（批量）。
 * <p>
 * 将草稿或待发布状态的医疗服务发布为可用，供挂号、收费等业务引用。
 */
@Getter
@Setter
public class PublishHealthcareServiceCommand extends BatchCommand<PublishHealthcareServiceCommand.Item> {
    /** 执行本次批量发布的操作人标识。 */
    private Long operatorId;
    /** 单条发布项：指定待发布的医疗服务。 */
    @Getter
    @Setter
    public static class Item {
        /** 待发布的医疗服务主键。 */
        private Long serviceId;
    }
}
