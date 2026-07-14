/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop.insurance.workinjury;

import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryReconciliationExtension;

/**
 * 未装配 wi-jilin（或任意第三方工伤对账 Module）时的默认 {@link WorkInjuryReconciliationExtension}。
 *
 * <p>接口 default 方法均返回空/false；工伤对账功能不可用，现网纯自费路径不受影响。</p>
 *
 * <p>吉林 Module 启用时由 {@code WorkInjuryReconciliationExtensionImpl}（{@code @Primary}）覆盖。</p>
 */
public final class NoOpWorkInjuryReconciliationExtension implements WorkInjuryReconciliationExtension {
}
