/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.policy.response;

import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 放号策略分页响应。
 */
@Getter
@Setter
public class PageReleasePoliciesResponse extends PagedResult<ReleasePolicyDto> {
}
