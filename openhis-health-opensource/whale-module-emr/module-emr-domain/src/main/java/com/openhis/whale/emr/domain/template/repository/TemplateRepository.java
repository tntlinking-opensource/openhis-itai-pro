/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.template.repository;

import com.openhis.whale.emr.domain.template.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 模板仓储。表 emr_template；医生站/维护页列表走 {@link JpaSpecificationExecutor}。
 */
@Repository
public interface TemplateRepository extends JpaRepository<Template, Long>, JpaSpecificationExecutor<Template> {

    /** 按主键查未软删模板。 */
    Optional<Template> findByIdAndIsDeletedFalse(Long id);

    /** 按编码查未软删模板（编码唯一校验）。 */
    Optional<Template> findByCodeAndIsDeletedFalse(String code);

    /** 某文书类型下启用模板（全院，organizationId 为空）。 */
    List<Template> findByDocumentTypeCodeAndEnabledAndIsDeletedFalse(String documentTypeCode, Boolean enabled);

    /** 某文书类型下启用全院模板。 */
    List<Template> findByDocumentTypeCodeAndEnabledAndIsDeletedFalseAndOrganizationIdIsNull(
            String documentTypeCode, Boolean enabled);

    /** 某文书类型下启用且归属指定科室的模板。 */
    List<Template> findByDocumentTypeCodeAndEnabledAndIsDeletedFalseAndOrganizationId(
            String documentTypeCode, Boolean enabled, Long organizationId);
}
