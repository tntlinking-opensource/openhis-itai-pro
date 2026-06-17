/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.domain.administration.practitionerrole.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
/**
 * 按医务人员 + 平台角色全量替换岗位分配集合。
 */
public class ReplacePractitionerRolesByTypeCommand {

    private Long practitionerId;
    /** 平台角色主键 {@code whale_roles.id}。 */
    private Long roleId;
    private List<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> items;
    private Long operatorId;
}
