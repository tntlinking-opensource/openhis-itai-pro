/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.healthcareservice.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建医疗服务命令（批量）。
 * <p>
 * 用于登记新的医疗服务项，包括名称、类型、分类、提供机构及诊查费 AD 绑定串。
 */
@Getter
@Setter
public class CreateHealthcareServiceCommand extends BatchCommand<CreateHealthcareServiceCommand.Item> {
    /** 执行本次批量创建的操作人标识。 */
    private Long operatorId;
    /**
     * 单条创建项：新医疗服务的基础属性。
     */
    @Getter
    @Setter
    public static class Item {
        /** 医疗服务显示名称。 */
        private String name;
        /** 服务类型字典编码。 */
        private String typeCode;
        /** 服务分类字典编码。 */
        private String categoryCode;
        /** 提供该服务的组织机构主键。 */
        private Long offeredOrgId;
        /**
         * 诊查费 ActivityDefinition 主键 CSV；零元号可空。
         * <p>对齐 DM-ADM-007 {@code activity_ids}；禁止再写业务 yb_no。</p>
         */
        private String activityIds;
    }
}
