/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.catalog.request;

import lombok.Data;

import java.util.List;

/**
 * 保存体检项目（ActivityDefinition + 小项链接）请求（CFG-04）。
 */
@Data
public class SaveActivityRequest {

    /** 项目主键；更新时必填。 */
    private Long activityId;

    /** 项目编码（更新时可选，对应 busNo 展示）。 */
    private String code;

    /** 项目名称。 */
    private String display;

    /** 项目类型（ActivityType.code）。 */
    private String typeEnum;

    /** 状态：active / inactive（映射 PublicationStatus）。 */
    private String statusEnum;

    /** 绑定小项主键列表。 */
    private List<Long> observationDefinitionIds;

    /** 执行科室机构主键。 */
    private Long orgId;

    /** 适用性别（BOTH/MALE/FEMALE）。 */
    private String permittedGenderEnum;

    /** 目录展示顺序。 */
    private Integer sortOrder;
}
