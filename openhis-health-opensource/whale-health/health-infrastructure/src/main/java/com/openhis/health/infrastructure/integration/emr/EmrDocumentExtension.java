/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr;

import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentListExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrOutpatientDocumentPageExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentQueryExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrOutpatientDocumentPageExtensionOut;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/** EMR 文书读侧、PDF 导出与归档锁定（EMR Module 提供能力）。 */
public interface EmrDocumentExtension {

    default boolean isEnabled() {
        return false;
    }

    default List<EmrDocumentQueryExtensionOut> listByEncounter(EmrDocumentListExtensionIn query) {
        return Collections.emptyList();
    }

    default boolean hasSubmittedDocument(Long patientId, Long encounterId, String documentTypeCode) {
        return false;
    }

    default Set<String> listSubmittedDocumentTypeCodes(Long patientId, Long encounterId) {
        return Collections.emptySet();
    }

    default byte[] exportPdf(Long documentId) {
        return new byte[0];
    }

    default void lockAllByEncounter(Long encounterId) {
    }

    default void unlockAllByEncounter(Long encounterId) {
    }

    /**
     * 批量判断就诊下是否存在已锁定文书（病例归档态：归档病例 → LOCKED）。
     *
     * @param encounterIds 就诊主键集合；可空
     * @return 存在 LOCKED 文书的就诊主键集合；未装配 EMR 时为空集
     */
    default java.util.Set<Long> filterEncounterIdsWithLockedOrArchivedDocuments(
            java.util.Collection<Long> encounterIds) {
        return java.util.Collections.emptySet();
    }

    /**
     * 门诊文书跨患者分页（病案「门诊病历查询」）。
     *
     * @param query 类型/患者/就诊/状态与分页；{@code null} 时返回空页
     * @return 分页摘要；未装配 EMR 时默认空页
     */
    default EmrOutpatientDocumentPageExtensionOut queryOutpatientDocumentPage(
            EmrOutpatientDocumentPageExtensionIn query) {
        EmrOutpatientDocumentPageExtensionOut empty = new EmrOutpatientDocumentPageExtensionOut();
        if (query != null) {
            if (query.getPageNo() != null && query.getPageNo() > 0) {
                empty.setPageNo(query.getPageNo());
            }
            if (query.getPageSize() != null && query.getPageSize() > 0) {
                empty.setPageSize(query.getPageSize());
            }
        }
        empty.setContent(Collections.emptyList());
        empty.setTotalCount(0L);
        return empty;
    }
}
