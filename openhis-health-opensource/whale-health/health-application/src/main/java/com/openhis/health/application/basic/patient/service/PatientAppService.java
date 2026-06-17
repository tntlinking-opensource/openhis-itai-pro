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

package com.openhis.health.application.basic.patient.service;

import com.openhis.health.application.basic.patient.dto.PatientDto;
import com.openhis.health.application.basic.patient.dto.PatientInitDto;
import com.openhis.health.application.basic.patient.dto.PatientStudentDto;
import com.openhis.health.application.basic.patient.mapper.PatientMapper;
import com.openhis.health.application.basic.patient.mapper.PatientStudentMapper;
import com.openhis.health.application.basic.patient.request.CreatePatientRequest;
import com.openhis.health.application.basic.patient.request.PatientQueryRequest;
import com.openhis.health.application.basic.patient.request.SavePatientStudentRequest;
import com.openhis.health.application.basic.patient.request.UpdatePatientRequest;
import com.openhis.health.domain.administration.patient.command.DeletePatientCommand;
import com.openhis.health.domain.administration.patient.command.DisablePatientStudentRecordCommand;
import com.openhis.health.domain.administration.patient.command.ModifyPatientProfileCommand;
import com.openhis.health.domain.administration.patient.command.RegisterPatientCommand;
import com.openhis.health.domain.administration.patient.command.UpsertPatientStudentRecordCommand;
import com.openhis.health.domain.administration.patient.queries.PatientPagedQuery;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientDomainService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.*;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.setting.domain.SettingDefinitionEnum;
import com.openhis.whale.setting.domain.service.SettingManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * 患者主数据与学籍维护的应用编排入口。
 *
 * <p>职责：将网关/前端的 Request 与 DTO 装配为领域 {@code Command}/{@code Query}，按顺序调用
 * {@link PatientDomainService}、{@link PatientQueryService}；不做持久化细节与聚合不变量校验（归属领域层）。</p>
 *
 * <p>接入：{@code whale-web} 的 {@code AppServiceControllerRegistrar} 将本类注册为
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientAppService {

    /** 患者读侧查询（分页、详情、主档字段权威来源）。 */
    private final PatientQueryService patientQueryService;
    /** 患者写侧编排（建档、改档、学籍增删改）。 */
    private final PatientDomainService patientDomainService;
    /** 术语/枚举选项：初始化下拉数据。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** dto ↔ Request ↔ Command 的标准映射。 */
    private final PatientMapper patientMapper;
    /** 学籍与患者详情合并展示映射。 */
    private final PatientStudentMapper patientStudentMapper;
    /** 全局开关：是否启用学籍维护（与前端表单显隐联动）。 */
    private final SettingManager settingManager;
    /** 当前登录用户上下文（建档机构归属）。 */
    private final ICurrentUser currentUser;

    /**
     * 患者管理页初始化：下拉选项（含学籍维护所需的学历、学习形式、在校状态、体检结果等枚举）。
     *
     * @return 各类 OptionDto 列表，供前端表单/下拉使用
     */
    public PatientInitDto init() {
        PatientInitDto dto = new PatientInitDto();
        // 人口学与健康相关枚举：婚姻、职业、性别、血型、亲属关系、证件类型、是否类。
        dto.setMaritalStatus(codeSystemQueryService.getOptionListByEnum(MaritalStatus.class));
        dto.setOccupationType(codeSystemQueryService.getOptionListByEnum(OccupationType.class));
        dto.setSex(codeSystemQueryService.getOptionListByEnum(AdministrativeGender.class));
        dto.setBloodTypeABO(codeSystemQueryService.getOptionListByEnum(BloodTypeAbo.class));
        dto.setBloodTypeRH(codeSystemQueryService.getOptionListByEnum(BloodTypeRh.class));
        dto.setFamilyRelationshipType(codeSystemQueryService.getOptionListByEnum(FamilyRelationshipType.class));
        dto.setIdentityDocumentType(codeSystemQueryService.getOptionListByEnum(IdentityDocumentType.class));
        dto.setWhetherStatus(codeSystemQueryService.getOptionListByEnum(Whether.class));
        // 学籍子域专用枚举：学历、学习形式、在校状态、体检结论。
        dto.setEducationLevelOptions(codeSystemQueryService.getOptionListByEnum(EducationLevel.class));
        dto.setStudyModeOptions(codeSystemQueryService.getOptionListByEnum(StudyMode.class));
        dto.setStudentStatusOptions(codeSystemQueryService.getOptionListByEnum(StudentStatus.class));
        dto.setPhysicalExamResultOptions(codeSystemQueryService.getOptionListByEnum(PhysicalExamResult.class));
        // 业务开关：当前租户是否开放学籍维护入口。
        dto.setStudentSwitchValue(settingManager.getEffectiveValue(SettingDefinitionEnum.STUDENT_SWITCH.getName()));
        return dto;
    }


    /**
     * 保存学籍：同一患者仅保留一条未删除记录，已存在则更新，否则新增。
     * <p>
     * 姓名、性别、证件号、拼音码、五笔码始终从当前患者主表重新计算并写入，与请求体无关。
     * </p>
     *
     * @param req 学籍可编辑字段（患者 ID、学号、学院等），不含姓名/证件号等由患者主档决定的字段
     * @return 保存后的学籍 DTO（含同步后的姓名、证件号等）
     * @throws ValidationException 请求体或必填字段不合法
     * @throws EntityNotFoundException 患者主档不存在或不可见
     */
    public PatientStudentDto savePatientStudent(SavePatientStudentRequest req) {
        // 入参校验：学籍维护至少需要患者主键与学号。
        if (req == null) {
            throw new ValidationException("保存学籍失败：请求体不能为空");
        }
        if (req.getPatientId() == null) {
            throw new ValidationException("保存学籍失败：patientId 不能为空");
        }
        if (req.getStudentId() == null || req.getStudentId().trim().isEmpty()) {
            throw new ValidationException("保存学籍失败：studentId（学号）不能为空");
        }

        // 读取患者主档：姓名、性别、证件号、拼音码、五笔码以主档为权威输入，忽略请求体同名字段（若未来扩展）。
        PatientDetailQueryModel detail = patientQueryService.loadPatientDetail(req.getPatientId());
        if (detail == null || detail.getPatient() == null) {
            throw new EntityNotFoundException("患者不存在或已删除");
        }

        // 同一患者仅保留一条有效学籍：若已存在记录，则本次按更新语义传入 studentId；否则按新增语义传 null。
        Long existedStudentId = this.resolveExistingStudentId(detail);

        // 组装领域命令：写侧仍会再次加载患者实体并校验状态，本层仅负责把主档权威字段带入学籍维护。
        UpsertPatientStudentRecordCommand command =
            patientStudentMapper.toUpsertCommand(req, detail, existedStudentId);

        // 当前接口未暴露操作人信息，updatedBy 允许为空；如后续接入登录态，应在此处补齐 operatorId。
        patientDomainService.upsertPatientStudentRecord(command);

        // 回读最新学籍并合并主档字段，作为前端回显的单条学籍 DTO。
        PatientDetailQueryModel refreshed = patientQueryService.loadPatientDetail(req.getPatientId());
        return patientStudentMapper.toStudentDto(refreshed);
    }

    /**
     * 从患者详情中解析当前有效学籍记录标识。
     *
     * <p>说明：本方法体现“一患一学籍”的业务选择规则（取 students 首条），不属于对象字段映射职责，故不下沉到 mapper。</p>
     *
     * @param detail 患者详情（含 students 列表）
     * @return 已存在返回学籍主键；不存在返回 null
     */
    private Long resolveExistingStudentId(PatientDetailQueryModel detail) {
        // 业务分支处理：无学籍记录时按新增语义处理。
        if (detail == null || detail.getStudents() == null || detail.getStudents().isEmpty()) {
            return null;
        }
        // 业务分支处理：按“一患者一学籍”约束取首条；若历史数据存在多条，应在数据治理中收敛。
        if (detail.getStudents().get(0) == null) {
            return null;
        }
        return detail.getStudents().get(0).getId();
    }

    /**
     * 删除指定患者下当前有效的学籍记录（逻辑删除 is_deleted=true）。
     * <p>
     * 若该患者无有效学籍，则静默成功，不抛错。
     * </p>
     *
     * @param patientId 患者主键
     * @throws ValidationException {@code patientId} 为空
     * @throws EntityNotFoundException 患者主档不存在或不可见
     */
    public void deletePatientStudent(Long patientId) {
        if (patientId == null) {
            throw new ValidationException("删除学籍失败：patientId 不能为空");
        }

        PatientDetailQueryModel detail = patientQueryService.loadPatientDetail(patientId);
        // 业务分支处理：患者不存在时沿用其他接口风格，明确抛错（与“无学籍静默成功”区分）。
        if (detail == null || detail.getPatient() == null) {
            throw new EntityNotFoundException("患者不存在或已删除");
        }

        Long studentId = this.resolveExistingStudentId(detail);
        // 业务分支处理：无有效学籍时静默成功。
        if (studentId == null) {
            return;
        }

        DisablePatientStudentRecordCommand command = new DisablePatientStudentRecordCommand();
        DisablePatientStudentRecordCommand.DisablePatientStudentRecordItemCommand item =
            new DisablePatientStudentRecordCommand.DisablePatientStudentRecordItemCommand();
        item.setPatientId(patientId);
        item.setStudentId(studentId);
        item.setReason("删除学籍");
        command.setItems(java.util.Collections.singletonList(item));
        // 操作人由底层框架自动获取，本层不在命令中透传。

        try {
            patientDomainService.disablePatientStudentRecord(command);
        } catch (BusinessException ex) {
            // 幂等：并发下可能已被其他请求停用，视为成功；其他业务异常继续抛出。
            if (ErrorCodeConstants.PATIENT_INVALID_STATUS.equals(ex.getErrorCode())) {
                return;
            }
            throw ex;
        }
    }

    /**
     * 患者列表分页查询（支持姓名关键字检索）。
     *
     * @param request 分页参数与检索关键字
     * @return 分页患者简要信息
     */
    public PagedResult<PatientDto> getPatientPage(PatientQueryRequest request) {
        PatientPagedQuery query = new PatientPagedQuery();
        // 分页与 keyword 装配与门诊选患者（pagePatientsForSelect）及 B.10.11 一致。
        if (request != null) {
            query.setPageNo(request.getPageNo());
            query.setPageSize(request.getPageSize());
            if (StringUtils.isNotBlank(request.getKeyword())) {
                query.setKeyword(request.getKeyword().trim());
            }
        }

        // 读侧分页：仅查询展示所需字段，写意图不在此接口体现。
        Page<PatientPageItemQueryModel> page = patientQueryService.searchPatientsPaged(query);
        Page<PatientDto> dtoPage = page.map(patientMapper::toPageItemDto);
        return PagedResult.fromPage(dtoPage);
    }

    /**
     * 按 ID 获取患者详情，含基础信息与其他证件/标识列表（adm_patient_identifier），用于编辑回显。
     *
     * @param id 患者主键
     * @return 患者 DTO，含 patientIdInfoList
     * @throws ValidationException {@code id} 为空
     * @throws EntityNotFoundException 患者不存在或已删除
     */
    public PatientDto getPatient(Long id) {
        if (id == null) {
            throw new ValidationException("查询患者详情失败：id 不能为空");
        }

        PatientDetailQueryModel detail;
        try {
            // 详情加载失败时按错误码区分「不存在」与其它业务失败，向上转为统一的接入层异常语义。
            detail = patientQueryService.loadPatientDetail(id);
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.PATIENT_NOT_FOUND.equals(ex.getErrorCode())) {
                throw new EntityNotFoundException("患者不存在或已删除");
            }
            throw ex;
        }

        PatientDto dto = patientMapper.toDetailDto(detail);
        if (dto == null) {
            throw new EntityNotFoundException("患者不存在或已删除");
        }
        return dto;
    }

    /**
     * 新建患者档案并返回详情。
     *
     * @param request 建档请求
     * @return 新建后的患者详情 DTO
     * @throws ValidationException 请求为空或命令映射失败
     * @throws EntityNotFoundException 建档成功后回读详情失败（极少见，通常表示并发删除）
     */
    public PatientDto createPatient(CreatePatientRequest request) {
        if (request == null) {
            throw new ValidationException("新增患者失败：请求体不能为空");
        }
        // 标准链：Request → RegisterPatientCommand → 领域建档 → 按新主键回读详情 DTO。
        RegisterPatientCommand command = patientMapper.toRegisterCommand(request);
        if (command == null) {
            throw new ValidationException("新增患者失败：命令组装失败");
        }
        // 建档机构归属当前登录机构，与门诊挂号新建患者主档一致，禁止由前端传入 organizationId。
        Long organizationId = currentUser.getCurrentOrganizationUnitId();
        if (organizationId == null) {
            throw new ValidationException("新增患者失败：当前登录机构不能为空");
        }
        if (command.getItem() != null) {
            command.getItem().setOrganizationId(organizationId);
        }
        if (command.getItems() != null) {
            for (RegisterPatientCommand.RegisterPatientItemCommand item : command.getItems()) {
                item.setOrganizationId(organizationId);
            }
        }
        Long patientId = patientDomainService.registerPatient(command);
        return this.getPatient(patientId);
    }

    /**
     * 修改患者档案并返回最新详情。
     *
     * @param request 修改请求（须含患者主键）
     * @return 更新后的患者详情 DTO
     * @throws ValidationException 请求、主键或命令映射不合法
     * @throws EntityNotFoundException 患者不存在或已删除
     */
    public PatientDto editPatient(UpdatePatientRequest request) {
        if (request == null) {
            throw new ValidationException("修改患者失败：请求体不能为空");
        }
        if (request.getId() == null) {
            throw new ValidationException("修改患者失败：id 不能为空");
        }
        ModifyPatientProfileCommand command = patientMapper.toModifyCommand(request);
        if (command == null) {
            throw new ValidationException("修改患者失败：命令组装失败");
        }
        patientDomainService.modifyPatientProfile(command);
        return this.getPatient(request.getId());
    }


    /**
     * 逻辑删除患者。
     *
     * @param id 患者主键
     * @throws ValidationException {@code id} 为空或命令映射失败
     */
    public void deletePatient(Long id) {
        if (id == null) {
            throw new ValidationException("删除患者失败：id 不能为空");
        }
        DeletePatientCommand command = patientMapper.toDeleteCommand(id, "删除患者");
        if (command == null) {
            throw new ValidationException("删除患者失败：命令组装失败");
        }
        patientDomainService.deletePatient(command);
    }
}
