/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.service;

import com.zxplat.plugin.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import com.zxplat.plugin.chs.domain.catalog.repository.YbNationalCatalogBulkUpsertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 目录镜像分批写入：每批独立事务，避免整包长事务 */
@Component
@RequiredArgsConstructor
class YbNationalCatalogBatchWriter {

    private final YbNationalCatalogBulkUpsertRepository bulkUpsertRepository;

    /**
     * 13xx 文件包首次入库批（纯 INSERT）。
     *
     * @return 写入行数
     */
    public int writeFilePackageFreshBatch(
            Long contractId,
            Long tenantId,
            Long operatorId,
            String sourceFileQuryNo,
            List<UpsertNationalCatalogRowCommand> batch) {
        return bulkUpsertRepository.bulkInsertFilePackageRows(
            contractId, tenantId, operatorId, sourceFileQuryNo, batch);
    }

    /**
     * 13xx 文件包重导批（INSERT ON CONFLICT DO UPDATE）。
     *
     * @return 写入行数
     */
    public int writeFilePackageUpsertBatch(
            Long contractId,
            Long tenantId,
            Long operatorId,
            String sourceFileQuryNo,
            List<UpsertNationalCatalogRowCommand> batch) {
        return bulkUpsertRepository.bulkUpsertFilePackageRows(
            contractId, tenantId, operatorId, sourceFileQuryNo, batch);
    }

    /**
     * 1312 平台拉取结果批 upsert。
     *
     * @return 写入行数
     */
    public int write1312UpsertBatch(
            Long contractId,
            Long tenantId,
            Long operatorId,
            List<UpsertNationalCatalogRowCommand> batch) {
        return bulkUpsertRepository.bulkUpsert1312Rows(contractId, tenantId, operatorId, batch);
    }
}
