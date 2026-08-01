/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.metadata.support;

import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.emr.domain.document.entity.Document;
import com.openhis.whale.emr.domain.document.entity.DocumentType;
import com.openhis.whale.emr.domain.loader.EmrAdmissionContextLoader;
import com.openhis.whale.emr.domain.document.service.DocumentManager;
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
    private final EmrAdmissionContextLoader healthMetadataReader;
    private final ICurrentUser currentUser;

    public EmrMetadataContextBuilder(
            DocumentManager documentManager,
            @Autowired(required = false) EmrAdmissionContextLoader healthMetadataReader,
            @Autowired(required = false) ICurrentUser currentUser) {
        this.documentManager = documentManager;
        this.healthMetadataReader = healthMetadataReader;
        this.currentUser = currentUser;
    }

    public Map<String, String> build(String patientId, String encounterId, Long documentId) {
        Map<String, String> ctx = new HashMap<>();
        if (healthMetadataReader != null) {
            if (StringUtils.hasText(patientId)) {
                healthMetadataReader.contributePatient(ctx, patientId);
            }
            if (StringUtils.hasText(encounterId)) {
                healthMetadataReader.contributeEncounter(ctx, encounterId);
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
            Long orgUnitId = currentUser.getCurrentOrganizationUnitId();
            put(ctx, "user.deptId", String.valueOf(orgUnitId));
            put(ctx, "user.deptName", String.valueOf(orgUnitId));
            if (healthMetadataReader != null) {
                healthMetadataReader.contributeOrganization(ctx, orgUnitId);
            }
        }
        if (currentUser.getCurrentPractitionerId() != null) {
            Long practitionerId = currentUser.getCurrentPractitionerId();
            put(ctx, "user.practitionerId", String.valueOf(practitionerId));
            if (healthMetadataReader != null) {
                healthMetadataReader.contributePractitioner(ctx, practitionerId);
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
