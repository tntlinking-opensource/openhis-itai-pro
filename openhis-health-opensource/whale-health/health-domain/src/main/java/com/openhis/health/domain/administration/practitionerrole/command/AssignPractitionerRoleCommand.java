/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.domain.administration.practitionerrole.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
/**
 * 分配岗位命令：医务人员 + 平台角色 + 科室/位置范围。
 */
public class AssignPractitionerRoleCommand extends BatchCommand<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> {

    private Long operatorId;

    @Getter
    @Setter
    public static class AssignPractitionerRoleItemCommand {
        private Long practitionerId;
        private Long orgId;
        private Long locationId;
        /** 平台角色主键 {@code whale_roles.id}；与 {@link #roleCode} 二选一，优先本字段。 */
        private Long roleId;
        /** 兼容旧前端 API：1=医生 2=护士 3=药师 4=库管 6=行政(财务)。 */
        private Integer roleCode;
        private String name;
        private String specialtyCode;
        private Integer typeCode;
        private List<Long> serviceIds;
        private List<AvailabilityScheduleCommand> scheduleItems;
    }
}
