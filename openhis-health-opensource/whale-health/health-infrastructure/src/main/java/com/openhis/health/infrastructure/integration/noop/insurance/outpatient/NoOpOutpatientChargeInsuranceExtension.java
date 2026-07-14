/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop.insurance.outpatient;

import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;

/**
 * 未装配 chs-jilin（或任意第三方门诊结算 Module）时的默认 {@link OutpatientChargeInsuranceExtension}。
 *
 * <p>{@link OutpatientChargeInsuranceExtension#isEnabled()} 继承接口默认 false；AppService 据此走纯自费收银，
 * 不抛 {@link UnsupportedOperationException}。</p>
 *
 * <p>吉林 Module 启用时由 {@code OutpatientChargeInsuranceExtensionImpl}（{@code @Primary}）覆盖。</p>
 */
public final class NoOpOutpatientChargeInsuranceExtension implements OutpatientChargeInsuranceExtension {
}
