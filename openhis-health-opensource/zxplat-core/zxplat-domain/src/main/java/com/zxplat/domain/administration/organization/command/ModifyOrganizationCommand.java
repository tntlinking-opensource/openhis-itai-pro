/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.organization.command;

import com.zxplat.domain.share.command.BatchCommand;
import com.zxplat.domain.share.enums.OrganizationClassification;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 修改机构命令：增量更新机构主数据及医保信息，可携带变更字段列表。
 */
@Getter
@Setter
public class ModifyOrganizationCommand extends BatchCommand<ModifyOrganizationCommand.ModifyOrganizationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条修改项。 */
    @Getter
    @Setter
    public static class ModifyOrganizationItemCommand {
        /** 机构主键。 */
        private Long organizationId;
        /** 机构名称。 */
        private String name;
        /** 机构业务编号。 */
        private String busNo;
        /** 机构分类。 */
        private OrganizationClassification classEnum;
        /** 展示排序。 */
        private Integer displayOrder;
        /** 默认医师主键。 */
        private Long defDoctorId;
        /** 科别。 */
        private String caty;
        /** 输入码。 */
        private InputCodeCommand inputCode;
        /** 医保信息。 */
        private MedicalInsuranceInfoCommand insuranceInfo;
        /** 本次实际变更的字段名列表。 */
        private List<String> changedFields;
    }
}
