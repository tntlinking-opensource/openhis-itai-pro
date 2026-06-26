/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.support.insurance.inventory;

import com.openhis.health.integration.insurance.inventory.DispenseTraceGateExtension;

/**
 * 未装配 chs-jilin（或任意第三方追溯门控 Module）时的默认 {@link DispenseTraceGateExtension}。
 *
 * <p>{@link DispenseTraceGateExtension#isEnabled()} 继承接口默认 false；发药 AppService 跳过 3505/3506 前置门控，
 * {@link DispenseTraceGateExtension#assertTraceAllowedForPrepare} 与
 * {@link DispenseTraceGateExtension#assertTraceAllowedForDispense} 均为空操作。</p>
 *
 * <p>吉林 Module 启用时由 {@code DispenseTraceGateExtensionImpl}（{@code @Primary}）覆盖。</p>
 */
public final class NoOpDispenseTraceGateExtension implements DispenseTraceGateExtension {
}
