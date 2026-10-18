/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.register.access;

import com.zxplat.plugin.wi.domain.register.result.WorkInjuryEncounterContextResult;

/** 就诊维度工伤上下文查询端口。 */
public interface WorkInjuryEncounterContextAccess {

    WorkInjuryEncounterContextResult resolveContext(Long encounterId);
}
