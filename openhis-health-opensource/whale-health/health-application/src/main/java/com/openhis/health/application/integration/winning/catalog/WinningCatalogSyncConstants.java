/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.integration.winning.catalog;

/**
 * 卫宁目录同步应用层常量。
 */
public final class WinningCatalogSyncConstants {

    private WinningCatalogSyncConstants() {
    }

    /** 同步批次号前缀（按日幂等键的一部分）。 */
    public static final String SYNC_BATCH_PREFIX = "WINNING_CATALOG";

    /**
     * 药耗批号售价行默认批号：现场未从卫宁带回批号时使用，便于急诊开立命中 {@code LOT_NUMBER_PRICE}。
     * <p>现场对接 {@link com.openhis.health.application.integration.winning.catalog.gateway.WinningCatalogGateway} 后建议改为卫宁真实批号。</p>
     */
    public static final String DEFAULT_LOT_NO_FOR_SYNC = "WINNING_DEFAULT";

    public static final String PRODUCT_KIND_MEDICATION = "MEDICATION";
    public static final String PRODUCT_KIND_DEVICE = "DEVICE";
    public static final String PRODUCT_KIND_ACTIVITY = "ACTIVITY";
}
