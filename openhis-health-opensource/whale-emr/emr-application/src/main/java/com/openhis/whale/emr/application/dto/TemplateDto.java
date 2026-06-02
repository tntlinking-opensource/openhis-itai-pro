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

package com.openhis.whale.emr.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 电子病历模板 DTO
 */
@Getter
@Setter
public class TemplateDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String code;
    private String documentTypeCode;
    private String documentTypeName;
    /** PERSONAL | DEPARTMENT | HOSPITAL */
    private String scopeLevel;
    private String scopeLevelName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;
    private String organizationName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerPractitionerId;
    /** AMB | IMP | EMER */
    private String encounterClassCode;
    private String encounterClassName;
    private Integer encounterClassEnum;
    private Integer encounterTypeEnum;
    private String content;
    private Boolean enabled;
    /** 与前端 isEnabled 字段对齐 */
    private Boolean isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updatedBy;
}
