/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.practitionerrole.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 分配岗位命令：医务人员 + 平台角色 + 科室/位置范围。
 */
@Getter
@Setter
public class AssignPractitionerRoleCommand extends BatchCommand<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> {

    /** 操作人标识（审计）。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class AssignPractitionerRoleItemCommand {
        /** 医务人员主键 {@code adm_practitioner.id}。 */
        private Long practitionerId;
        /** 任职科室/机构单元主键 {@code adm_organization.id}。 */
        private Long orgId;
        /** 任职位置主键 {@code adm_location.id}（可选）。 */
        private Long locationId;
        /** 平台角色主键 {@code whale_roles.id}；与 {@link #roleCode} 二选一，优先本字段。 */
        private Long roleId;
        /** 兼容旧前端 API：1=医生 2=护士 3=药师 4=库管 6=行政(财务)。 */
        private Integer roleCode;
        /** 岗位展示名称（可选覆盖）。 */
        private String name;
        /** 专业/专科编码（字典）。 */
        private String specialtyCode;
        /** 岗位类型编码（字典）。 */
        private Integer typeCode;
        /** 可开立的医疗服务主键列表 {@code adm_healthcare_service.id}。 */
        private List<Long> serviceIds;
        /** 出诊/排班时段列表。 */
        private List<AvailabilityScheduleCommand> scheduleItems;
    }
}
