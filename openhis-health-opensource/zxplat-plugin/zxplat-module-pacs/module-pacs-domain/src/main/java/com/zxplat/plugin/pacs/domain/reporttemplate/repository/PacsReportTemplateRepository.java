/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.reporttemplate.repository;

import com.zxplat.plugin.pacs.domain.reporttemplate.entity.PacsReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 检查报告模板仓储。
 */
public interface PacsReportTemplateRepository
    extends JpaRepository<PacsReportTemplate, Long>, JpaSpecificationExecutor<PacsReportTemplate> {
}
