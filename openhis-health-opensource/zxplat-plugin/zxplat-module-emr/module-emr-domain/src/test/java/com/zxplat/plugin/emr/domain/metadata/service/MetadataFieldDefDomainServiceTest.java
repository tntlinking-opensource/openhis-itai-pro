/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.metadata.service;

import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.plugin.emr.domain.metadata.MetadataFieldReferenceChecker;
import com.zxplat.plugin.emr.domain.metadata.command.CreateMetadataFieldDefCommand;
import com.zxplat.plugin.emr.domain.metadata.command.DeleteMetadataFieldDefCommand;
import com.zxplat.plugin.emr.domain.metadata.entity.MetadataFieldDef;
import com.zxplat.plugin.emr.domain.metadata.repository.MetadataFieldDefRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetadataFieldDefDomainService 关键：fieldKey 唯一、删除引用门禁。
 */
@ExtendWith(MockitoExtension.class)
class MetadataFieldDefDomainServiceTest {

    @Mock
    private MetadataFieldDefRepository metadataFieldDefRepository;
    @Mock
    private MetadataFieldReferenceChecker referenceChecker;

    @InjectMocks
    private MetadataFieldDefDomainService metadataFieldDefDomainService;

    @Test
    void create_rejectsDuplicateFieldKey() {
        CreateMetadataFieldDefCommand cmd = new CreateMetadataFieldDefCommand();
        cmd.setFieldKey("patientName");
        cmd.setLabel("姓名");
        cmd.setValueSource("MANUAL");
        when(metadataFieldDefRepository.existsByFieldKeyAndIsDeletedFalse("patientName")).thenReturn(true);

        assertThrows(ValidationException.class, () -> metadataFieldDefDomainService.create(cmd));
        verify(metadataFieldDefRepository, never()).save(any());
    }

    @Test
    void delete_rejectsWhenReferenced() {
        MetadataFieldDef entity = new MetadataFieldDef();
        entity.setId(3L);
        entity.setFieldKey("patientName");
        when(metadataFieldDefRepository.findByIdAndIsDeletedFalse(3L)).thenReturn(Optional.of(entity));
        when(referenceChecker.isReferenced("patientName")).thenReturn(true);

        DeleteMetadataFieldDefCommand cmd = new DeleteMetadataFieldDefCommand();
        cmd.setId(3L);
        assertThrows(ValidationException.class, () -> metadataFieldDefDomainService.delete(cmd));
        verify(metadataFieldDefRepository, never()).delete(any(MetadataFieldDef.class));
    }
}
