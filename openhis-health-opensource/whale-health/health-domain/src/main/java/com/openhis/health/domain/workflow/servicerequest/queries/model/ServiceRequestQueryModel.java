/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 服务申请单条加载查询模型。
 */
@Getter
@Setter
public class ServiceRequestQueryModel {
    /** 主键。 */
    private Long id;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 类别枚举编码。 */
    private String categoryEnum;
}
