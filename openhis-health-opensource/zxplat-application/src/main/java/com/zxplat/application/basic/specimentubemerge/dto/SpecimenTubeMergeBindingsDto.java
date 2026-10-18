/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.specimentubemerge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备合管绑定详情 DTO（设备下多组；与前端 getBindings 契约对齐）。
 */
@Data
public class SpecimenTubeMergeBindingsDto {

    /** 检验设备主键 {@code adm_device.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deviceId;

    /** 设备名称。 */
    private String deviceName;

    /** 设备业务编码。 */
    private String deviceBusNo;

    /** 设备下合管组列表（按 groupNo 升序）。 */
    private List<SpecimenTubeMergeGroupDto> groups = new ArrayList<>();
}
