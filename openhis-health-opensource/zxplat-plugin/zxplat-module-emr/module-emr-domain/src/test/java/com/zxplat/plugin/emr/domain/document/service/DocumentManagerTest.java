/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.document.service;

import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.plugin.emr.domain.document.entity.Document;
import com.zxplat.plugin.emr.domain.document.repository.DocumentRepository;
import com.zxplat.plugin.emr.domain.document.repository.DocumentTypeRepository;
import com.zxplat.plugin.emr.domain.document.repository.DocumentVersionRepository;
import com.zxplat.plugin.emr.domain.template.entity.Template;
import com.zxplat.plugin.emr.domain.template.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DocumentManager 关键不变式：待归档/已归档不可直接改、首次病程唯一。
 */
@ExtendWith(MockitoExtension.class)
class DocumentManagerTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentVersionRepository documentVersionRepository;
    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private DocumentTypeRepository documentTypeRepository;

    @InjectMocks
    private DocumentManager documentManager;

    private Document draft;

    @BeforeEach
    void setUp() {
        draft = new Document();
        draft.setId(1L);
        draft.setPatientId("P1");
        draft.setEncounterId("E1");
        draft.setDocumentTypeCode("PROGRESS_NOTE");
        draft.setStatus(DocumentManager.STATUS_DRAFT);
        draft.setContent("{\"type\":\"doc\",\"content\":[]}");
    }

    @Test
    void saveContent_rejectsPendingArchiveDocument() {
        draft.setStatus(DocumentManager.STATUS_PENDING_ARCHIVE);
        when(documentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(draft));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> documentManager.saveContent(1L, "{\"type\":\"doc\"}", false));
        assertEquals("文书已锁定或归档，不可修改", ex.getMessage());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void saveContent_rejectsArchivedDocument() {
        draft.setStatus(DocumentManager.STATUS_ARCHIVED);
        when(documentRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(draft));

        assertThrows(ValidationException.class,
                () -> documentManager.saveContent(1L, "{\"type\":\"doc\"}", false));
        verify(documentRepository, never()).save(any());
    }

    @Test
    void create_rejectsDuplicateFirstProgressNote() {
        Template template = new Template();
        template.setId(10L);
        template.setCode(DocumentManager.TEMPLATE_CODE_FIRST_PROGRESS);
        template.setName("首次病程");
        template.setContent("{\"type\":\"doc\",\"content\":[]}");

        when(templateRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(template));
        when(documentRepository.existsByEncounterIdAndSourceTemplateIdAndIsDeletedFalse("E1", 10L))
                .thenReturn(true);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> documentManager.create("P1", "E1", "PROGRESS_NOTE", 10L));
        assertEquals("该就诊已存在首次病程记录", ex.getMessage());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void create_persistsDraftWithTemplateContent() {
        Template template = new Template();
        template.setId(11L);
        template.setCode("PN_DAILY");
        template.setName("日常病程");
        template.setContent("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}");

        when(templateRepository.findByIdAndIsDeletedFalse(11L)).thenReturn(Optional.of(template));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.setId(99L);
            return d;
        });

        Document created = documentManager.create("P1", "E1", "PROGRESS_NOTE", 11L);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();
        assertEquals(DocumentManager.STATUS_DRAFT, saved.getStatus());
        assertEquals(11L, saved.getSourceTemplateId());
        assertEquals(template.getContent(), saved.getContent());
        assertEquals(99L, created.getId());
    }
}
