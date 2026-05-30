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

package com.openhis.health.application.basic.patient.mapper;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.application.basic.patient.dto.PatientDto;
import com.openhis.health.application.basic.patient.dto.PatientIdentifierItemDto;
import com.openhis.health.application.basic.patient.request.CreatePatientRequest;
import com.openhis.health.application.basic.patient.request.UpdatePatientRequest;
import com.openhis.health.domain.administration.patient.command.DeletePatientCommand;
import com.openhis.health.domain.administration.patient.command.ModifyPatientProfileCommand;
import com.openhis.health.domain.administration.patient.command.PatientIdentifierItemCommand;
import com.openhis.health.domain.administration.patient.command.RegisterPatientCommand;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.entity.PatientIdentifier;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientIdentifierQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.openhis.health.domain.administration.patient.valueobject.Address;
import com.openhis.health.domain.administration.patient.valueobject.BloodType;
import com.openhis.health.domain.administration.patient.valueobject.ContactPerson;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.IdentifierState;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 患者主数据应用层手写映射：{@link Patient} / 查询模型 ↔ {@link PatientDto}；
 * 建档与改档请求 ↔ {@link RegisterPatientCommand}、{@link ModifyPatientProfileCommand}；附带证件行列表转换。
 *
 * <p>字符串 trim、日期时间解析、枚举编码解析等均集中在私有工具方法；不写业务流程分支（归属 AppService / Domain）。</p>
 */
@Component
public class PatientMapper {

    /**
     * 领域患者实体 → 完整患者 DTO（用于少量仍为实体直出的路径）。
     *
     * @param entity 患者实体；{@code null} 返回 {@code null}
     * @return DTO 或 {@code null}
     */
    public PatientDto toDto(Patient entity) {
        if (entity == null) {
            return null;
        }
        PatientDto dto = new PatientDto();
        dto.setId(entity.getId());
        dto.setBusNo(entity.getBusNo());
        dto.setName(entity.getName());
        dto.setGenderEnum(entity.getGenderEnum());
        dto.setBirthDate(entity.getBirthDate());
        dto.setIdCard(entity.getIdCard());
        dto.setPhone(entity.getPhone());
        dto.setPrfsEnum(entity.getPrfsEnum());
        dto.setWorkCompany(entity.getWorkCompany());
        ContactPerson linkPerson = entity.getLinkName();
        if (linkPerson != null) {
            dto.setLinkName(linkPerson.getName());
            dto.setLinkRelationCode(linkPerson.getRelationCode());
            dto.setLinkTelcom(linkPerson.getTelcom());
        } else {
            dto.setLinkName(null);
            dto.setLinkRelationCode(null);
            dto.setLinkTelcom(null);
        }

        Address addr = entity.getAddress();
        if (addr != null) {
            dto.setAddress(addr.getText());
            dto.setAddressProvince(addr.getProvince());
            dto.setAddressCity(addr.getCity());
            dto.setAddressDistrict(addr.getDistrict());
            dto.setAddressStreet(addr.getStreet());
        }

        dto.setNationalityCode(entity.getNationalityCode());

        BloodType bt = entity.getBloodType();
        if (bt != null) {
            dto.setBloodAbo(bt.getAbo());
            dto.setBloodRh(bt.getRh());
        }

        dto.setMaritalStatusEnum(entity.getMaritalStatusEnum());
        dto.setDeceasedDate(entity.getDeceasedDate());
        dto.setPyStr(entity.getPyStr());
        dto.setWbStr(entity.getWbStr());
        dto.setActiveFlag(entity.getActiveFlag());
        dto.setCreateTime(entity.getCreatedAt());
        return dto;
    }

