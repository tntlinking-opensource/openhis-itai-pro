/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.port;

/**
 * 标准落地包存储端口（领域定义；本地/对象存储由应用或 bridge 实现）。
 */
public interface CatalogNoticePackStorePort {

    /**
     * 持久化标准落地包字节并返回可回读 URI。
     *
     * @param tenantId 租户
     * @param hash     内容 SHA-256（用于路径分区）
     * @param fileName 建议文件名
     * @param bytes    包内容
     * @return 存储 URI（如 {@code file:/...}）
     */
    String storeCanonicalPack(Long tenantId, String hash, String fileName, byte[] bytes);

    /**
     * 按 URI 加载标准落地包字节。
     *
     * @param canonicalPackUri 存储 URI
     * @return 包内容
     */
    byte[] loadCanonicalPack(String canonicalPackUri);
}
