/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.download.mapper;

import com.openhis.chs.application.catalog.download.response.CatalogDownloadCursorResponse;
import com.openhis.chs.application.catalog.download.response.ListCatalogDownloadCursorsResponse;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.enums.ListType;

import java.util.List;
import java.util.Map;

/**
 * 目录下载页：游标实体与 UI 出参映射。
 */
public final class InsuranceCatalogDownloadMapper {

    private InsuranceCatalogDownloadMapper() {
    }

    /**
     * 将游标实体列表映射为 UI 出参，并附带镜像行数等展示字段。
     *
     * @param rows              领域游标实体列表，可为 null
     * @param listTypeRowCounts list_type → 镜像行数
     * @return 游标列表出参，空输入返回空 rows
     */
    public static ListCatalogDownloadCursorsResponse toCursorResponse(
            List<YbCatalogDownloadCursor> rows,
            Map<String, Long> listTypeRowCounts) {
        ListCatalogDownloadCursorsResponse response = new ListCatalogDownloadCursorsResponse();
        if (rows == null) {
            return response;
        }
        Map<String, Long> counts = listTypeRowCounts == null ? Map.of() : listTypeRowCounts;
        for (YbCatalogDownloadCursor row : rows) {
            if (row == null) {
                continue;
            }
            String infno = row.getInfno();
            String listType = listTypeForInfno(infno);
            String lastSuccessAt = row.getLastSuccessAt() == null ? null : row.getLastSuccessAt().toString();
            String localVer = row.getLocalVer();
            String lastFilename = row.getLastFilename();
            Long rowCount = listType == null ? null : counts.getOrDefault(listType, 0L);

            response.getRows().add(new CatalogDownloadCursorResponse()
                .setId(row.getId() == null ? null : String.valueOf(row.getId()))
                .setContractId(row.getContractId() == null ? null : String.valueOf(row.getContractId()))
                .setInfno(infno)
                .setLocalVer(localVer)
                .setLastFileQuryNo(row.getLastFileQuryNo())
                .setLastFilename(lastFilename)
                .setLastSuccessAt(lastSuccessAt)
                .setInfnoLabel(infnoLabel(infno))
                .setLocalVerShort(shorten(localVer, 28))
                .setLastFilenameShort(shorten(lastFilename, 36))
                .setSyncStatusText(lastSuccessAt == null || lastSuccessAt.isBlank() ? "尚未成功同步" : "已同步")
                .setLastZipCached(null)
                .setLocalCatalogRowCount(rowCount == null ? 0L : rowCount));
        }
        return response;
    }

    /** 与 {@code CatalogDownloadDisplaySupport#listTypeForInfno} 对齐。 */
    public static String listTypeForInfno(String infno) {
        if (infno == null || infno.isBlank()) {
            return ListType.WESTERN_DRUG.getCode();
        }
        return switch (infno.trim()) {
            case ChsInfNo.DRUG_CATALOG_DOWNLOAD -> ListType.WESTERN_DRUG.getCode();
            case ChsInfNo.HERB_CATALOG_DOWNLOAD -> ListType.HERB.getCode();
            case ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD -> ListType.MEDICAL_SERVICE.getCode();
            case ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD -> ListType.MEDICAL_CONSUMABLE.getCode();
            case ChsInfNo.DISEASE_CATALOG_DOWNLOAD -> ChsInfNo.DISEASE_CATALOG_DOWNLOAD;
            case ChsInfNo.SURGERY_CATALOG_DOWNLOAD -> ChsInfNo.SURGERY_CATALOG_DOWNLOAD;
            case ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD -> ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD;
            case ChsInfNo.BY_DISEASE_PAYMENT_CATALOG_DOWNLOAD -> ChsInfNo.BY_DISEASE_PAYMENT_CATALOG_DOWNLOAD;
            case ChsInfNo.DAY_SURGERY_DISEASE_CATALOG_DOWNLOAD -> ChsInfNo.DAY_SURGERY_DISEASE_CATALOG_DOWNLOAD;
            case ChsInfNo.TUMOR_MORPHOLOGY_CATALOG_DOWNLOAD -> ChsInfNo.TUMOR_MORPHOLOGY_CATALOG_DOWNLOAD;
            case ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD -> ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD;
            case ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD -> ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD;
            default -> ListType.WESTERN_DRUG.getCode();
        };
    }

    private static String infnoLabel(String infno) {
        if (infno == null || infno.isBlank()) {
            return "-";
        }
        String name = ChsInfNo.resolveDisplayName(infno.trim());
        return infno.trim() + (name == null || name.equals(infno.trim()) ? "" : " " + name.replace("目录下载", ""));
    }

    private static String shorten(String text, int max) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String t = text.trim();
        if (t.length() <= max) {
            return t;
        }
        int head = Math.max(8, max / 2 - 2);
        int tail = Math.max(6, max - head - 3);
        return t.substring(0, head) + "..." + t.substring(t.length() - tail);
    }
}

