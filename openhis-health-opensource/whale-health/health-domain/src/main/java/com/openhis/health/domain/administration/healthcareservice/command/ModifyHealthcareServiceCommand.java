/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.healthcareservice.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 修改医疗服务命令（批量）。
 * <p>
 * 用于变更已有医疗服务的名称、类型、分类或提供机构等信息，可配合变更字段列表做增量更新或审计。
 */
@Getter
@Setter
public class ModifyHealthcareServiceCommand extends BatchCommand<ModifyHealthcareServiceCommand.Item> {
    /** 执行本次批量修改的操作人标识。 */
    private Long operatorId;
    /**
     * 单条修改项：目标服务标识与待更新字段。
     */
    @Getter
    @Setter
    public static class Item {
        /** 被修改的医疗服务主键。 */
        private Long serviceId;
        /** 医疗服务显示名称。 */
        private String name;
        /** 服务类型字典编码。 */
        private String typeCode;
        /** 服务分类字典编码。 */
        private String categoryCode;
        /** 提供该服务的组织机构主键。 */
        private Long offeredOrgId;
        /** 医保目录编码或对照码。 */
        private String ybNo;
        /** 本次实际发生变更的字段名列表（用于部分更新或留痕）。 */
        private List<String> changedFields;
    }
}
