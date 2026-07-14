/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop.insurance.benefit;

import com.openhis.health.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;

/**
 * 未装配 chs-jilin（或任意第三方待遇查询 Module）时的默认 {@link InsuranceRegisterBenefitQueryExtension}。
 *
 * <p>{@link InsuranceRegisterBenefitQueryExtension#isEnabled()} 与
 * {@link InsuranceRegisterBenefitQueryExtension#supportsThirdPartyContract(Long)} 均继承接口默认 false；
 * 挂号待遇预览/确认走本地契约，不发起 1101/2001 等平台查询。</p>
 *
 * <p>吉林 Module 启用时由 {@code InsuranceRegisterBenefitQueryExtensionImpl}（{@code @Primary}）覆盖。</p>
 */
public final class NoOpInsuranceRegisterBenefitQueryExtension implements InsuranceRegisterBenefitQueryExtension {
}
