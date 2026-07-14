/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.download.mapper;

import com.openhis.chs.application.catalog.download.response.CatalogDownloadCursorResponse;
import com.openhis.chs.application.catalog.download.response.ListCatalogDownloadCursorsResponse;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;

import java.util.List;

/**
 * 目录下载页：游标实体与 UI 出参映射。
 * <p>纯字段搬运，不含业务判断。</p>
 */
public final class InsuranceCatalogDownloadMapper {

    private InsuranceCatalogDownloadMapper() {
    }

    /**
     * 将游标实体列表映射为 UI 出参。
     *
     * @param rows 领域游标实体列表，可为 null
     * @return 游标列表出参，空输入返回空 rows
     */
    public static ListCatalogDownloadCursorsResponse toCursorResponse(List<YbCatalogDownloadCursor> rows) {
        ListCatalogDownloadCursorsResponse response = new ListCatalogDownloadCursorsResponse();
        if (rows == null) {
            return response;
        }
        for (YbCatalogDownloadCursor row : rows) {
            if (row == null) {
                continue;
            }
            String lastSuccessAt = null;
            if (row.getLastSuccessAt() != null) {
                lastSuccessAt = row.getLastSuccessAt().toString();
            }
            response.getRows().add(new CatalogDownloadCursorResponse()
                .setContractId(row.getContractId())
                .setInfno(row.getInfno())
                .setLocalVer(row.getLocalVer())
                .setLastFileQuryNo(row.getLastFileQuryNo())
                .setLastFilename(row.getLastFilename())
                .setLastSuccessAt(lastSuccessAt));
        }
        return response;
    }
}
