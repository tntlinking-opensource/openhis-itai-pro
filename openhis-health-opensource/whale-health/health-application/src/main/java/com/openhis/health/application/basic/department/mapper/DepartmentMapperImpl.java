/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.basic.department.mapper;

import com.openhis.health.application.basic.department.dto.DepartmentDetailDto;
import com.openhis.health.application.basic.department.dto.DepartmentDto;
import com.openhis.health.application.basic.department.request.CreateDepartmentRequest;
import com.openhis.health.application.basic.department.request.UpdateDepartmentRequest;
import com.openhis.health.domain.administration.organization.command.CreateOrganizationCommand;
import com.openhis.health.domain.administration.organization.command.InputCodeCommand;
import com.openhis.health.domain.administration.organization.command.MedicalInsuranceInfoCommand;
import com.openhis.health.domain.administration.organization.command.ModifyOrganizationCommand;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.share.enums.OrganizationClassification;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 科室应用层对象映射器实现。
 *
 * <p>职责：在应用层请求/响应模型与领域命令、领域实体之间做无副作用转换，
 * 同时补齐旧版页面仍依赖的枚举显示字段与状态字段。</p>
 */
@Component
public class DepartmentMapperImpl implements DepartmentMapper {

    /** 术语查询：补齐科室类型、启用状态等字典展示文案。 */
    private final CodeSystemQueryService codeSystemQueryService;

    public DepartmentMapperImpl(CodeSystemQueryService codeSystemQueryService) {
        this.codeSystemQueryService = codeSystemQueryService;
    }

    /**
     * 将组织实体映射为列表/树节点 DTO，并补齐旧版页面依赖的枚举展示字段。
     *
     * @param entity 领域组织，可为 null
     * @return 科室节点；入参为 null 时返回 null
     */
    @Override
    public DepartmentDto toDto(Organization entity) {
        if (entity == null) {
            return null;
        }
        DepartmentDto dto = new DepartmentDto();
        dto.setId(entity.getId());
        dto.setBusNo(entity.getBusNo());
        dto.setName(entity.getName());
        dto.setStatusEnum(entity.getStatusEnum());
        dto.setStatusEnum_dictText(codeSystemQueryService.getDisplayByCode(
            PublicationStatus.class, entity.getStatusEnum()));
        dto.setTypeEnum(resolveLegacyTypeEnum(entity.getTypeEnum()));
        dto.setClassEnum(toLegacyClassEnum(entity.getClassEnum()));
        dto.setPyStr(entity.getPyStr());
        dto.setWbStr(entity.getWbStr());
        dto.setDefDoctorId(entity.getDefDoctorId());
        dto.setYbNo(entity.getYbNo());
        dto.setYbName(entity.getYbName());
        dto.setMedinsId(entity.getMedinsId());
        dto.setMedinsAdmdvs(entity.getMedinsAdmdvs());
        dto.setMedinsType(entity.getMedinsType());
        dto.setMedinsLv(entity.getMedinsLv());
        dto.setCaty(entity.getCaty());
        dto.setDisplayOrder(entity.getDisplayOrder());
        dto.setParentId(entity.getParentId());
        dto.setTypeEnum_dictText(codeSystemQueryService.getDisplayByCode(OrganizationType.class, entity.getTypeEnum()));
        // 机构分类枚举直接取 display，与类型码表路径分离
        if (entity.getClassEnum() == null) {
            dto.setClassEnum_dictText(null);
        } else {
            dto.setClassEnum_dictText(entity.getClassEnum().getDisplay());
        }
        return dto;
    }

