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

package com.openhis.whale.emr.application.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建模板请求
 */
@Getter
@Setter
public class CreateTemplateRequest {

    private String name;
    private String code;
    private String documentTypeCode;
    /** PERSONAL | DEPARTMENT | HOSPITAL */
    private String scopeLevel;
    private Long organizationId;
    private Long ownerPractitionerId;
    /** AMB | IMP | EMER */
    private String encounterClassCode;
    private Integer encounterTypeEnum;
    private String content;
    private Boolean enabled = true;
    /**
     * 医生站「存为模版」：强制个人模板、所有者取当前登录医务人员。
     */
    private Boolean fromDoctorStation;
}
