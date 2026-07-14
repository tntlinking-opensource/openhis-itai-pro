/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr;

import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentListExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentQueryExtensionOut;

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
}
