/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.mrms;

import com.zxplat.domain.mrms.caserecord.entity.CaseRecord;
import com.zxplat.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.zxplat.domain.share.authorization.MrmsAdminAuthorizer;
import com.zxplat.domain.share.constants.MrmsErrorCodes;
import com.zxplat.domain.share.enums.CaseRecordStatus;
import com.zxplat.domain.infrastructure.integration.emr.EmrDocumentExtension;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * 临床「归档病例 / 解档病例」编排（门诊/住院病例查询共用）。
 *
 * <p>落 EMR 文书 ARCHIVED，不迁移病案主档状态。病案主档已 ARCHIVED 时禁止解档病例。</p>
 */
@Component
@RequiredArgsConstructor
public class MrmsClinicalCaseArchiveSupport {

    /** 病案管理员鉴权。 */
    private final MrmsAdminAuthorizer mrmsAdminAuthorizer;

    /** EMR 文书锁定 Extension。 */
    private final EmrDocumentExtension emrDocumentExtension;

    /** 病案主档读侧（解档前判断是否已病案归档）。 */
    private final CaseRecordDomainService caseRecordDomainService;

    /**
     * 归档病例：将就诊下全部 EMR 文书置为 ARCHIVED。
     *
     * @param encounterId 就诊主键
     * @param tenantId    租户主键（解档校验主档时用；归档仅作会话校验透传）
     */
    public void archiveClinicalCase(Long encounterId, Long tenantId) {
        // Gate：仅病案管理员可归档病例
        mrmsAdminAuthorizer.assertMrmsAdminRole();
        if (encounterId == null) {
            throw new ValidationException("就诊主键不能为空");
        }
        // Gate：EMR 未启用则无法锁定文书
        if (!emrDocumentExtension.isEnabled()) {
            throw new BusinessException(MrmsErrorCodes.CLINICAL_ARCHIVE_EMR_DISABLED, "电子病历未启用，无法归档病例");
        }
        // Cmd：批量置 ARCHIVED
        emrDocumentExtension.lockAllByEncounter(encounterId);
    }

    /**
     * 解档病例：将 ARCHIVED 文书恢复为 DRAFT；病案主档已归档时拒绝。
     *
     * @param encounterId 就诊主键
     * @param tenantId    租户主键
     */
    public void unarchiveClinicalCase(Long encounterId, Long tenantId) {
        // Gate：仅病案管理员可解档病例
        mrmsAdminAuthorizer.assertMrmsAdminRole();
        if (encounterId == null) {
            throw new ValidationException("就诊主键不能为空");
        }
        if (!emrDocumentExtension.isEnabled()) {
            throw new BusinessException(MrmsErrorCodes.CLINICAL_ARCHIVE_EMR_DISABLED, "电子病历未启用，无法解档病例");
        }
        // Gate：病案主档已归档时禁止单独解档病例导致文书与主档不一致
        if (tenantId != null && this.isCaseRecordArchived(tenantId, encounterId)) {
            throw new BusinessException(
                    MrmsErrorCodes.CLINICAL_UNARCHIVE_BLOCKED_BY_CASE_RECORD,
                    "病案已归档，请使用质控工作台解档病案");
        }
        // Cmd：ARCHIVED → DRAFT
        emrDocumentExtension.unlockAllByEncounter(encounterId);
    }

    /**
     * 批量判断就诊是否处于病例归档态（存在 PENDING_ARCHIVE/ARCHIVED 文书）。
     *
     * @param encounterIds 就诊主键集合
     * @return 已归档病例的就诊主键集合
     */
    public Set<Long> filterClinicallyArchivedEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty() || !emrDocumentExtension.isEnabled()) {
            return Collections.emptySet();
        }
        return emrDocumentExtension.filterEncounterIdsWithLockedOrArchivedDocuments(encounterIds);
    }

    /**
     * 就诊对应病案主档是否已 ARCHIVED。
     *
     * @param tenantId    租户
     * @param encounterId 就诊
     * @return true 表示主档已病案归档
     */
    private boolean isCaseRecordArchived(Long tenantId, Long encounterId) {
        CaseRecord record = caseRecordDomainService.findByEncounterId(tenantId, encounterId).orElse(null);
        if (record == null) {
            return false;
        }
        return record.getStatusEnum() == CaseRecordStatus.ARCHIVED;
    }
}
