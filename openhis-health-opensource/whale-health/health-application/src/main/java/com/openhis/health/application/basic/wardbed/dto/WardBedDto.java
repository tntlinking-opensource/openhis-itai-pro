/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.wardbed.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 病区/病房/床位位置展示 DTO，可嵌套子床位与父级快照。
 */
@Data
public class WardBedDto {
    /** 位置主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 业务编号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** 状态枚举 code。 */
    private String statusEnum;
    /** 状态展示文案。 */
    private String statusEnumText;
    /** 物理形态枚举 code。 */
    private String formEnum;
    /** 物理形态展示文案。 */
    private String formEnumText;
    /** 所属机构主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;
    /** 父位置主键（病房/床位挂靠）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;
    /** 显示顺序。 */
    private Integer displayOrder;
    /** 子位置（如病房下的床位）。 */
    private List<WardBedDto> children;
    /** 父位置快照（如床位所属病房）。 */
    private WardBedDto parent;
}
