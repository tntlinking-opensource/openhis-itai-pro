/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.service;

import com.openhis.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import com.openhis.chs.domain.catalog.repository.YbNationalCatalogBulkUpsertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;

/**
 * 目录镜像分批写入：每批独立事务，避免整包长事务。
 */
@Component
@RequiredArgsConstructor
class YbNationalCatalogBatchWriter {

    private final YbNationalCatalogBulkUpsertRepository bulkUpsertRepository;

    public int writeFilePackageFreshBatch(
            Long contractId,
            Long tenantId,
            Long operatorId,
            String sourceFileQuryNo,
            List<UpsertNationalCatalogRowCommand> batch) {
        return bulkUpsertRepository.bulkInsertFilePackageRows(
            contractId, tenantId, operatorId, sourceFileQuryNo, batch);
    }

    public int writeFilePackageUpsertBatch(
            Long contractId,
            Long tenantId,
            Long operatorId,
            String sourceFileQuryNo,
            List<UpsertNationalCatalogRowCommand> batch) {
        return bulkUpsertRepository.bulkUpsertFilePackageRows(
            contractId, tenantId, operatorId, sourceFileQuryNo, batch);
    }

    public int write1312UpsertBatch(
            Long contractId,
            Long tenantId,
            Long operatorId,
            List<UpsertNationalCatalogRowCommand> batch) {
        return bulkUpsertRepository.bulkUpsert1312Rows(contractId, tenantId, operatorId, batch);
    }
}
