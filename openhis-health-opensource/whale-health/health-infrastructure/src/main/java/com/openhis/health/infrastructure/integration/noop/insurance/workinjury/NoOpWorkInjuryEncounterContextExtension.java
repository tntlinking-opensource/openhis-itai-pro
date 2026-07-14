/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop.insurance.workinjury;

import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryEncounterContextExtension;

/**
 * 未装配 wi-jilin 时的默认 {@link WorkInjuryEncounterContextExtension}。
 *
 * <p>接口 default 方法返回空上下文；收银/登记无法挂载工伤就诊快照，走非工伤路径。</p>
 *
 * <p>吉林 Module 启用时由 {@code WorkInjuryEncounterContextExtensionImpl}（{@code @Primary}）覆盖。</p>
 */
public final class NoOpWorkInjuryEncounterContextExtension implements WorkInjuryEncounterContextExtension {
}
