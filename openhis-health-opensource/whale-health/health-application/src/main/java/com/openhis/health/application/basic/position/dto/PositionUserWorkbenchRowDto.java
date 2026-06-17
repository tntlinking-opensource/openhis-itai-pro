/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.basic.position.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 岗位工作台左侧：用户 + 系统角色 + 医务人员主档摘要一行。
 */
@Data
public class PositionUserWorkbenchRowDto {

    /** 系统用户主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    /** 登录账号。 */
    private String userName;
    /** 用户显示姓名。 */
    private String name;
    /** 系统角色展示名，顿号拼接 */
    private String roleNames;
    /** 用户是否启用。 */
    private Boolean isActive;

    /** 关联医务人员主档主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;
    /** 医务人员姓名。 */
    private String practitionerName;
    /** 医务人员业务号。 */
    private String busNo;
    /** adm_practitioner.status_enum */
    private String practitionerStatusEnum;
}
