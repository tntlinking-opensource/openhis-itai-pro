/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.lis;

import com.zxplat.domain.infrastructure.integration.lis.LisAdviceApplyExtension;

/**
 * 未装配 LIS Module 时的默认 {@link LisAdviceApplyExtension}。
 *
 * <p>{@link LisAdviceApplyExtension#isEnabled()} 继承接口默认 false；检验医嘱签发后不推送 LIS 申请，
 * 本地医嘱与收费不受影响。</p>
 */
public final class NoOpLisAdviceApplyExtension implements LisAdviceApplyExtension {
}
