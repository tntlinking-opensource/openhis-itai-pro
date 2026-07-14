/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.location.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 编辑位置信息命令：维护名称、形态、模式、类型 JSON、排序与检索码等，可携带变更字段列表供审计。
 */
@Getter
@Setter
public class EditLocationInfoCommand extends BatchCommand<EditLocationInfoCommand.EditLocationInfoItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条编辑项。 */
    @Getter
    @Setter
    public static class EditLocationInfoItemCommand {
        /** 位置主键。 */
        private Long locationId;
        /** 位置名称。 */
        private String name;
        /** 地点形态枚举编码（院区/楼栋/床位等）。 */
        private String formEnum;
        /** 管理模式枚举编码。 */
        private String modeEnum;
        /** 类型扩展 JSON（与前端/字典约定）。 */
        private String typeJson;
        /** 展示排序。 */
        private Integer displayOrder;
        /** 拼音检索串。 */
        private String pyStr;
        /** 五笔检索串。 */
        private String wbStr;
        /** 所属机构/科室主键（科室药房等须挂靠上级科室时写入）。 */
        private Long organizationId;
        /** 本次实际变更的字段名列表。 */
        private List<String> changedFields;
    }
}
