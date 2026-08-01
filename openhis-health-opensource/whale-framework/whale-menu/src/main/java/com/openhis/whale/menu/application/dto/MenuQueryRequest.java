/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.menu.application.dto;

import com.openhis.whale.core.application.dto.BaseQueryRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuQueryRequest extends BaseQueryRequest {

    private String keyword;
    private Long parentId;
    private String menuType;
    private Boolean isEnabled;
    private Boolean isHidden;
}
