/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitionerrole.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 按医务人员 + 平台角色全量替换岗位分配集合。
 */
@Getter
@Setter
public class ReplacePractitionerRolesByTypeCommand {

    /** 医务人员主键 {@code adm_practitioner.id}，替换范围锚点。 */
    private Long practitionerId;
    /** 平台角色主键 {@code zxplat_roles.id}。 */
    private Long roleId;
    /** 目标岗位分配行（科室/地点/服务项目）；全量覆盖旧集合。 */
    private List<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> items;
    /** 操作人主键（审计）。 */
    private Long operatorId;
}
