/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.registration.support;

import com.zxplat.plugin.pacs.application.registration.dto.PacsRegistrationSlipPrintDto;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.patient.entity.Patient;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.PacsPrintScenarioConstants;
import com.zxplat.domain.share.enums.PacsWorkItemStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Map;

/**
 * 检查登记条打印 slip 组装（与 pacs-registration-slip-print 模板 PrintData 契约一致）。
 */
@Component
@RequiredArgsConstructor
public class PacsRegistrationSlipPrintAssembler {

    private final PacsWorkItemQueryService pacsWorkItemQueryService;
    private final PatientQueryService patientQueryService;
    private final OrganizationQueryService organizationQueryService;

    /**
     * 按工单组装登记条打印 slip。
     *
     * @param workItemId 工单主键
     * @return 打印 slip
     */
    public PacsRegistrationSlipPrintDto assemble(Long workItemId) {
        if (workItemId == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "工单标识不能为空");
        }
        PacsWorkItemDetailQueryModel detail = pacsWorkItemQueryService.getDetail(workItemId);
        this.assertPrintable(detail);

        PacsRegistrationSlipPrintDto slip = new PacsRegistrationSlipPrintDto();
        slip.setScenarioCode(PacsPrintScenarioConstants.REGISTRATION_SLIP_PRINT);
        slip.setWorkItemId(detail.getId());
        slip.setEncounterId(detail.getEncounterId());
        slip.setPatientId(detail.getPatientId());
        slip.setAccessionNumber(detail.getAccessionNumber());
        slip.setModalityCode(this.firstNonBlank(detail.getModalityCode(), ""));
        slip.setBodySiteCode(this.firstNonBlank(detail.getBodySiteCode(), ""));
        slip.setStatusEnum(detail.getStatusEnum());
        slip.setScheduledTimeText(this.formatDateTime(detail.getScheduledTime()));
        slip.setOrganizationName(this.resolveOrganizationName(detail.getOrganizationId()));

        Patient patient = this.resolvePatient(detail.getPatientId());
        if (patient != null) {
            slip.setPatientName(this.firstNonBlank(patient.getName(), ""));
            slip.setGenderEnumText(this.firstNonBlank(this.resolveGenderText(patient.getGenderEnum()), ""));
            slip.setAgeText(this.firstNonBlank(this.resolveAgeText(patient.getBirthDate()), ""));
        } else {
            slip.setPatientName("");
            slip.setGenderEnumText("");
            slip.setAgeText("");
        }
        return slip;
    }

    /**
     * 仅已确认待摄及之后态可打登记条（须有检查号）。
     *
     * @param detail 工单详情
     */
    private void assertPrintable(PacsWorkItemDetailQueryModel detail) {
        if (StringUtils.isEmpty(detail.getAccessionNumber())) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_RULE_CONFLICT, "未生成检查号，不可打印登记条");
        }
        String status = detail.getStatusEnum();
        if (PacsWorkItemStatus.SCHEDULED.getCode().equals(status)) {
            return;
        }
        if (PacsWorkItemStatus.IN_PROGRESS.getCode().equals(status)) {
            return;
        }
        if (PacsWorkItemStatus.COMPLETED.getCode().equals(status)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_STATUS, "当前状态不可打印登记条");
    }

    /**
     * 解析患者实体；不存在则返回 null（姓名等出站空串）。
     *
     * @param patientId 患者主键
     * @return 患者或 null
     */
    private Patient resolvePatient(Long patientId) {
        if (patientId == null) {
            return null;
        }
        return patientQueryService.findOptionalPatientEntityById(patientId).orElse(null);
    }

    /**
     * 解析科室名称。
     *
     * @param organizationId 科室主键
     * @return 名称；未命中为空串
     */
    private String resolveOrganizationName(Long organizationId) {
        if (organizationId == null) {
            return "";
        }
        Map<Long, String> names = organizationQueryService.mapNamesByIds(List.of(organizationId));
        return this.firstNonBlank(names.get(organizationId), "");
    }

    /**
     * 性别枚举 → 中文展示（打印用，非出站码表）。
     *
     * @param genderEnum 性别枚举 code
     * @return 男/女/原值
     */
    private String resolveGenderText(String genderEnum) {
        String code = StringUtils.trimToEmpty(genderEnum);
        if ("male".equalsIgnoreCase(code)) {
            return "男";
        }
        if ("female".equalsIgnoreCase(code)) {
            return "女";
        }
        return StringUtils.trimToNull(genderEnum);
    }

    /**
     * 出生日期 → 年龄展示。
     *
     * @param birthDate 出生日期
     * @return 如「45岁」；无法计算为空串
     */
    private String resolveAgeText(LocalDate birthDate) {
        if (birthDate == null) {
            return "";
        }
        Period period = Period.between(birthDate, LocalDate.now());
        int years = period.getYears();
        if (years < 0) {
            return "";
        }
        return years + "岁";
    }

    /**
     * 格式化计划检查时间。
     *
     * @param scheduledTime 计划时间
     * @return 展示串
     */
    private String formatDateTime(LocalDateTime scheduledTime) {
        if (scheduledTime == null) {
            return "";
        }
        return GeneralSharedConstants.DATE_TIME_FORMATTER.format(scheduledTime);
    }

    /**
     * 取首个非空白串，否则回落。
     *
     * @param first 优先值
     * @param fallback 回落值
     * @return 非空结果
     */
    private String firstNonBlank(String first, String fallback) {
        String trimmed = StringUtils.trimToNull(first);
        if (trimmed != null) {
            return trimmed;
        }
        if (fallback == null) {
            return "";
        }
        return fallback;
    }
}
