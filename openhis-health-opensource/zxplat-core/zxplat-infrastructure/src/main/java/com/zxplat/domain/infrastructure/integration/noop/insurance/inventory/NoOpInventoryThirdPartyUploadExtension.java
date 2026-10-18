/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.insurance.inventory;

import com.zxplat.domain.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;

/**
 * 未装配 chs-jilin（或任意第三方进销存 Module）时的默认 {@link InventoryThirdPartyUploadExtension}。
 *
 * <p>{@link InventoryThirdPartyUploadExtension#isEnabled()} 继承接口默认 false；库房/药房 AppService 跳过平台上报，
 * 本地入库/出库/盘点/发药等业务不受影响。</p>
 *
 * <p>吉林 Module 启用时由 {@code InventoryThirdPartyUploadExtensionImpl}（{@code @Primary}）覆盖。</p>
 */
public final class NoOpInventoryThirdPartyUploadExtension implements InventoryThirdPartyUploadExtension {
}
