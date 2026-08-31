/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.support;

import com.openhis.health.application.peis.group.dto.RosterImportRowDto;
import com.openhis.health.domain.administration.patient.command.RegisterPatientCommand;
import com.openhis.health.domain.administration.patient.queries.model.PatientSimpleQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientDomainService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.IdCardUtil;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 团检名册导入时按身份证匹配或自动建档，解析患者主键。
 */
@Service
@RequiredArgsConstructor
public class GroupRosterPatientResolver {

    private final PatientQueryService patientQueryService;
    private final PatientDomainService patientDomainService;

    /**
     * 按身份证查已有患者；未命中则建档并返回新患者标识。
     *
     * @param row   导入行
     * @param orgId 批次所属机构
     * @return 患者主键
     */
    public Long resolveOrCreatePatient(RosterImportRowDto row, Long orgId) {
        if (orgId == null) {
            throw new ValidationException("导入名册失败：批次未配置机构");
        }
        if (row == null || StringUtils.isEmpty(row.getFullName())) {
            throw new ValidationException("导入名册失败：姓名不能为空");
        }
        assertRowIdentity(row);
        String idCard = trimToNull(row.getIdCardNo());
        if (idCard != null) {
            PatientSimpleQueryModel existing = patientQueryService.findPatientByIdCard(idCard);
            if (existing != null && existing.getId() != null) {
                return existing.getId();
            }
        }
        return createPatient(row, orgId, idCard);
    }

    private Long createPatient(RosterImportRowDto row, Long orgId, String idCard) {
        RegisterPatientCommand command = new RegisterPatientCommand();
        RegisterPatientCommand.RegisterPatientItemCommand item = new RegisterPatientCommand.RegisterPatientItemCommand();
        item.setName(row.getFullName().trim());
        item.setOrganizationId(orgId);
        item.setPhone(trimToNull(row.getMobilePhone()));
        item.setIdCard(idCard);
        item.setGenderEnum(resolveGender(row, idCard));
        if (idCard != null) {
            try {
                LocalDateTime birth = IdCardUtil.extractBirthdayFromIdCard(idCard);
                item.setBirthDate(birth.toLocalDate());
            } catch (IllegalArgumentException ignored) {
                // 身份证格式非法时跳过生日推导，仍允许建档
            }
        }
        item.setTempFlag(Boolean.FALSE);
        command.setItem(item);
        return patientDomainService.registerPatient(command);
    }

    private String resolveGender(RosterImportRowDto row, String idCard) {
        String rawGender = trimToNull(row.getGenderEnum());
        if (rawGender != null) {
            AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, rawGender);
            if (gender == null) {
                throw new ValidationException("导入名册失败：性别编码无效");
            }
            return gender.getCode();
        }
        if (idCard != null && idCard.length() == 18) {
            char genderDigit = idCard.charAt(16);
            if (Character.isDigit(genderDigit)) {
                int value = genderDigit - '0';
                if (value % 2 == 1) {
                    return AdministrativeGender.MALE.getCode();
                }
                return AdministrativeGender.FEMALE.getCode();
            }
        }
        return AdministrativeGender.UNKNOWN.getCode();
    }

    private void assertRowIdentity(RosterImportRowDto row) {
        if (trimToNull(row.getIdCardNo()) != null) {
            return;
        }
        if (trimToNull(row.getMobilePhone()) != null) {
            return;
        }
        if (trimToNull(row.getStudentNo()) != null) {
            return;
        }
        if (trimToNull(row.getEmployeeNo()) != null) {
            return;
        }
        throw new ValidationException("导入名册失败：请至少填写身份证、手机号、学号或工号之一");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
