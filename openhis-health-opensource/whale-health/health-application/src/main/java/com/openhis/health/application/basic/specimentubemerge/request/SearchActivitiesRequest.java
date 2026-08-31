/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimentubemerge.request;

import lombok.Data;

/**
 * 检验类诊疗项目关键字检索请求。
 */
@Data
public class SearchActivitiesRequest {

    /** 关键字（名称/编码/拼音/五笔）。 */
    private String keyword;

    /** 返回条数上限；默认 50。 */
    private Integer limit;
}
