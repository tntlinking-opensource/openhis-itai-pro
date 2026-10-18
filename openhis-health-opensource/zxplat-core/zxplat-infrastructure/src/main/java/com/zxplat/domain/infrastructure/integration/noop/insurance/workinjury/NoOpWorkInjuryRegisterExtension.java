/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.insurance.workinjury;

import com.zxplat.domain.infrastructure.integration.workinjury.WorkInjuryRegisterExtension;

/**
 * 未装配 wi-jilin（或任意第三方工伤登记 Module）时的默认 {@link WorkInjuryRegisterExtension}。
 *
 * <p>接口 default 方法均返回空/false；工伤登记 AppService 跳过平台交互，不抛 {@link UnsupportedOperationException}。</p>
 *
 * <p>吉林 Module 启用时由 {@code WorkInjuryRegisterExtensionImpl}（{@code @Primary}）覆盖。</p>
 */
public final class NoOpWorkInjuryRegisterExtension implements WorkInjuryRegisterExtension {
}
