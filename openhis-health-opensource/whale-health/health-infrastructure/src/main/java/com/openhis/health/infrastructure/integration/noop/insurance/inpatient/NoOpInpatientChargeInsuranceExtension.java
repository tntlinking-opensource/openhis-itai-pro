/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop.insurance.inpatient;

import com.openhis.health.infrastructure.integration.insurance.inpatient.InpatientChargeInsuranceExtension;

/**
 * 未装配 chs-jilin（或任意第三方住院结算 Module）时的默认 {@link InpatientChargeInsuranceExtension}。
 *
 * <p>{@link InpatientChargeInsuranceExtension#isEnabled()} 继承接口默认 false；AppService 据此走纯自费住院路径，
 * 不抛 {@link UnsupportedOperationException}。</p>
 *
 * <p>吉林 Module 启用时由 {@code InpatientChargeInsuranceExtensionImpl}（{@code @Primary}）覆盖。</p>
 */
public final class NoOpInpatientChargeInsuranceExtension implements InpatientChargeInsuranceExtension {
}
