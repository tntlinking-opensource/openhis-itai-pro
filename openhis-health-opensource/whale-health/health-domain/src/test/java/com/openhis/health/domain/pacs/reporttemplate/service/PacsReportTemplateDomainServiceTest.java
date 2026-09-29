/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.reporttemplate.service;

import com.openhis.health.domain.pacs.reporttemplate.command.SavePacsReportTemplateCommand;
import com.openhis.health.domain.pacs.reporttemplate.entity.PacsReportTemplate;
import com.openhis.health.domain.pacs.reporttemplate.repository.PacsReportTemplateRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PacsReportTemplateDomainService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PacsReportTemplateDomainServiceTest {

    @Mock
    private PacsReportTemplateRepository templateRepository;

    @InjectMocks
    private PacsReportTemplateDomainService domainService;

    @Test
    void save_happyPath_persistsEntity() {
        SavePacsReportTemplateCommand cmd = new SavePacsReportTemplateCommand();
        cmd.setName("CT 常规");
        cmd.setModalityCode("CT");
        cmd.setFindings("双肺未见明显异常");
        cmd.setConclusion("未见明显异常");
        cmd.setTenantId(1L);
        cmd.setOperatorId(900L);

        when(templateRepository.save(any(PacsReportTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PacsReportTemplate saved = domainService.save(cmd);

        assertNotNull(saved);
        assertEquals("CT 常规", saved.getName());
        assertEquals("CT", saved.getModalityCode());
        verify(templateRepository).save(any(PacsReportTemplate.class));
    }

    @Test
    void requireEnabled_disabled_throws() {
        PacsReportTemplate entity = new PacsReportTemplate();
        entity.setId(10L);
        entity.setEnabledFlag(Boolean.FALSE);
        entity.setIsDeleted(Boolean.FALSE);

        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));

        BusinessException exception = assertThrows(BusinessException.class, () -> domainService.requireEnabled(10L));
        assertEquals(ErrorCodeConstants.PACS_REPORT_TEMPLATE_DISABLED, exception.getErrorCode());
    }
}
