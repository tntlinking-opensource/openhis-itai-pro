/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.location.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 登记位置命令：新建地点节点并挂父级、机构与形态等主数据。
 */
@Getter
@Setter
public class RegisterLocationCommand extends BatchCommand<RegisterLocationCommand.RegisterLocationItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条登记项。 */
    @Getter
    @Setter
    public static class RegisterLocationItemCommand {
        /** 位置名称。 */
        private String name;
        /** 父位置主键（根节点可空）。 */
        private Long parentId;
        /** 地点形态枚举编码。 */
        private String formEnum;
        /** 管理模式枚举编码。 */
        private String modeEnum;
        /** 类型扩展 JSON。 */
        private String typeJson;
        /** 所属机构主键。 */
        private Long organizationId;
        /** 展示排序。 */
        private Integer displayOrder;
        /** 拼音检索串。 */
        private String pyStr;
        /** 五笔检索串。 */
        private String wbStr;
        /** 为 true 时登记即为启用（病区床位主数据）；默认 false 为草稿。 */
        private Boolean registerAsActive;
    }
}
