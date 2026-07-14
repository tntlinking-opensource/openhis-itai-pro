/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop.insurance.workinjury;

import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryOutpatientSettlementExtension;

/**
 * 未装配 wi-jilin（或任意第三方工伤门诊结算 Module）时的默认 {@link WorkInjuryOutpatientSettlementExtension}。
 *
 * <p>接口 default 方法均返回空/false；工伤门诊结算走本地降级，不发起平台 2207 等交易。</p>
 *
 * <p>吉林 Module 启用时由 {@code WorkInjuryOutpatientSettlementExtensionImpl}（{@code @Primary}）覆盖。</p>
 */
public final class NoOpWorkInjuryOutpatientSettlementExtension implements WorkInjuryOutpatientSettlementExtension {
}
