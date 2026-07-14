/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.ordertemplate.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 组套模板头+行查询模型。
 */
@Getter
@Setter
public class OrderTemplateWithLinesQueryModel {
    /**
     * 组套模板头信息。
     */
    private OrderTemplateHeaderQueryModel header;
    /**
     * 组套模板行列表。
     */
    private List<OrderTemplateLineQueryModel> lines;
}
