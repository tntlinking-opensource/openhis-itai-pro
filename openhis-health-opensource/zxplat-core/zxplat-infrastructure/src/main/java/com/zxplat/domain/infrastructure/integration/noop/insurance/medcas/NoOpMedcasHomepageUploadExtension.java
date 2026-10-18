/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.insurance.medcas;

import com.zxplat.domain.infrastructure.integration.insurance.medcas.MedcasHomepageUploadExtension;

/**
 * 未装配 chs-jilin（或任意第三方病案首页 Module）时的默认 {@link MedcasHomepageUploadExtension}。
 *
 * <p>{@link MedcasHomepageUploadExtension#isEnabled()} 继承接口默认 false；出院病案首页不上传医保平台。</p>
 *
 * <p>吉林 Module 启用时由 {@code MedcasHomepageUploadExtensionImpl}（{@code @Primary}）覆盖。</p>
 */
public final class NoOpMedcasHomepageUploadExtension implements MedcasHomepageUploadExtension {
}
