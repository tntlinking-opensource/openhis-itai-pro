/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.support.insurance.outpatient;

import com.openhis.health.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;

/**
 * 未装配 chs-jilin（或任意第三方挂号登记 Module）时的默认 {@link OutpatientRegistrationInsuranceExtension}。
 *
 * <p>{@link OutpatientRegistrationInsuranceExtension#isEnabled()} 继承接口默认 false；AppService 跳过 2201 平台登记，
 * 不抛 {@link UnsupportedOperationException}。</p>
 *
 * <p>吉林 Module 启用时由 {@code OutpatientRegistrationInsuranceExtensionImpl}（{@code @Primary}）覆盖。</p>
 */
public final class NoOpOutpatientRegistrationInsuranceExtension implements OutpatientRegistrationInsuranceExtension {
}
