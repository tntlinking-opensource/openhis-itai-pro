/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

import com.alibaba.fastjson2.JSON;
import com.openhis.chs.api.dto.catalog.CatalogFileDownloadOutput;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * 1301 目录 ZIP 本地磁盘缓存：每个「目录版本」一个 ZIP 文件。
 *
 * <p>路径规则（{@code catalogVer} 即阶段 1 每轮 1301 的 {@code data.ver / requestVer}）：</p>
 * <pre>
 *   {baseDir}/{tenantId}/{contractId}/{infno}/versions/{catalogVer}.zip
 *   {baseDir}/{tenantId}/{contractId}/{infno}/versions/{catalogVer}.meta.json
 * </pre>
 *
 * <p>勿用 file_qury_no 作文件名：多版本链中 file_qury_no 可能变化或重复，版本号才是增量单元。</p>
 *
 * <p>配置：{@code openhis.chs.catalog.download-cache-dir} / {@code CHS_CATALOG_ZIP_CACHE_DIR}</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class CatalogDownloadZipCache {

    private static final String VERSIONS_DIR = "versions";
    private static final String META_SUFFIX = ".meta.json";

    private final Path baseDir;

    public CatalogDownloadZipCache(
            @Value("${openhis.chs.catalog.download-cache-dir:#{systemProperties['java.io.tmpdir']}/chs-catalog-zip}")
            String baseDirProperty) {
        this.baseDir = Paths.get(StringUtils.defaultIfBlank(baseDirProperty, "./chs-catalog-zip"));
        log.info("[catalog-zip-cache] baseDir={}", this.baseDir.toAbsolutePath());
    }

    /**
     * @param catalogVer 本轮 1301 请求的本地版本号（requestVer），非 file_qury_no
     */
    public boolean exists(Long tenantId, Long contractId, String infno, String catalogVer) {
        Path path = resolveZipPath(tenantId, contractId, infno, catalogVer);
        boolean ok = Files.isRegularFile(path);
        log.debug("[catalog-zip-cache] exists={} catalogVer={} path={}", ok, catalogVer, path);
        return ok;
    }

    public Optional<byte[]> read(Long tenantId, Long contractId, String infno, String catalogVer) {
        Path path = resolveZipPath(tenantId, contractId, infno, catalogVer);
        if (!Files.isRegularFile(path)) {
            log.debug("[catalog-zip-cache] miss catalogVer={} path={}", catalogVer, path);
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            log.debug("[catalog-zip-cache] hit catalogVer={} path={} size={}", catalogVer, path, bytes.length);
            return Optional.of(bytes);
        } catch (IOException ex) {
            log.warn("[catalog-zip-cache] 读取失败 path={}", path, ex);
            return Optional.empty();
        }
    }

    public void write(Long tenantId, Long contractId, String infno, String catalogVer, byte[] zipBytes)
            throws IOException {
        if (zipBytes == null || zipBytes.length == 0 || StringUtils.isBlank(catalogVer)) {
            return;
        }
        Path path = resolveZipPath(tenantId, contractId, infno, catalogVer);
        Files.createDirectories(path.getParent());
        Files.write(path, zipBytes);
        log.debug("[catalog-zip-cache] written catalogVer={} path={} size={}", catalogVer, path, zipBytes.length);
    }

    public void writeMeta(
            Long tenantId,
            Long contractId,
            String infno,
            String catalogVer,
            CatalogFileDownloadOutput meta) throws IOException {
        if (meta == null || StringUtils.isBlank(catalogVer)) {
            return;
        }
        Path path = resolveMetaPath(tenantId, contractId, infno, catalogVer);
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.toJSONString(meta), StandardCharsets.UTF_8);
        log.debug("[catalog-zip-cache] meta written catalogVer={} path={}", catalogVer, path);
    }

    public Optional<CatalogFileDownloadOutput> readMeta(
            Long tenantId, Long contractId, String infno, String catalogVer) {
        Path path = resolveMetaPath(tenantId, contractId, infno, catalogVer);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            CatalogFileDownloadOutput meta = JSON.parseObject(json, CatalogFileDownloadOutput.class);
            return Optional.ofNullable(meta);
        } catch (IOException ex) {
            log.warn("[catalog-zip-cache] meta 读取失败 path={}", path, ex);
            return Optional.empty();
        }
    }

    /** 全量重拉前清空该契约+infno 下已缓存的版本 ZIP 及元数据。 */
    public int clearVersions(Long tenantId, Long contractId, String infno) throws IOException {
        Path versionsDir = baseDir
            .resolve(String.valueOf(tenantId))
            .resolve(String.valueOf(contractId))
            .resolve(sanitize(infno))
            .resolve(VERSIONS_DIR);
        if (!Files.isDirectory(versionsDir)) {
            return 0;
        }
        int removed = 0;
        try (var stream = Files.list(versionsDir)) {
            for (Path file : stream.toList()) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String name = file.getFileName().toString();
                if (name.endsWith(".zip") || name.endsWith(META_SUFFIX)) {
                    Files.delete(file);
                    removed++;
                }
            }
        }
        log.info("[catalog-zip-cache] cleared infno={} dir={} removed={}", infno, versionsDir, removed);
        return removed;
    }

    public String resolvePathString(Long tenantId, Long contractId, String infno, String catalogVer) {
        return resolveZipPath(tenantId, contractId, infno, catalogVer).toAbsolutePath().toString();
    }

    private Path resolveZipPath(Long tenantId, Long contractId, String infno, String catalogVer) {
        return resolveVersionsDir(tenantId, contractId, infno).resolve(sanitize(catalogVer) + ".zip");
    }

    private Path resolveMetaPath(Long tenantId, Long contractId, String infno, String catalogVer) {
        return resolveVersionsDir(tenantId, contractId, infno).resolve(sanitize(catalogVer) + META_SUFFIX);
    }

    private Path resolveVersionsDir(Long tenantId, Long contractId, String infno) {
        return baseDir
            .resolve(String.valueOf(tenantId))
            .resolve(String.valueOf(contractId))
            .resolve(sanitize(infno))
            .resolve(VERSIONS_DIR);
    }

    private static String sanitize(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "_";
        }
        return raw.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
