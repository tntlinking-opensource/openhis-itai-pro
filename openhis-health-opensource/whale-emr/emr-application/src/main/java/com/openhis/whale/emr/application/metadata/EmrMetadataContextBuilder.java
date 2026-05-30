/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.application.metadata;

import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.emr.domain.entity.Document;
import com.openhis.whale.emr.domain.entity.DocumentType;
import com.openhis.whale.emr.domain.metadata.port.EmrMetadataContextPort;
import com.openhis.whale.emr.domain.service.DocumentManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 组装元数据解析用的扁平上下文（patient.* / encounter.* / document.* / user.* / system.*）。
 */
@Component
public class EmrMetadataContextBuilder {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DocumentManager documentManager;
    private final EmrMetadataContextPort metadataContextPort;
    private final ICurrentUser currentUser;

    public EmrMetadataContextBuilder(
            DocumentManager documentManager,
            @Autowired(required = false) EmrMetadataContextPort metadataContextPort,
            @Autowired(required = false) ICurrentUser currentUser) {
        this.documentManager = documentManager;
        this.metadataContextPort = metadataContextPort;
        this.currentUser = currentUser;
    }

    public Map<String, String> build(String patientId, String encounterId, Long documentId) {
        Map<String, String> ctx = new HashMap<>();
        if (metadataContextPort != null) {
            if (StringUtils.hasText(patientId)) {
                metadataContextPort.contributePatient(ctx, patientId);
            }
            if (StringUtils.hasText(encounterId)) {
                metadataContextPort.contributeEncounter(ctx, encounterId);
            }
        }
        contributeDocument(ctx, documentId);
        contributeUser(ctx);
        contributeSystem(ctx);
        return ctx;
    }

    private void contributeDocument(Map<String, String> ctx, Long documentId) {
        if (documentId == null) {
            return;
        }
        Optional<Document> docOpt = documentManager.findById(documentId);
        if (docOpt.isEmpty()) {
            return;
        }
        Document doc = docOpt.get();
        put(ctx, "document.id", String.valueOf(doc.getId()));
        put(ctx, "document.title", doc.getTitle());
        put(ctx, "document.documentTypeCode", doc.getDocumentTypeCode());
        put(ctx, "document.status", doc.getStatus());
        if (doc.getCreatedAt() != null) {
            put(ctx, "document.createdAt", doc.getCreatedAt().format(DATETIME_FMT));
        }
        documentManager.getDocumentTypeByCode(doc.getDocumentTypeCode())
                .map(DocumentType::getName)
                .ifPresent(name -> put(ctx, "document.documentTypeName", name));
    }

    private void contributeUser(Map<String, String> ctx) {
        if (currentUser == null || !currentUser.isAuthenticated()) {
            return;
        }
        if (currentUser.getCurrentUserId() != null) {
            put(ctx, "user.id", String.valueOf(currentUser.getCurrentUserId()));
        }
        put(ctx, "user.displayName", currentUser.getCurrentUserName());
        if (currentUser.getCurrentOrganizationUnitId() != null) {
            put(ctx, "user.deptId", String.valueOf(currentUser.getCurrentOrganizationUnitId()));
            put(ctx, "user.deptName", String.valueOf(currentUser.getCurrentOrganizationUnitId()));
        }
        if (currentUser.getCurrentPractitionerId() != null) {
            Long practitionerId = currentUser.getCurrentPractitionerId();
            put(ctx, "user.practitionerId", String.valueOf(practitionerId));
            if (metadataContextPort != null) {
                metadataContextPort.contributePractitioner(ctx, practitionerId);
            }
        }
    }

    private void contributeSystem(Map<String, String> ctx) {
        LocalDateTime now = LocalDateTime.now();
        put(ctx, "system.now", now.format(DATETIME_FMT));
        put(ctx, "system.today", now.toLocalDate().toString());
        put(ctx, "system.printedAt", now.format(DATETIME_FMT));
    }

    private static void put(Map<String, String> ctx, String key, String value) {
        if (StringUtils.hasText(value)) {
            ctx.put(key, value.trim());
        }
    }
}
