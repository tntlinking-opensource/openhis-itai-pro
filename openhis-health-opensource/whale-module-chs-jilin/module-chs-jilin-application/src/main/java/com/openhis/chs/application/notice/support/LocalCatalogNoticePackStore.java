/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.support;

import com.openhis.chs.domain.notice.port.CatalogNoticePackStorePort;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地目录实现标准落地包存取（M1；后续可由对象存储替换，仍实现同一 Port）。
 * <p>扫描源配置优先 {@code openhis.chs.notice.canonical-root}，兼容 {@code chs.notice.canonical-root}。</p>
 */
@Component
public class LocalCatalogNoticePackStore implements CatalogNoticePackStorePort {

    private final Path rootDir;
    private final String canonicalRootConfigured;

    public LocalCatalogNoticePackStore(
            @Value("${openhis.chs.notice.pack-store-dir:${chs.notice.pack-store-dir:}}") String configuredRoot,
            @Value("${openhis.chs.notice.canonical-root:${chs.notice.canonical-root:}}") String canonicalRoot) {
        String root = configuredRoot;
        // 未配置时落到用户目录，便于本地试跑无需改 yml
        if (root == null || root.isBlank()) {
            root = System.getProperty("user.home") + "/.openhis/chs-notice-packs";
        }
        this.rootDir = Paths.get(root).toAbsolutePath().normalize();
        if (canonicalRoot == null) {
            this.canonicalRootConfigured = "";
        } else {
            this.canonicalRootConfigured = canonicalRoot.trim();
        }
    }

    /**
     * 解析扫描根目录：优先请求覆盖，否则用配置 {@code openhis.chs.notice.canonical-root}。
     *
     * @param scanRootOverride 请求级路径；可空
     * @return 规范化绝对路径
     */
    public Path resolveCanonicalRoot(String scanRootOverride) {
        String root = scanRootOverride;
        if (root == null || root.isBlank()) {
            root = canonicalRootConfigured;
        }
        if (root == null || root.isBlank()) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_FILE_UNSUPPORTED,
                "请配置 openhis.chs.notice.canonical-root（标准落地包根目录）");
        }
        Path path = Paths.get(root).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_FILE_UNSUPPORTED,
                "扫描根目录不存在或不是目录：" + path);
        }
        return path;
    }

    /**
     * 配置中的扫描根目录原文（可能为空，供 UI 提示）。
     *
     * @return 配置值；未配置时为空串
     */
    public String getConfiguredCanonicalRoot() {
        return canonicalRootConfigured;
    }

    /**
     * 将包写入租户/hash 分区目录，返回 file URI。
     *
     * @param tenantId 租户
     * @param hash     内容 hash
     * @param fileName 建议文件名
     * @param bytes    字节
     * @return file URI
     */
    @Override
    public String storeCanonicalPack(Long tenantId, String hash, String fileName, byte[] bytes) {
        if (tenantId == null || hash == null || hash.isBlank() || bytes == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_STORE_FAILED, "存储参数不完整");
        }
        String safeName = fileName;
        if (safeName == null || safeName.isBlank()) {
            safeName = hash + ".zip";
        }
        // 去掉路径分隔，防止穿越
        safeName = safeName.replace('\\', '_').replace('/', '_');
        Path dir = rootDir.resolve(String.valueOf(tenantId)).resolve(hash);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(safeName);
            Files.write(target, bytes);
            return target.toUri().toString();
        } catch (IOException ex) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_STORE_FAILED,
                "写入本地标准包失败：" + ex.getMessage(),
                ex);
        }
    }

    /**
     * 从 file URI 或绝对路径读取字节。
     *
     * @param canonicalPackUri 存储 URI
     * @return 包内容
     */
    @Override
    public byte[] loadCanonicalPack(String canonicalPackUri) {
        if (canonicalPackUri == null || canonicalPackUri.isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_STORE_FAILED, "标准包 URI 为空");
        }
        try {
            Path path;
            if (canonicalPackUri.startsWith("file:")) {
                path = Paths.get(URI.create(canonicalPackUri));
            } else {
                path = Paths.get(canonicalPackUri);
            }
            if (!Files.isRegularFile(path)) {
                throw new BusinessException(
                    ChsErrorCodes.NOTICE_STORE_FAILED,
                    "标准包文件不存在：" + canonicalPackUri);
            }
            return Files.readAllBytes(path);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_STORE_FAILED,
                "读取标准包失败：" + ex.getMessage(),
                ex);
        }
    }
}
