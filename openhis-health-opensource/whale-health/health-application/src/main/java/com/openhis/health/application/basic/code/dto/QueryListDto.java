/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.code.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 列表查询条件：与 {@link QueryDto} 同形，预留命名为区分分页/列表场景。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryListDto extends QueryDto {}
