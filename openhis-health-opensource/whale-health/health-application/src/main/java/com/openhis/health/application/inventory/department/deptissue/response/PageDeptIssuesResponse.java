/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissue.response;

import com.openhis.health.application.inventory.department.deptissue.dto.DeptIssueListRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 科室领用单分页列表响应（IF-DEP-IS-02）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */
public class PageDeptIssuesResponse extends PagedResult<DeptIssueListRowDto> {
}
