/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.service;

import com.zxplat.framework.core.application.util.StringUtils;
import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import com.zxplat.plugin.chs.domain.share.constants.ChsCatalogConstants;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.zxplat.plugin.chs.domain.catalog.repository.YbCatalogDownloadCursorRepository;
import com.zxplat.plugin.chs.domain.catalog.support.CatalogDownloadStartVerSupport;
import com.zxplat.plugin.chs.domain.catalog.support.CatalogDownloadVersionSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 医保目录下载游标写侧（表 {@code yb_catalog_download_cursor}）
 *
 * <p>每次成功处理一个版本 TXT 后<b>追加插入</b>一行快照，便于对照排查。
 * 下次起点：同 contract+infno+tenant；1305 另按 F002/F003 分支前缀取最新 {@code local_ver}。</p>
 */
@Service
@RequiredArgsConstructor
public class YbCatalogDownloadCursorDomainService {

    private final YbCatalogDownloadCursorRepository repository;

    /**
     * 下次文件型目录的 {@code data.ver}：最新游标快照的 local_ver，无则按 infno 取规范初始值。
     */
    public String resolveStartLocalVer(Long contractId, String infno, Long tenantId) {
        return resolveStartLocalVer(contractId, infno, tenantId, null);
    }

    /**
     * @param verBranch 1305 分支 F002/F003；其它 infno 可空
     */
    public String resolveStartLocalVer(Long contractId, String infno, Long tenantId, String verBranch) {
        String cursor = findLatest(contractId, infno, tenantId, verBranch)
            .map(YbCatalogDownloadCursor::getLocalVer)
            .filter(StringUtils::isNotEmpty)
            .orElse(null);
        return CatalogDownloadStartVerSupport.resolveStartVer(infno, verBranch, false, null, cursor);
    }

    /**
     * 查询契约+infno 下最近一次成功游标快照（不区分 1305 分支）。
     *
     * @return 最新游标，参数无效时 empty
     */
    public Optional<YbCatalogDownloadCursor> findLatest(
            Long contractId, String infno, Long tenantId) {
        return findLatest(contractId, infno, tenantId, null);
    }

    /**
     * 查询最新游标；{@code infno=1305} 时按 {@code verBranch}（默认 F002）前缀过滤。
     *
     * @param verBranch 1305 分支；其它 infno 忽略
     */
    public Optional<YbCatalogDownloadCursor> findLatest(
            Long contractId, String infno, Long tenantId, String verBranch) {
        if (contractId == null || tenantId == null || isEmpty(infno)) {
            return Optional.empty();
        }
        String trimmedInfno = infno.trim();
        if (ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD.equals(trimmedInfno)) {
            String branch = CatalogDownloadStartVerSupport.canonical1305Branch(verBranch);
            return repository
                .findFirstByContractIdAndInfnoAndTenantIdAndIsDeletedFalseAndLocalVerStartingWithOrderByLastSuccessAtDescIdDesc(
                    contractId, trimmedInfno, tenantId, branch);
        }
        return repository.findFirstByContractIdAndInfnoAndTenantIdAndIsDeletedFalseOrderByLastSuccessAtDescIdDesc(
            contractId, trimmedInfno, tenantId);
    }

    /**
     * 每成功入库一个版本 TXT 后追加一行游标快照。
     *
     * @param localVerAfterImport 本版本处理完成后的本地最大版本（通常为本 TXT「版本名称」）
     */
    public YbCatalogDownloadCursor appendSnapshot(
            Long contractId,
            String infno,
            Long tenantId,
            String localVerAfterImport,
            String fileQuryNo,
            String filename,
            Long operatorId) {
        YbCatalogDownloadCursor cursor = new YbCatalogDownloadCursor();
        cursor.setContractId(contractId);
        cursor.setInfno(infno.trim());
        cursor.setTenantId(tenantId);
        cursor.setIsDeleted(false);
        cursor.setCreatedBy(operatorId);
        if (isNotEmpty(localVerAfterImport)) {
            cursor.setLocalVer(CatalogDownloadVersionSupport.normalizeForCursor(localVerAfterImport));
        } else {
            cursor.setLocalVer(ChsCatalogConstants.DEFAULT_CATALOG_DOWNLOAD_VER);
        }
        cursor.setLastFileQuryNo(fileQuryNo);
        cursor.setLastFilename(filename);
        cursor.setLastSuccessAt(LocalDateTime.now());
        cursor.setUpdatedBy(operatorId);
        return repository.save(cursor);
    }
}
