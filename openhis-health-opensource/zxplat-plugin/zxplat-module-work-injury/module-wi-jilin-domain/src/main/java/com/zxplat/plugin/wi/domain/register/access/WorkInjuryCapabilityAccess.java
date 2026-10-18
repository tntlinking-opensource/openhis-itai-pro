/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.register.access;

import com.zxplat.plugin.wi.domain.register.result.WorkInjuryCapabilityResult;

/** 工伤模块 capability 查询端口。 */
public interface WorkInjuryCapabilityAccess {

    WorkInjuryCapabilityResult capability();
}
