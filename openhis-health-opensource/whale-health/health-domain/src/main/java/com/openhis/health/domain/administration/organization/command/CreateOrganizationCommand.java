/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.organization.command;

import com.openhis.health.domain.share.command.BatchCommand;
import com.openhis.health.domain.share.enums.OrganizationClassification;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建机构命令：登记组织机构树节点，可附带输入码与医保定点信息。
 */
@Getter
@Setter
public class CreateOrganizationCommand extends BatchCommand<CreateOrganizationCommand.CreateOrganizationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条创建项。 */
    @Getter
    @Setter
    public static class CreateOrganizationItemCommand {
        /** 机构名称。 */
        private String name;
        /** 机构业务编号（可选，不传则由领域自动生成）。 */
        private String busNo;
        /** 父机构主键。 */
        private Long parentId;
        /** 机构类型枚举编码。 */
        private String typeEnum;
        /** 机构分类（临床/行政等）。 */
        private OrganizationClassification classEnum;
        /** 同级展示排序。 */
        private Integer displayOrder;
        /** 默认医师主键（可空）。 */
        private Long defDoctorId;
        /** 科别。 */
        private String caty;
        /** 拼音/五笔等输入码。 */
        private InputCodeCommand inputCode;
        /** 医保定点信息。 */
        private MedicalInsuranceInfoCommand insuranceInfo;
    }
}
