/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.template.service;

import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.plugin.emr.domain.enums.TemplateScopeLevel;
import com.zxplat.plugin.emr.domain.template.command.CreateTemplateCommand;
import com.zxplat.plugin.emr.domain.template.command.DeleteTemplateCommand;
import com.zxplat.plugin.emr.domain.template.command.SetTemplateEnabledCommand;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TemplateDomainService 关键：编码唯一、范围组合、软删。
 */
@ExtendWith(MockitoExtension.class)
class TemplateDomainServiceTest {

    @Mock
    private TemplateRepository templateRepository;

    @InjectMocks
    private TemplateDomainService templateDomainService;

    private CreateTemplateCommand baseCmd;

    @BeforeEach
    void setUp() {
        baseCmd = new CreateTemplateCommand();
        baseCmd.setName("入院记录");
        baseCmd.setCode("ADM_NOTE");
        baseCmd.setDocumentTypeCode("ADMISSION");
        baseCmd.setScopeLevel(TemplateScopeLevel.HOSPITAL.getCode());
        baseCmd.setEncounterClassCode("IMP");
        baseCmd.setContent("{\"type\":\"doc\"}");
    }

    @Test
    void createTemplate_rejectsDuplicateCode() {
        when(templateRepository.findByCodeAndIsDeletedFalse("ADM_NOTE"))
                .thenReturn(Optional.of(new Template()));

        assertThrows(IllegalArgumentException.class, () -> templateDomainService.createTemplate(baseCmd));
        verify(templateRepository, never()).save(any());
    }

    @Test
    void createTemplate_rejectsDepartmentWithoutOrg() {
        when(templateRepository.findByCodeAndIsDeletedFalse("ADM_NOTE")).thenReturn(Optional.empty());
        baseCmd.setScopeLevel(TemplateScopeLevel.DEPARTMENT.getCode());
        baseCmd.setOrganizationId(null);

        assertThrows(ValidationException.class, () -> templateDomainService.createTemplate(baseCmd));
        verify(templateRepository, never()).save(any());
    }

    @Test
    void deleteTemplate_marksSoftDeleted() {
        Template existing = new Template();
        existing.setId(1L);
        existing.setIsDeleted(false);
        when(templateRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        DeleteTemplateCommand cmd = new DeleteTemplateCommand();
        cmd.setId(1L);
        templateDomainService.deleteTemplate(cmd);

        ArgumentCaptor<Template> captor = ArgumentCaptor.forClass(Template.class);
        verify(templateRepository).save(captor.capture());
        assertTrue(Boolean.TRUE.equals(captor.getValue().getIsDeleted()));
    }

    @Test
    void setTemplateEnabled_updatesFlag() {
        Template existing = new Template();
        existing.setId(2L);
        existing.setEnabled(false);
        when(templateRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        SetTemplateEnabledCommand cmd = new SetTemplateEnabledCommand();
        cmd.setTemplateId(2L);
        cmd.setEnabled(true);
        templateDomainService.setTemplateEnabled(cmd);

        ArgumentCaptor<Template> captor = ArgumentCaptor.forClass(Template.class);
        verify(templateRepository).save(captor.capture());
        assertEquals(true, captor.getValue().getEnabled());
    }
}