    /**
     * 将新增请求装配为领域创建命令（含输入码与医保信息子命令）。
     *
     * @param request    新增请求，可为 null
     * @param operatorId 操作人 ID
     * @return 创建命令；请求为 null 时返回 null
     */
    @Override
    public CreateOrganizationCommand toCreateCommand(CreateDepartmentRequest request, Long operatorId) {
        if (request == null) {
            return null;
        }
        CreateOrganizationCommand cmd = new CreateOrganizationCommand();
        cmd.setOperatorId(operatorId);
        CreateOrganizationCommand.CreateOrganizationItemCommand item = new CreateOrganizationCommand.CreateOrganizationItemCommand();
        item.setName(request.getName());
        item.setBusNo(request.getBusNo());
        String typeCode = resolveOrganizationTypeCode(request.getTypeEnum());
        item.setTypeEnum(typeCode);
        item.setParentId(request.getParentId());
        item.setClassEnum(resolveOrganizationClass(request.getClassEnum()));
        item.setDisplayOrder(request.getDisplayOrder());
        item.setDefDoctorId(request.getDefDoctorId());
        item.setCaty(request.getCaty());
        item.setInputCode(buildInputCode(request.getPyStr(), request.getWbStr()));
        item.setInsuranceInfo(buildInsuranceInfo(
            request.getYbNo(),
            request.getYbName(),
            request.getMedinsId(),
            request.getMedinsAdmdvs(),
            request.getMedinsType(),
            request.getMedinsLv()
        ));
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 将编辑请求装配为领域修改命令（类型不在此变更路径内）。
     *
     * @param request    编辑请求，可为 null
     * @param operatorId 操作人 ID
     * @return 修改命令；请求为 null 时返回 null
     */
    @Override
    public ModifyOrganizationCommand toModifyCommand(UpdateDepartmentRequest request, Long operatorId) {
        if (request == null) {
            return null;
        }
        ModifyOrganizationCommand cmd = new ModifyOrganizationCommand();
        cmd.setOperatorId(operatorId);
        ModifyOrganizationCommand.ModifyOrganizationItemCommand item = new ModifyOrganizationCommand.ModifyOrganizationItemCommand();
        item.setOrganizationId(request.getId());
        item.setName(request.getName());
        item.setBusNo(request.getBusNo());
        item.setClassEnum(resolveOrganizationClass(request.getClassEnum()));
        item.setDisplayOrder(request.getDisplayOrder());
        item.setDefDoctorId(request.getDefDoctorId());
        item.setCaty(request.getCaty());
        item.setInputCode(buildInputCode(request.getPyStr(), request.getWbStr()));
        item.setInsuranceInfo(buildInsuranceInfo(
            request.getYbNo(),
            request.getYbName(),
            request.getMedinsId(),
            request.getMedinsAdmdvs(),
            request.getMedinsType(),
            request.getMedinsLv()
        ));
        cmd.setItem(item);
        return cmd;
    }

    /**
     * 将组织实体映射为详情 DTO（不含父级嵌套，由应用层按需填充）。
     *
     * @param organization 领域组织，可为 null
     * @return 详情 DTO；入参为 null 时返回 null
     */
    @Override
    public DepartmentDetailDto toDetailDto(Organization organization) {
        if (organization == null) {
            return null;
        }

        DepartmentDetailDto dto = new DepartmentDetailDto();
        dto.setId(organization.getId());
        dto.setName(organization.getName());
        dto.setTypeEnum(resolveLegacyTypeEnum(organization.getTypeEnum()));
        dto.setTypeEnum_dictText(codeSystemQueryService.getDisplayByCode(OrganizationType.class, organization.getTypeEnum()));
        dto.setClassEnum(toLegacyClassEnum(organization.getClassEnum()));
        if (organization.getClassEnum() == null) {
            dto.setClassEnum_dictText(null);
        } else {
            dto.setClassEnum_dictText(organization.getClassEnum().getDisplay());
        }
        dto.setYbNo(organization.getYbNo());
        dto.setYbName(organization.getYbName());
        dto.setMedinsId(organization.getMedinsId());
        dto.setMedinsAdmdvs(organization.getMedinsAdmdvs());
        dto.setMedinsType(organization.getMedinsType());
        dto.setMedinsLv(organization.getMedinsLv());
        dto.setActiveFlag(toLegacyActiveFlag(organization.getActiveFlag()));
        dto.setActiveFlag_dictText(resolveDetailActiveFlagText(organization.getActiveFlag()));
        dto.setPyStr(organization.getPyStr());
        dto.setWbStr(organization.getWbStr());
        dto.setDefDoctorId(organization.getDefDoctorId());
        dto.setCaty(organization.getCaty());
        dto.setBusNo(organization.getBusNo());
        dto.setDisplayOrder(organization.getDisplayOrder());
        return dto;
    }

    /**
     * 装配拼音/五笔输入码子命令；两码皆空时不创建子命令。
     *
     * @param pyStr 拼音码
     * @param wbStr 五笔码
     * @return 输入码命令或 null
     */
    private InputCodeCommand buildInputCode(String pyStr, String wbStr) {
        if (pyStr == null && wbStr == null) {
            return null;
        }
        InputCodeCommand inputCode = new InputCodeCommand();
        inputCode.setPyStr(pyStr);
        inputCode.setWbStr(wbStr);
        return inputCode;
    }

    /**
     * 装配医保信息子命令；全部字段为空时不创建子命令。
     *
     * @param ybNo        医保机构编码
     * @param ybName      医保机构名称
     * @param medinsId    医药机构标识
     * @param medinsAdmdvs 行政区划
     * @param medinsType  机构类型
     * @param medinsLv    机构等级
     * @return 医保信息命令或 null
     */
    private MedicalInsuranceInfoCommand buildInsuranceInfo(
        String ybNo,
        String ybName,
        String medinsId,
        String medinsAdmdvs,
        String medinsType,
        String medinsLv
    ) {
        if (ybNo == null
            && ybName == null
            && medinsId == null
            && medinsAdmdvs == null
            && medinsType == null
            && medinsLv == null) {
            return null;
        }
        MedicalInsuranceInfoCommand insuranceInfo = new MedicalInsuranceInfoCommand();
        insuranceInfo.setYbNo(ybNo);
        insuranceInfo.setYbName(ybName);
        insuranceInfo.setMedinsId(medinsId);
        insuranceInfo.setMedinsAdmdvs(medinsAdmdvs);
        insuranceInfo.setMedinsType(medinsType);
        insuranceInfo.setMedinsLv(medinsLv);
        return insuranceInfo;
    }

    /**
     * 将机构分类枚举转为旧版页面使用的排序整型。
     *
     * @param classification 领域分类
     * @return 排序值或 null
     */
    private Integer toLegacyClassEnum(OrganizationClassification classification) {
        if (classification == null || classification.getSortOrder() == null) {
            return null;
        }
        return Math.toIntExact(classification.getSortOrder());
    }

    /**
     * 将类型编码解析为旧版页面使用的排序整型（按 {@link OrganizationType} 匹配）。
     *
     * @param typeCode 领域类型编码
     * @return 排序值或 null
     */
    private Integer resolveLegacyTypeEnum(String typeCode) {
        OrganizationType type = resolveOrganizationType(typeCode);
        if (type == null || type.getSortOrder() == null) {
            return null;
        }
        return Math.toIntExact(type.getSortOrder());
    }

    /**
     * 将领域布尔启用态转为旧版 1/2 整型（仅详情 DTO 等旧接口使用）。
     *
     * @param activeFlag 领域启用标志
     * @return 1、2 或 null
     */
    private Integer toLegacyActiveFlag(Boolean activeFlag) {
        if (activeFlag == null) {
            return null;
        }
        if (Boolean.TRUE.equals(activeFlag)) {
            return 1;
        }
        return 2;
    }

    /**
     * 解析详情页状态展示文案。
     *
     * @param activeFlag 领域启用状态
     * @return 页面状态文案
     */
    private String resolveDetailActiveFlagText(Boolean activeFlag) {
        if (Boolean.TRUE.equals(activeFlag)) {
            return "启用";
        }
        return "停用";
    }

    /**
     * 解析科室分类：支持直接传领域 code，或传旧版排序数字字符串。
     *
     * @param classEnumRaw 前端原始值
     * @return 分类枚举或 null
     */
    private OrganizationClassification resolveOrganizationClass(String classEnumRaw) {
        if (StringUtils.isBlank(classEnumRaw)) {
            return null;
        }
        String normalizedClass = classEnumRaw.trim();
        for (OrganizationClassification item : OrganizationClassification.values()) {
            if (item != null && item.getCode().equals(normalizedClass)) {
                return item;
            }
        }
        long sortOrder;
        try {
            sortOrder = Long.parseLong(normalizedClass);
        } catch (NumberFormatException ex) {
            return null;
        }
        for (OrganizationClassification item : OrganizationClassification.values()) {
            if (item != null && item.getSortOrder() != null && item.getSortOrder().longValue() == sortOrder) {
                return item;
            }
        }
        return null;
    }

    /**
     * 解析科室类型：支持直接传领域 code，或传旧版排序数字字符串。
     *
     * @param typeEnumRaw 前端原始值
     * @return 领域类型 code，无法解析时返回 null
     */
    private String resolveOrganizationTypeCode(String typeEnumRaw) {
        OrganizationType type = resolveOrganizationType(typeEnumRaw);
        return type == null ? null : type.getCode();
    }

    /**
     * 解析机构层级类型：支持领域 code、旧版 sortOrder 数字，以及历史误写的业务 type 码。
     *
     * @param typeEnumRaw 前端或库表原始值
     * @return 层级类型枚举或 null
     */
    private OrganizationType resolveOrganizationType(String typeEnumRaw) {
        return OrganizationType.resolveRaw(typeEnumRaw);
    }
}