    /**
     * 分页列表查询模型 → 列表行 DTO（附带性别展示文案、启用文案与粗略年龄）。
     *
     * @param model 列表行模型；{@code null} 返回 {@code null}
     * @return DTO 或 {@code null}
     */
    public PatientDto toPageItemDto(PatientPageItemQueryModel model) {
        if (model == null) {
            return null;
        }
        PatientDto dto = new PatientDto();
        dto.setId(model.getId());
        dto.setBusNo(model.getBusNo());
        dto.setName(model.getName());
        dto.setGenderEnum(model.getGenderEnum());
        if (model.getGenderEnum() != null) {
            dto.setGenderEnumText(this.getGenderDisplayText(model.getGenderEnum()));
        }
        dto.setBirthDate(model.getBirthDate());
        dto.setIdCard(model.getIdCard());
        dto.setPhone(model.getPhone());

        if (model.getActiveFlag() != null) {
            PatientMapper.fillActiveFlagAndText(dto, model.getActiveFlag());
        }

        if (model.getBirthDate() != null) {
            // 粗略年龄：仅用于检索列表提示，非合规意义上的周岁计算。
            int age = java.time.LocalDate.now().getYear() - model.getBirthDate().getYear();
            dto.setAge(Math.max(age, 0));
        }
        return dto;
    }

    /**
     * 患者详情查询模型 → 编辑回显 DTO（扁平地址与血缘字段 + 证件行列表）。
     *
     * @param detail 详情模型（须含 patient）；不完整时返回 {@code null}
     * @return DTO 或 {@code null}
     */
    public PatientDto toDetailDto(PatientDetailQueryModel detail) {
        if (detail == null || detail.getPatient() == null) {
            return null;
        }

        PatientDto dto = new PatientDto();
        dto.setId(detail.getPatient().getId());
        dto.setBusNo(detail.getPatient().getBusNo());
        dto.setName(detail.getPatient().getName());
        dto.setGenderEnum(detail.getPatient().getGenderEnum());
        if (detail.getPatient().getGenderEnum() != null) {
            dto.setGenderEnumText(this.getGenderDisplayText(detail.getPatient().getGenderEnum()));
        }
        dto.setBirthDate(detail.getPatient().getBirthDate());
        dto.setIdCard(detail.getPatient().getIdCard());
        dto.setPhone(detail.getPatient().getPhone());
        dto.setPrfsEnum(detail.getPatient().getPrfsEnum());
        dto.setWorkCompany(detail.getPatient().getWorkCompany());
        dto.setLinkName(detail.getPatient().getLinkName());
        dto.setLinkRelationCode(detail.getPatient().getLinkRelationCode());
        dto.setLinkTelcom(detail.getPatient().getLinkTelcom());
        dto.setAddress(detail.getPatient().getAddress());
        dto.setAddressProvince(detail.getPatient().getAddressProvince());
        dto.setAddressCity(detail.getPatient().getAddressCity());
        dto.setAddressDistrict(detail.getPatient().getAddressDistrict());
        dto.setAddressStreet(detail.getPatient().getAddressStreet());
        dto.setNationalityCode(detail.getPatient().getNationalityCode());
        dto.setBloodAbo(detail.getPatient().getBloodTypeAbo());
        dto.setBloodRh(detail.getPatient().getBloodTypeRh());
        dto.setMaritalStatusEnum(detail.getPatient().getMaritalStatusEnum());
        dto.setDeceasedDate(detail.getPatient().getDeceasedDate());
        dto.setPyStr(detail.getPatient().getPyStr());
        dto.setWbStr(detail.getPatient().getWbStr());

        if (detail.getPatient().getActiveFlag() != null) {
            PatientMapper.fillActiveFlagAndText(dto, detail.getPatient().getActiveFlag());
        }

        if (detail.getPatient().getBirthDate() != null) {
            int age = java.time.LocalDate.now().getYear() - detail.getPatient().getBirthDate().getYear();
            dto.setAge(Math.max(age, 0));
        }

        dto.setPatientIdInfoList(this.toIdentifierItemListFromModels(detail.getIdentifiers()));
        return dto;
    }

