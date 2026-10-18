/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新服务口径命令。
 */
@Getter
@Setter
public class UpdateServiceProfileCommand extends BatchCommand<UpdateServiceProfileCommand.UpdateServiceProfileItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 更新服务档案项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class UpdateServiceProfileItemCommand {
        /** 排班主键。 */
        private Long scheduleId;
        /** 服务类别JSON。 */
        private String serviceCategoryJson;
        /** specialtyJSON。 */
        private String specialtyJson;
        /** 服务类型conceptJSON。 */
        private String serviceTypeConceptJson;
        /** 医疗服务服务主键。 */
        private Long healthcareServiceId;
        /** 档案version。 */
        private String profileVersion;
    }
}
