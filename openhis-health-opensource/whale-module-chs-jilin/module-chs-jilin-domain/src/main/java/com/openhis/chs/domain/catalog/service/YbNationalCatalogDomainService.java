/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.service;

import com.openhis.chs.domain.share.enums.ListType;
import com.openhis.whale.core.application.util.StringUtils;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import com.openhis.chs.domain.share.constants.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import com.openhis.chs.domain.catalog.repository.YbNationalCatalogItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 国家医保目录镜像写侧312 / 13xx 文件名upsert） *
 * <h3>唯一键（调试重复/覆盖问题时对照）</h3>
 * <ul>
 *   <li><b>13xx 文件名/b>301/1302/1305/1306/1307…）：{@code source_file_qury_no + source_line_no}</li>
 *   <li><b>1312</b>：{@code hilist_code}（source_line_no 为空/li>
 * </ul>
 *
 * <p>文件包首次入库走INSERT；重导走 PostgreSQL {@code ON CONFLICT DO UPDATE} 批量写入。</p> */
@Service
@RequiredArgsConstructor
public class YbNationalCatalogDomainService {

    private final YbNationalCatalogItemRepository repository;
    private final YbNationalCatalogBatchWriter batchWriter;

    /**
     * 1312 等平台拉取后的批upsert（按 {@link ChsCatalogConstants#CATALOG_UPSERT_BATCH_SIZE} 分批提交）     *
     * @return 实际处理的行数（跳过 hilist_code 为空的命令） */
    public int upsertRows(Long contractId, Long tenantId, Long operatorId,
                          List<UpsertNationalCatalogRowCommand> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int batchSize = ChsCatalogConstants.CATALOG_UPSERT_BATCH_SIZE;
        int count = 0;
        List<UpsertNationalCatalogRowCommand> pending = new ArrayList<>(batchSize);
        for (UpsertNationalCatalogRowCommand row : rows) {
            if (row == null || isEmpty(row.getHilistCode())) {
                continue;
            }
            pending.add(row);
            count++;
            if (pending.size() >= batchSize) {
                flush1312Batch(contractId, tenantId, operatorId, pending);
                pending.clear();
            }
        }
        if (!pending.isEmpty()) {
            flush1312Batch(contractId, tenantId, operatorId, pending);
        }
        return count;
    }

    /**
     * 13xx ZIP 包是否已入库（按 file_qury_no 判定，每包只查一次） */
    public boolean existsFilePackage(
            Long contractId, Long tenantId, String listType, String sourceFileQuryNo) {
        if (isEmpty(sourceFileQuryNo)) {
            return false;
        }
        return repository.existsByContractIdAndListTypeAndSourceFileQuryNoAndTenantIdAndIsDeletedFalse(
            contractId,
            StringUtils.defaultIfBlank(listType, ListType.WESTERN_DRUG.getCode()),
            sourceFileQuryNo.trim(),
            tenantId);
    }

    /**
     * 13xx 流式解析的单批入库（每批独立事务）     *
     * @param freshPackage {@code true} 表示file_qury_no 首次入库，走INSERT */
    public int upsertFilePackageBatch(
            Long contractId,
            Long tenantId,
            Long operatorId,
            String sourceFileQuryNo,
            List<UpsertNationalCatalogRowCommand> batch,
            boolean freshPackage) {
        if (batch == null || batch.isEmpty()) {
            return 0;
        }
        if (freshPackage) {
            return batchWriter.writeFilePackageFreshBatch(
                contractId, tenantId, operatorId, sourceFileQuryNo, batch);
        }
        return batchWriter.writeFilePackageUpsertBatch(
            contractId, tenantId, operatorId, sourceFileQuryNo, batch);
    }

    /**
     * 13xx 整包入库（非流式场景兼容入口） */
    public int upsertFilePackageRows(
            Long contractId,
            Long tenantId,
            Long operatorId,
            String sourceFileQuryNo,
            String listType,
            List<UpsertNationalCatalogRowCommand> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        boolean fresh = !existsFilePackage(contractId, tenantId, listType, sourceFileQuryNo);
        int batchSize = ChsCatalogConstants.CATALOG_UPSERT_BATCH_SIZE;
        int count = 0;
        List<UpsertNationalCatalogRowCommand> pending = new ArrayList<>(batchSize);
        for (UpsertNationalCatalogRowCommand row : rows) {
            if (row == null || isEmpty(row.getHilistCode())) {
                continue;
            }
            row.setSourceFileQuryNo(sourceFileQuryNo);
            pending.add(row);
            count++;
            if (pending.size() >= batchSize) {
                upsertFilePackageBatch(contractId, tenantId, operatorId, sourceFileQuryNo, pending, fresh);
                pending.clear();
            }
        }
        if (!pending.isEmpty()) {
            upsertFilePackageBatch(contractId, tenantId, operatorId, sourceFileQuryNo, pending, fresh);
        }
        return count;
    }

    /** 1312 单批 upsert 提交 */
    private void flush1312Batch(
            Long contractId,
            Long tenantId,
            Long operatorId,
            List<UpsertNationalCatalogRowCommand> batch) {
        batchWriter.write1312UpsertBatch(contractId, tenantId, operatorId, batch);
    }
}