    /**
     * 证件查询模型列表 → 前端表格行 DTO（时间格式化为约定字符串）。
     *
     * @param list 查询模型列表；{@code null} 或空返回空列表
     * @return 非 {@code null} 列表
     */
    public List<PatientIdentifierItemDto> toIdentifierItemListFromModels(List<PatientIdentifierQueryModel> list) {
        List<PatientIdentifierItemDto> result = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return result;
        }
        for (PatientIdentifierQueryModel item : list) {
            if (item == null) {
                continue;
            }
            PatientIdentifierItemDto it = new PatientIdentifierItemDto();
            it.setId(item.getId());
            if (item.getStateEnum() != null) {
                it.setUseEnum(item.getStateEnum().getCode());
            }
            it.setTypeCode(item.getTypeCode());
            it.setIdentifierNo(item.getIdentifierNo());
            if (item.getStartTime() != null) {
                it.setStartTime(GeneralSharedConstants.DATE_TIME_FORMATTER.format(item.getStartTime()));
            }
            if (item.getEndTime() != null) {
                it.setEndTime(GeneralSharedConstants.DATE_TIME_FORMATTER.format(item.getEndTime()));
            }
            result.add(it);
        }
        return result;
    }

    /**
     * 建档请求 → 单条 {@link RegisterPatientCommand}（含证件子命令列表）。
     *
     * @param request 请求；{@code null} 返回 {@code null}
     * @return 命令或 {@code null}
     */
    public RegisterPatientCommand toRegisterCommand(CreatePatientRequest request) {
        if (request == null) {
            return null;
        }
        RegisterPatientCommand command = new RegisterPatientCommand();
        command.setOccurredAt(LocalDateTime.now());

        RegisterPatientCommand.RegisterPatientItemCommand item = new RegisterPatientCommand.RegisterPatientItemCommand();
        item.setName(trimToNull(request.getName()));
        item.setGenderEnum(request.getGenderEnum());
        if (request.getBirthDate() != null) {
            item.setBirthDate(request.getBirthDate().toLocalDate());
        } else {
            item.setBirthDate(null);
        }
        item.setIdCard(trimToNull(request.getIdCard()));
        item.setPhone(trimToNull(request.getPhone()));
        item.setTempFlag(null);
        item.setOrganizationId(null);
        item.setIdentifierItems(this.toIdentifierItemCommands(request.getPatientIdInfoList()));
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 修改档案请求 → 单条 {@link ModifyPatientProfileCommand}。
     *
     * @param request 请求；{@code null} 返回 {@code null}
     * @return 命令或 {@code null}
     */
    public ModifyPatientProfileCommand toModifyCommand(UpdatePatientRequest request) {
        if (request == null) {
            return null;
        }
        ModifyPatientProfileCommand command = new ModifyPatientProfileCommand();
        ModifyPatientProfileCommand.ModifyPatientProfileItemCommand item = new ModifyPatientProfileCommand.ModifyPatientProfileItemCommand();
        item.setPatientId(request.getId());
        item.setName(trimToNull(request.getName()));
        item.setGenderEnum(request.getGenderEnum());
        if (request.getBirthDate() != null) {
            item.setBirthDate(request.getBirthDate().toLocalDate());
        } else {
            item.setBirthDate(null);
        }
        item.setIdCard(trimToNull(request.getIdCard()));
        item.setMaritalStatusEnum(request.getMaritalStatusEnum());
        item.setPrfsEnum(request.getPrfsEnum());
        item.setPhone(trimToNull(request.getPhone()));
        item.setNationalityCode(trimToNull(request.getNationalityCode()));
        item.setWorkCompany(trimToNull(request.getWorkCompany()));
        item.setAddress(trimToNull(request.getAddress()));
        item.setAddressProvince(trimToNull(request.getAddressProvince()));
        item.setAddressCity(trimToNull(request.getAddressCity()));
        item.setAddressDistrict(trimToNull(request.getAddressDistrict()));
        item.setAddressStreet(trimToNull(request.getAddressStreet()));
        item.setLinkName(trimToNull(request.getLinkName()));
        item.setLinkRelationCode(request.getLinkRelationCode());
        item.setLinkTelcom(trimToNull(request.getLinkTelcom()));
        item.setBloodTypeAbo(request.getBloodAbo());
        item.setBloodTypeRh(request.getBloodRh());
        item.setIdentifierItems(this.toIdentifierItemCommands(request.getPatientIdInfoList()));
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 组装逻辑删除患者命令（单条）。
     *
     * @param patientId     患者主键；{@code null} 返回 {@code null}
     * @param deleteReason 删除原因（可空，将 trim）
     * @return 命令或 {@code null}
     */
    public DeletePatientCommand toDeleteCommand(Long patientId, String deleteReason) {
        if (patientId == null) {
            return null;
        }
        DeletePatientCommand command = new DeletePatientCommand();
        DeletePatientCommand.DeletePatientItemCommand item = new DeletePatientCommand.DeletePatientItemCommand();
        item.setPatientId(patientId);
        item.setDeleteReason(trimToNull(deleteReason));
        command.setItem(item);
        return command;
    }

    /**
     * 证件实体列表 → DTO 列表（用于实体仍挂在聚合上的读路径）。
     *
     * @param list 实体列表；{@code null} 或空返回空列表
     * @return 非 {@code null} 列表
     */
    public List<PatientIdentifierItemDto> toIdentifierItemList(List<PatientIdentifier> list) {
        List<PatientIdentifierItemDto> result = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return result;
        }
        for (PatientIdentifier entity : list) {
            if (entity == null) {
                continue;
            }
            PatientIdentifierItemDto dto = new PatientIdentifierItemDto();
            dto.setId(entity.getId());
            if (entity.getStateEnum() != null && entity.getStateEnum().getSortOrder() != null) {
                dto.setUseEnum(entity.getStateEnum().getCode());
            }
            dto.setTypeCode(entity.getTypeCode());
            dto.setIdentifierNo(entity.getIdentifierNo());
            if (entity.getStartTime() != null) {
                dto.setStartTime(GeneralSharedConstants.DATE_TIME_FORMATTER.format(entity.getStartTime()));
            }
            if (entity.getEndTime() != null) {
                dto.setEndTime(GeneralSharedConstants.DATE_TIME_FORMATTER.format(entity.getEndTime()));
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * 前端证件行 → 领域证件子命令列表（新建语义 id 置空）。
     *
     * @param input 请求内嵌列表；{@code null} 或空返回 {@link Collections#emptyList()}
     * @return 子命令列表
     */
    private List<PatientIdentifierItemCommand> toIdentifierItemCommands(List<CreatePatientRequest.CreatePatientIdentifierRequest> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }
        List<PatientIdentifierItemCommand> result = new ArrayList<>();
        for (CreatePatientRequest.CreatePatientIdentifierRequest it : input) {
            if (it == null) {
                continue;
            }
            PatientIdentifierItemCommand cmd = new PatientIdentifierItemCommand();
            cmd.setId(null);
            cmd.setTypeCode(trimToNull(it.getTypeCode()));
            cmd.setIdentifierNo(trimToNull(it.getIdentifierNo()));
            if (it.getUseEnum() != null) {
                cmd.setStateEnum(EnumUtil.fromCode(IdentifierState.class, it.getUseEnum()));
            }
            cmd.setStartTime(parseDateTimeOrNull(it.getStartTime()));
            cmd.setEndTime(parseDateTimeOrNull(it.getEndTime()));
            result.add(cmd);
        }
        return result;
    }

    /**
     * 按全局日期时间格式解析字符串；空白返回 {@code null}，非法格式抛校验异常。
     *
     * @param input 原始字符串
     * @return {@link LocalDateTime} 或 {@code null}
     */
    private static LocalDateTime parseDateTimeOrNull(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String normalized = input.trim();
        try {
            return LocalDateTime.parse(normalized, GeneralSharedConstants.DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("证件时间格式非法：" + normalized);
        }
    }

    /**
     * 将性别持久化 code 转为术语展示文案；无法解析时原样返回入参便于排错。
     *
     * @param genderCode {@link AdministrativeGender#getCode()} 取值
     * @return 展示文案或原始 code
     */
    private String getGenderDisplayText(String genderCode) {
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, genderCode);
        if (gender == null) {
            return genderCode;
        }
        return gender.getDisplay();
    }

    /**
     * 空白归一为 {@code null}，减少领域命令中出现仅空格字符串。
     *
     * @param s 原始串
     * @return trim 后非空则返回 trim 结果，否则 {@code null}
     */
    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t;
    }

    /**
     * 同步填充启用布尔值与中文文案。
     *
     * @param dto        患者行 DTO
     * @param activeFlag 是否启用
     */
    private static void fillActiveFlagAndText(PatientDto dto, Boolean activeFlag) {
        if (Boolean.TRUE.equals(activeFlag)) {
            dto.setActiveFlag(Boolean.TRUE);
            dto.setActiveFlagText("启用");
        } else {
            dto.setActiveFlag(Boolean.FALSE);
            dto.setActiveFlagText("停用");
        }
    }

}

