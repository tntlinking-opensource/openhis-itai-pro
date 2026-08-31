/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimentubemerge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备合管绑定详情 DTO（与前端 getBindings 契约对齐）。
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

    /** 是否绑定采血费 activity。 */
    private Boolean bindBloodCollectionFee;

    /** 检验项目 activity 主键（不含采血费）。 */
    private List<String> activityIds = new ArrayList<>();

    /** 耗材绑定行。 */
    private List<SpecimenTubeMergeConsumableLineDto> consumableLines = new ArrayList<>();
}
