/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import com.zxplat.plugin.peis.application.group.dto.CompanyTreeNodeDto;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 团检单位树响应。
 */
@Getter
@Setter
public class ListCompanyTreeResponse {

    /** 根节点列表。 */
    private List<CompanyTreeNodeDto> roots = new ArrayList<>();
}
