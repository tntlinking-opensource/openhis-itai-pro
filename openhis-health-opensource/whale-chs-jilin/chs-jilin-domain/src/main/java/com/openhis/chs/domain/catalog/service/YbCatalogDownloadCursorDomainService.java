/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.service;

import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.openhis.chs.domain.catalog.repository.YbCatalogDownloadCursorRepository;
import com.openhis.chs.domain.catalog.support.CatalogDownloadStartVerSupport;
import com.openhis.chs.domain.catalog.support.CatalogDownloadVersionSupport;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 医保目录下载游标写侧（表 {@code yb_catalog_download_cursor}）。
 *
 * <p>每次成功处理一个版本 TXT 后<b>追加插入</b>一行快照（不 update），便于对照排查。
 * 下次 1301 起点取同 contract+infno+tenant 下 {@code last_success_at} 最新一条的 {@code local_ver}。</p>
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
     * @param verBranch 1305 分支 F002/F003，其它 infno 可空
     */
    public String resolveStartLocalVer(Long contractId, String infno, Long tenantId, String verBranch) {
        String cursor = findLatest(contractId, infno, tenantId)
            .map(YbCatalogDownloadCursor::getLocalVer)
            .filter(StringUtils::isNotBlank)
            .orElse(null);
        return CatalogDownloadStartVerSupport.resolveStartVer(infno, verBranch, false, null, cursor);
    }

    public java.util.Optional<YbCatalogDownloadCursor> findLatest(
            Long contractId, String infno, Long tenantId) {
        if (contractId == null || tenantId == null || StringUtils.isBlank(infno)) {
            return java.util.Optional.empty();
        }
        return repository.findFirstByContractIdAndInfnoAndTenantIdAndIsDeletedFalseOrderByLastSuccessAtDescIdDesc(
            contractId, infno.trim(), tenantId);
    }

    /**
     * 每成功入库一个版本 TXT 后追加一行游标快照。
     *
     * @param localVerAfterImport 本版本处理完成后的本地最大版本（通常为本包 TXT「版本名称」）
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
        if (StringUtils.isNotBlank(localVerAfterImport)) {
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
