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
/**
 * PositionUserWorkbenchRowDto。
 */
public class PositionUserWorkbenchRowDto {

    @JsonSerialize(using = ToStringSerializer.class)
    /**
     * userId。
     */
    private Long userId;
    /**
     * userName。
     */
    private String userName;
    /**
     * name。
     */
    private String name;
    /** 系统角色展示名，顿号拼接 */
    /**
     * roleNames。
     */
    private String roleNames;
    /**
     * isActive。
     */
    private Boolean isActive;

    @JsonSerialize(using = ToStringSerializer.class)
    /**
     * practitionerId。
     */
    private Long practitionerId;
    /**
     * practitionerName。
     */
    private String practitionerName;
    /**
     * busNo。
     */
    private String busNo;
    /** adm_practitioner.status_enum */
    /**
     * practitionerStatusEnum。
     */
    private String practitionerStatusEnum;
}
