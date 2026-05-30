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

package com.openhis.health.domain.administration.encounter.service;

import com.openhis.health.domain.share.constants.EncounterConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterDiagnosis;
import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import com.openhis.health.domain.administration.encounter.entity.EncounterParticipant;
import com.openhis.health.domain.administration.encounter.queries.EmergencyEncounterDoctorListQuery;
import com.openhis.health.domain.administration.encounter.queries.EncounterProjectionQuery;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.OutpatientDuplicateCheckQuery;
import com.openhis.health.domain.administration.encounter.queries.ClinicRegisterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.OutpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.RegistrationSlipPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.ClinicRegisterQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.RegistrationSlipQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterCompletionCheckQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterLocationInfoQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterLocationQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterParticipantQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterProjectionQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterSimpleQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OpenInpatientLineQuery;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientDuplicateCheckQueryModel;
import com.openhis.health.domain.administration.encounter.repository.EncounterDiagnosisRepository;
import com.openhis.health.domain.administration.encounter.repository.EncounterLocationRepository;
import com.openhis.health.domain.administration.encounter.repository.EncounterParticipantRepository;
import com.openhis.health.domain.administration.encounter.repository.EncounterRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.share.enums.EncounterActivityStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 就诊读侧查询服务。
 *
 * <p>职责：加载就诊主档与参与者/位置/诊断等子集查询模型，提供分页投影、完诊校验与门诊重复挂号校验等只读能力。</p>
 *
 * <p>边界：不写库；强一致详情以聚合仓储为准；投影列表中患者姓名等字段若与读模型不一致，以代码内 DOC-CONFLICT 为准。</p>
 */
@Service
@RequiredArgsConstructor
public class EncounterQueryService {

    private final EncounterRepository encounterRepository;
    private final EncounterParticipantRepository participantRepository;
    private final EncounterLocationRepository locationRepository;
    private final EncounterDiagnosisRepository diagnosisRepository;

    /**
     * 按主键加载就诊详情查询模型。
     *
     * @param encounterId 就诊标识
     * @return 详情模型
     * @throws BusinessException 就诊不存在时抛出
     */
    public EncounterDetailQueryModel loadEncounterById(Long encounterId) {
        // 强依赖主档：不存在则抛错，避免下游用空主键继续展示。
        Encounter encounter = this.mustGetEncounterEntity(encounterId);
        return this.toDetailModel(encounter);
    }

    /**
     * 与 {@link #loadEncounterById(Long)} 等价，兼容历史命名。
     *
     * @param encounterId 就诊标识
     * @return 详情模型
     * @throws BusinessException 就诊不存在时抛出
     */
    public EncounterDetailQueryModel mustGetEncounter(Long encounterId) {
        return this.loadEncounterById(encounterId);
    }

    /**
     * 按主键加载指定就诊类别的详情（住院登记、门诊挂号等须与通用 {@link #mustGetEncounter(Long)} 隔离）。
     *
     * @param encounterId 就诊标识
     * @param classEnum 期望的就诊类别 {@code code}（如 {@link EncounterClass#IMP}、{@link EncounterClass#AMB}）
     * @param wrongClassErrorCode 主键存在但类别不符时的业务错误码
     * @param wrongClassMessage 类别不符时的提示文案
     * @return 详情模型
     * @throws BusinessException 记录不存在或类别不符时抛出
     */
    public EncounterDetailQueryModel mustGetEncounterOfClass(
            Long encounterId,
            String classEnum,
            String wrongClassErrorCode,
            String wrongClassMessage) {
        Encounter encounter = this.mustGetEncounterEntityOfClass(
            encounterId, classEnum, wrongClassErrorCode, wrongClassMessage);
        return this.toDetailModel(encounter);
    }

    /**
     * 读侧门控：按主键与就诊类别加载聚合根实体（应用层校验后读取 organizationId 等头字段）。
     *
     * @param encounterId 就诊标识
     * @param classEnum 期望的就诊类别 {@code code}
     * @param wrongClassErrorCode 主键存在但类别不符时的业务错误码
     * @param wrongClassMessage 类别不符时的提示文案
     * @return 非空就诊实体
     * @throws BusinessException 记录不存在或类别不符时抛出
     */
    public Encounter requireEncounterEntityOfClass(
            Long encounterId,
            String classEnum,
            String wrongClassErrorCode,
            String wrongClassMessage) {
        return this.mustGetEncounterEntityOfClass(
            encounterId, classEnum, wrongClassErrorCode, wrongClassMessage);
    }

    /**
     * 按业务单号加载就诊摘要（用于扫码、外链等按单号定位场景）。
     *
     * @param busNo 就诊业务单号
     * @return 摘要模型
     * @throws BusinessException 单号不存在时抛出
     */
    public EncounterSimpleQueryModel loadEncounterByBusNo(String busNo) {
        // 业务单号常用于扫码入场，单号无效须明确失败以便终端提示重扫。
        Encounter encounter = encounterRepository.findByBusNo(busNo)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在"));
        EncounterSimpleQueryModel model = new EncounterSimpleQueryModel();
        model.setId(encounter.getId());
        model.setBusNo(encounter.getBusNo());
        model.setClassEnum(encounter.getClassEnum());
        model.setStatusEnum(encounter.getStatusEnum());
        return model;
    }

    /**
     * 发药单列表关键字：按就诊业务号模糊匹配未删除就诊主键。
     *
     * @param keyword 用户关键字；空白时返回空列表
     * @return 命中就诊主键列表
     */
    public List<Long> listEncounterIdsByBusNoKeyword(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return Collections.emptyList();
        }
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(keyword.trim());
        Specification<Encounter> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
        List<Encounter> rows = this.encounterRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (Encounter row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 发药单列表关键字：按患者主键集合反查未删除就诊主键。
     *
     * @param patientIds 患者主键集合；空时返回空列表
     * @return 就诊主键列表
     */
    public List<Long> listEncounterIdsByPatientIds(Collection<Long> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<Encounter> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            root.get(FieldNameConstants.PATIENT_ID).in(patientIds));
        List<Encounter> rows = this.encounterRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (Encounter row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 列出就诊下仍处于激活状态的参与者（如当前在岗医生、护士）。
     *
     * @param encounterId 就诊标识
     * @return 参与者视图列表
     */
    public List<EncounterParticipantQueryModel> listActiveEncounterParticipants(Long encounterId) {
        List<EncounterParticipant> rows = participantRepository
            .findAllByEncounterIdAndStatusEnum(encounterId, EncounterActivityStatus.ACTIVE.getCode());
        List<EncounterParticipantQueryModel> models = new ArrayList<>();
        // Loop：遍历参与者记录，逐条装配查询模型
        for (EncounterParticipant row : rows) {
            EncounterParticipantQueryModel model = new EncounterParticipantQueryModel();
            model.setParticipantId(row.getId());
            model.setEncounterId(row.getEncounterId());
            model.setRoleCode(row.getRoleCode());
            model.setPractitionerId(row.getPractitionerId());
            model.setStatusEnum(row.getStatusEnum());
            model.setStartTime(row.getStartTime());
            model.setEndTime(row.getEndTime());
            models.add(model);
        }
        return models;
    }

    /**
     * 按角色编码筛选激活参与者（如仅主治、仅麻醉师）。
     *
     * @param encounterId 就诊标识
     * @param roleCode    参与者角色编码
     * @return 参与者视图列表
     */
    public List<EncounterParticipantQueryModel> listActiveEncounterParticipantsByType(Long encounterId, String roleCode) {
        List<EncounterParticipant> rows = participantRepository
            .findAllByEncounterIdAndRoleCodeAndStatusEnum(
                encounterId, roleCode, EncounterActivityStatus.ACTIVE.getCode());
        List<EncounterParticipantQueryModel> models = new ArrayList<>();
        // Loop：遍历角色匹配的参与者，装配查询模型
        for (EncounterParticipant row : rows) {
            EncounterParticipantQueryModel model = new EncounterParticipantQueryModel();
            model.setParticipantId(row.getId());
            model.setEncounterId(row.getEncounterId());
            model.setRoleCode(row.getRoleCode());
            model.setPractitionerId(row.getPractitionerId());
            model.setStatusEnum(row.getStatusEnum());
            model.setStartTime(row.getStartTime());
            model.setEndTime(row.getEndTime());
            models.add(model);
        }
        return models;
    }

    /**
     * 列出就诊的位置轨迹（按开始时间升序，含历史科室/床位）。
     *
     * @param encounterId 就诊标识
     * @return 位置记录视图列表
     */
    public List<EncounterLocationQueryModel> listEncounterLocations(Long encounterId) {
        List<EncounterLocation> rows = locationRepository.findAllByEncounterIdOrderByStartTimeAsc(encounterId);
        List<EncounterLocationQueryModel> models = new ArrayList<>();
        // Loop：遍历位置记录，装配查询模型（含历史科室/床位）
        for (EncounterLocation row : rows) {
            EncounterLocationQueryModel model = new EncounterLocationQueryModel();
            model.setLocationRecordId(row.getId());
            model.setEncounterId(row.getEncounterId());
            model.setLocationId(row.getLocationId());
            model.setFormEnum(row.getFormEnum());
            model.setStatusEnum(row.getStatusEnum());
            model.setStartTime(row.getStartTime());
            model.setEndTime(row.getEndTime());
            models.add(model);
        }
        return models;
    }

    /**
     * 加载当前就诊活动位置信息。
     *
     * @param encounterId 就诊标识
     * @return 当前位置模型
     */
    public EncounterLocationInfoQueryModel getEncounterLocationInfo(Long encounterId) {
        // 先校验就诊存在，再取当前活动位置，避免对幽灵就诊组装位置信息。
        this.mustGetEncounterEntity(encounterId);
        List<EncounterLocation> activeRows = locationRepository
            .findAllByEncounterIdAndStatusEnum(encounterId, EncounterActivityStatus.ACTIVE.getCode());
        EncounterLocationInfoQueryModel model = new EncounterLocationInfoQueryModel();
        model.setEncounterId(encounterId);
        // 无激活位置记录时，返回空壳模型，由 UI 展示「未分诊/未入区」等状态。
        if (activeRows.isEmpty()) {
            return model;
        }
        EncounterLocation active = activeRows.get(0);
        model.setCurrentLocationId(active.getLocationId());
        model.setCurrentFormEnum(active.getFormEnum());
        model.setActiveSince(active.getStartTime());
        return model;
    }

    /**
     * 列出就诊关联的诊断子表行（EncounterDiagnosis，非 Condition 事实表全量）。
     *
     * @param encounterId 就诊标识
     * @return 诊断子表视图列表
     */
    public List<EncounterDiagnosisQueryModel> listEncounterDiagnoses(Long encounterId) {
        List<EncounterDiagnosis> rows = diagnosisRepository.findAllByEncounterId(encounterId);
        List<EncounterDiagnosisQueryModel> models = new ArrayList<>();
        // Loop：遍历诊断记录，装配查询模型
        for (EncounterDiagnosis row : rows) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            EncounterDiagnosisQueryModel model = new EncounterDiagnosisQueryModel();
            model.setDiagnosisId(row.getId());
            model.setEncounterId(row.getEncounterId());
            model.setConditionId(row.getConditionId());
            model.setPrimaryFlag(row.getPrimaryFlag());
            model.setRank(row.getRank());
            model.setDescription(row.getDescription());
            model.setDiagTypeCode(row.getDiagTypeCode());
            model.setMedType(row.getMedType());
            models.add(model);
        }
        return models;
    }

    /**
     * 按 {@code adm_encounter_diagnosis.id} 批量解析医保/诊疗类别（{@code med_type}）。
     *
     * @param diagnosisIds 就诊诊断关联行主键集合
     * @return 关联主键 → med_type；未命中时不包含该键
     */
    public Map<Long, String> mapMedTypeByEncounterDiagnosisIds(Collection<Long> diagnosisIds) {
        if (diagnosisIds == null || diagnosisIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        // Loop：遍历入参集合，提取有效诊断主键
        for (Long id : diagnosisIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EncounterDiagnosis> rows = diagnosisRepository.findAllByIdIn(unique);
        Map<Long, String> out = new HashMap<>(rows.size() * 2);
        // Loop：遍历诊断记录，构建主键到医保诊疗类别的映射
        for (EncounterDiagnosis row : rows) {
            if (row != null && row.getId() != null) {
                out.put(row.getId(), row.getMedType());
            }
        }
        return out;
    }

    /**
     * 分页查询就诊投影列表。
     *
     * @param query 分页查询入参
     * @return 分页结果
     */
    public Page<EncounterProjectionQueryModel> queryEncounterProjections(EncounterProjectionQuery query) {
        int pageNo = 0;
        int pageSize = 20;
        if (query != null && query.getPageNo() != null && query.getPageNo() > 0) {
            pageNo = query.getPageNo() - 1;
        }
        if (query != null && query.getPageSize() != null && query.getPageSize() > 0) {
            pageSize = query.getPageSize();
        }
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
        Specification<Encounter> specification = (root, criteriaQuery, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null) {
                if (query.getPatientId() != null) {
                    predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
                }
                if (query.getClassEnum() != null) {
                    predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.CLASS_ENUM), query.getClassEnum()));
                }
                if (query.getStatusEnum() != null) {
                    predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
                }
                if (query.getOrganizationId() != null) {
                    predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()));
                }
            }
            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<Encounter> page = encounterRepository.findAll(specification, pageable);
        List<EncounterProjectionQueryModel> rows = new ArrayList<>();
        // Loop：遍历结果集，逐条装配展示字段
        for (Encounter encounter : page.getContent()) {
            EncounterProjectionQueryModel model = new EncounterProjectionQueryModel();
            model.setEncounterId(encounter.getId());
            // DOC-CONFLICT: SDD 定义 patientName 来源 encounter_projection 投影表，当前领域层未引入该投影实体，暂以 patientId 字符串占位。
            model.setPatientName(String.valueOf(encounter.getPatientId()));
            model.setClassEnum(encounter.getClassEnum());
            model.setStatusEnum(encounter.getStatusEnum());
            model.setOrganizationId(encounter.getOrganizationId());
            rows.add(model);
        }
        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    /**
     * 急诊医生站：分页查询 {@link EncounterClass#EMER} 就诊主数据（不经门诊信息读模型）。
     *
     * @param query 查询入参；{@code statusEnumIn} 应由应用层保证非空，否则结果集无状态谓词约束
     * @param keywordMatchedPatientIds 关键字经 {@code PatientQueryService} 预解析的患者主键；无关键字匹配患者时可为空列表
     * @return 分页实体页
     */
    public Page<Encounter> pageEmergencyEncountersForDoctorStation(
        EmergencyEncounterDoctorListQuery query,
        Collection<Long> keywordMatchedPatientIds) {
        int pageNo = 0;
        int pageSize = 20;
        if (query != null && query.getPageNo() != null && query.getPageNo() > 0) {
            pageNo = query.getPageNo() - 1;
        }
        if (query != null && query.getPageSize() != null && query.getPageSize() > 0) {
            pageSize = Math.min(query.getPageSize(), 1000);
        }
        Pageable pageable = PageRequest.of(
            pageNo,
            pageSize,
            Sort.by(Sort.Order.desc(FieldNameConstants.START_TIME), Sort.Order.desc(FieldNameConstants.ID))
        );
        Specification<Encounter> specification = (root, criteriaQuery, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.EMER.getCode()));
            if (query != null) {
                if (query.getOrganizationId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()));
                }
                if (query.getStartTimeFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.START_TIME), query.getStartTimeFrom()));
                }
                if (query.getStartTimeTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.START_TIME), query.getStartTimeTo()));
                }
                if (query.getStatusEnumIn() != null && !query.getStatusEnumIn().isEmpty()) {
                    predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(query.getStatusEnumIn()));
                }
                if (StringUtils.isNotBlank(query.getKeyword())) {
                    String trimmed = query.getKeyword().trim();
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                    List<jakarta.persistence.criteria.Predicate> keywordOr = new ArrayList<>();
                    keywordOr.add(cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
                    if (keywordMatchedPatientIds != null && !keywordMatchedPatientIds.isEmpty()) {
                        keywordOr.add(root.get(FieldNameConstants.PATIENT_ID).in(keywordMatchedPatientIds));
                    }
                    predicates.add(cb.or(keywordOr.toArray(new jakarta.persistence.criteria.Predicate[0])));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return encounterRepository.findAll(specification, pageable);
    }

    /**
     * 校验门诊是否可完诊。
     *
     * @param encounterId 就诊标识
     * @return 校验结果
     */
    public EncounterCompletionCheckQueryModel checkOutpatientCompletable(Long encounterId) {
        Encounter encounter = this.mustGetEncounterEntity(encounterId);
        EncounterCompletionCheckQueryModel result = new EncounterCompletionCheckQueryModel();
        result.setEncounterId(encounterId);
        result.setCompletable(Boolean.FALSE);
        // 完诊校验仅针对门诊类别；其他类别直接拒绝并提示。
        if (!EncounterClass.AMB.getCode().equals(encounter.getClassEnum())) {
            result.setFailureCode(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS);
            result.setFailureMessage("当前就诊类别不是门诊");
            return result;
        }
        // 仅「进行中/暂停」允许完诊，避免已结束就诊重复完诊。
        if (!EncounterStatus.IN_PROGRESS.getCode().equals(encounter.getStatusEnum())
            && !EncounterStatus.ON_HOLD.getCode().equals(encounter.getStatusEnum())) {
            result.setFailureCode(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS);
            result.setFailureMessage("当前状态不允许完诊");
            return result;
        }
        List<EncounterParticipant> activeDoctors = participantRepository
            .findAllByEncounterIdAndRoleCodeAndStatusEnum(
                encounterId, EncounterConstants.ROLE_ADMITTER, EncounterActivityStatus.ACTIVE.getCode());
        // 门诊完诊需有在班的接诊医生参与者，否则业务上无法闭环责任主体。
        if (activeDoctors.isEmpty()) {
            result.setFailureCode(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT);
            result.setFailureMessage("缺少负责医生参与者");
            return result;
        }
        result.setCompletable(Boolean.TRUE);
        result.setFailureCode("OK");
        result.setFailureMessage("通过");
        return result;
    }

    /**
     * 校验门诊重复挂号。
     *
     * <p>规则：同一自然日、同一患者、同一目标科室下，若已存在占用名额的门诊就诊（含诊毕 {@link EncounterStatus#DISCHARGED} 等终态），则视为重复；
     * {@link EncounterStatus#PLANNED}（未交费）不占名额；{@link EncounterStatus#CANCELLED}（退号）释放名额。</p>
     *
     * @param query 校验入参
     * @return 校验结果
     */
    public OutpatientDuplicateCheckQueryModel checkOutpatientDuplicateRegistration(OutpatientDuplicateCheckQuery query) {
        if (query == null || query.getPatientId() == null || query.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "患者标识和目标科室必填");
        }
        LocalDate registrationDay = query.getRegistrationDate();
        if (registrationDay == null) {
            registrationDay = LocalDate.now();
        }
        LocalDateTime dayStart = registrationDay.atStartOfDay();
        LocalDateTime dayEndExclusive = registrationDay.plusDays(1).atStartOfDay();
        List<String> blockingStatuses = Arrays.asList(
            EncounterStatus.REGISTERED.getCode(),
            EncounterStatus.TRIAGED.getCode(),
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode(),
            EncounterStatus.DISCHARGED.getCode(),
            EncounterStatus.COMPLETED.getCode()
        );
        List<String> outpatientClasses = Arrays.asList(EncounterClass.AMB.getCode(), EncounterClass.EMER.getCode());
        boolean duplicated = existsPaidOutpatientEncounterForDepartmentOnCalendarDay(
            query.getPatientId(),
            query.getOrganizationId(),
            outpatientClasses,
            blockingStatuses,
            dayStart,
            dayEndExclusive);

        OutpatientDuplicateCheckQueryModel model = new OutpatientDuplicateCheckQueryModel();
        model.setDuplicated(duplicated);
        model.setPatientId(query.getPatientId());
        model.setOrganizationId(query.getOrganizationId());
        if (duplicated) {
            Specification<Encounter> specification = (root, cq, cb) -> cb.and(
                cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()),
                cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()),
                root.get(FieldNameConstants.CLASS_ENUM).in(outpatientClasses),
                root.get(FieldNameConstants.STATUS_ENUM).in(blockingStatuses),
                cb.greaterThanOrEqualTo(root.get(FieldNameConstants.CREATED_AT), dayStart),
                cb.lessThan(root.get(FieldNameConstants.CREATED_AT), dayEndExclusive),
                cb.isFalse(root.get(FieldNameConstants.IS_DELETED))
            );
            List<Encounter> encounters = encounterRepository.findAll(specification);
            List<Long> encounterIds = new ArrayList<>();
            // Loop：提取重复就诊的主键列表
            for (Encounter encounter : encounters) {
                encounterIds.add(encounter.getId());
            }
            model.setEncounterIds(encounterIds);
            return model;
        }
        model.setEncounterIds(Collections.emptyList());
        return model;
    }

    /**
     * 急诊分诊保存：查找该患者、科室、号源类型下最近一条尚未终结的急诊就诊，用于复用（禁止并行多条未结束急诊就诊）。
     *
     * @param patientId 患者标识
     * @param organizationId 与分诊行一致的挂号科室
     * @param serviceTypeId 号源类型（当前急诊开立固定为 1）
     * @return 可复用的就诊主键；不存在则 empty
     */
    public Optional<Long> findLatestActiveEmergencyEncounterId(
            Long patientId, Long organizationId, Long serviceTypeId) {
        if (patientId == null || organizationId == null || serviceTypeId == null) {
            return Optional.empty();
        }
        List<String> terminalStatuses =
                Arrays.asList(
                        EncounterStatus.DISCHARGED.getCode(),
                        EncounterStatus.COMPLETED.getCode(),
                        EncounterStatus.CANCELLED.getCode(),
                        EncounterStatus.ENTERED_IN_ERROR.getCode());
        Specification<Encounter> specification =
            buildLatestNonTerminalEmergencyEncounterSpec(
                patientId, organizationId, serviceTypeId, EncounterClass.EMER.getCode(), terminalStatuses);
        Page<Encounter> page =
            encounterRepository.findAll(specification, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id")));
        if (page.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(page.getContent().get(0).getId());
    }

    /**
     * 同日同科室门诊防重：是否存在占用名额的就诊（与 {@link EncounterDomainService} 写侧断言口径一致）。
     *
     * @param patientId 患者主键
     * @param organizationId 挂号科室/机构主键
     * @param outpatientClassEnums 门诊类别 code 集合（如 OP/EM）
     * @param paidStatusEnums 视为已占用号源的状态 code 集合
     * @param dayStartInclusive 自然日起始（含）
     * @param dayEndExclusive 自然日结束（不含）
     * @return 存在至少一条匹配就诊时 {@code true}
     */
    public boolean existsPaidOutpatientEncounterForDepartmentOnCalendarDay(
        Long patientId,
        Long organizationId,
        Collection<String> outpatientClassEnums,
        Collection<String> paidStatusEnums,
        LocalDateTime dayStartInclusive,
        LocalDateTime dayEndExclusive) {
        Specification<Encounter> specification = buildPaidOutpatientDaySpec(
            patientId, organizationId, outpatientClassEnums, paidStatusEnums, dayStartInclusive, dayEndExclusive);
        return encounterRepository.count(specification) > 0;
    }

    /**
     * 构造「同日同科室已占用门诊号源」的 JPA 规约（供 count 防重，B.2 禁仓储 @Query）。
     */
    private static Specification<Encounter> buildPaidOutpatientDaySpec(
        Long patientId,
        Long organizationId,
        Collection<String> outpatientClassEnums,
        Collection<String> paidStatusEnums,
        LocalDateTime dayStartInclusive,
        LocalDateTime dayEndExclusive) {
        return (root, query, cb) -> cb.and(
            cb.equal(root.get(FieldNameConstants.PATIENT_ID), patientId),
            cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId),
            root.get(FieldNameConstants.CLASS_ENUM).in(outpatientClassEnums),
            root.get(FieldNameConstants.STATUS_ENUM).in(paidStatusEnums),
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.greaterThanOrEqualTo(root.get(FieldNameConstants.CREATED_AT), dayStartInclusive),
            cb.lessThan(root.get(FieldNameConstants.CREATED_AT), dayEndExclusive));
    }

    /**
     * 构造「患者+科室+号源下最近一条未终结急诊就诊」的 JPA 规约（分诊复用就诊）。
     */
    private static Specification<Encounter> buildLatestNonTerminalEmergencyEncounterSpec(
        Long patientId,
        Long organizationId,
        Long serviceTypeId,
        String classEnum,
        Collection<String> excludedStatuses) {
        return (root, query, cb) -> cb.and(
            cb.equal(root.get(FieldNameConstants.PATIENT_ID), patientId),
            cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId),
            cb.equal(root.get(FieldNameConstants.SERVICE_TYPE_ID), serviceTypeId),
            cb.equal(root.get(FieldNameConstants.CLASS_ENUM), classEnum),
            cb.not(root.get(FieldNameConstants.STATUS_ENUM).in(excludedStatuses)));
    }

    /**
     * 按门诊/急诊锚点查询未取消的住院线（最新一条）。
     *
     * @param ambEncounterId 源门诊或急诊就诊标识
     * @return 查询结果；无命中时 {@link OpenInpatientLineQuery#isExists()} 为 false
     */
    public OpenInpatientLineQuery findOpenInpatientLine(Long ambEncounterId) {
        OpenInpatientLineQuery result = new OpenInpatientLineQuery();
        result.setExists(false);
        if (ambEncounterId == null) {
            return result;
        }
        List<String> excluded = Collections.singletonList(EncounterStatus.CANCELLED.getCode());
        Optional<Encounter> row = encounterRepository.findTopByAmbEncounterIdAndClassEnumAndStatusEnumNotInOrderByCreatedAtDesc(
            ambEncounterId, EncounterClass.IMP.getCode(), excluded);
        if (row.isEmpty()) {
            return result;
        }
        Encounter inpatient = row.get();
        result.setExists(true);
        result.setEncounterId(inpatient.getId());
        result.setBusNo(inpatient.getBusNo());
        result.setStatusEnum(inpatient.getStatusEnum());
        return result;
    }

    /**
     * 判断门诊/急诊锚点下是否已有未取消住院线。
     *
     * @param ambEncounterId 源就诊标识
     * @return 已存在未取消住院线时 true
     */
    public boolean existsOpenInpatientLine(Long ambEncounterId) {
        return this.findOpenInpatientLine(ambEncounterId).isExists();
    }

    /**
     * 断言源就诊可从门急诊发起住院申请（BR-IP-REQ-001）。
     *
     * @param ambEncounterId 源门诊或急诊就诊标识
     * @throws BusinessException 就诊不存在、类别不符、已终态或状态不允许
     */
    public void assertAmbForInpatientReq(Long ambEncounterId) {
        Encounter amb = this.mustGetEncounterEntity(ambEncounterId);
        String classCode = amb.getClassEnum();
        if (!EncounterClass.AMB.getCode().equals(classCode) && !EncounterClass.EMER.getCode().equals(classCode)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "仅门诊或急诊就诊可发起住院申请");
        }
        EncounterStatus current = EnumUtil.fromCode(EncounterStatus.class, amb.getStatusEnum());
        if (current.isTerminal()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "源就诊已结束，无法办理住院");
        }
        List<String> eligible = Arrays.asList(
            EncounterStatus.REGISTERED.getCode(),
            EncounterStatus.TRIAGED.getCode(),
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode()
        );
        if (!eligible.contains(amb.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "源就诊当前状态不允许办理住院");
        }
    }

    /**
     * 加载就诊聚合根；不存在时抛出业务异常，供读侧强依赖主档的方法复用。
     *
     * @param encounterId 就诊标识
     * @return 非空就诊实体
     * @throws BusinessException 主键无对应有效记录时
     */
    private Encounter mustGetEncounterEntity(Long encounterId) {
        return encounterRepository.findById(encounterId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在"));
    }

    /**
     * 将就诊主档字段拷贝到详情查询模型（不含参与者/位置等子资源，需另行接口加载）。
     *
     * @param encounter 就诊实体
     * @return 详情模型
     */
    private EncounterDetailQueryModel toDetailModel(Encounter encounter) {
        EncounterDetailQueryModel model = new EncounterDetailQueryModel();
        model.setId(encounter.getId());
        model.setBusNo(encounter.getBusNo());
        model.setPatientId(encounter.getPatientId());
        model.setClassEnum(encounter.getClassEnum());
        model.setStatusEnum(encounter.getStatusEnum());
        model.setOrganizationId(encounter.getOrganizationId());
        model.setStartTime(encounter.getStartTime());
        model.setEndTime(encounter.getEndTime());
        model.setReceptionTime(encounter.getReceptionTime());
        model.setAdmitSourceCode(encounter.getAdmitSourceCode());
        model.setPriorityEnum(encounter.getPriorityEnum());
        model.setAmbEncounterId(encounter.getAmbEncounterId());
        return model;
    }

    /**
     * 批量解析住院就诊主键 → 源门诊/急诊就诊主键（{@code amb_encounter_id}）。
     *
     * <p>用于登记列表/弹窗展示：有医生申请转住院时须带回门诊锚点，无申请直登则无映射项。</p>
     *
     * @param encounterIds 住院就诊主键集合；{@code null} 或空时返回空映射
     * @return 住院就诊 ID → 源门诊就诊 ID（仅 {@code ambEncounterId} 非空的行）
     */
    public Map<Long, Long> mapAmbEncounterIdByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, Long> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        // 住院登记列表仅解析 IMP 线，避免误将门诊就诊 ID 带入映射。
        List<Encounter> rows =
            encounterRepository.findAllByIdInAndClassEnum(distinct, EncounterClass.IMP.getCode());
        for (Encounter row : rows) {
            if (row.getAmbEncounterId() != null) {
                result.put(row.getId(), row.getAmbEncounterId());
            }
        }
        return result;
    }

    /**
     * 批量解析住院就诊主键 → 住院业务号（{@code bus_no}）。
     *
     * <p>读模型 {@code encounter_bus_no} 未投影时，登记/预交金列表经本方法回填。</p>
     *
     * @param encounterIds 住院就诊主键集合；{@code null} 或空时返回空映射
     * @return 住院就诊 ID → 住院号
     */
    public Map<Long, String> mapBusNoByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, String> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        List<Encounter> rows =
            encounterRepository.findAllByIdInAndClassEnum(distinct, EncounterClass.IMP.getCode());
        for (Encounter row : rows) {
            if (row.getId() != null && StringUtils.isNotBlank(row.getBusNo())) {
                result.put(row.getId(), row.getBusNo());
            }
        }
        return result;
    }

    /**
     * 批量解析住院就诊主键 → 患者主键。
     *
     * <p>读模型 {@code patient_id} 未投影时，预交金/登记列表经本方法回填，避免患者-就诊一致性门控误判。</p>
     *
     * @param encounterIds 住院就诊主键集合；{@code null} 或空时返回空映射
     * @return 住院就诊 ID → 患者 ID
     */
    public Map<Long, Long> mapPatientIdByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, Long> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        List<Encounter> rows =
            encounterRepository.findAllByIdInAndClassEnum(distinct, EncounterClass.IMP.getCode());
        for (Encounter row : rows) {
            if (row.getId() != null && row.getPatientId() != null) {
                result.put(row.getId(), row.getPatientId());
            }
        }
        return result;
    }

    /**
     * 按住院就诊主键批量解析 {@code priority_enum}（护士站列表卡片，AS-IP-NUR-001 IF-IP-NUR-02）。
     *
     * <p>读模型未投影病情优先级时，应用层经本方法批量回填，避免逐条 N+1。</p>
     *
     * @param encounterIds 住院就诊主键集合；{@code null} 或空时返回空映射
     * @return 住院就诊 ID → {@code adm_encounter.priority_enum}；无值则不写入该键
     */
    public Map<Long, String> mapPriorityEnumByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, String> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        List<Encounter> rows =
            encounterRepository.findAllByIdInAndClassEnum(distinct, EncounterClass.IMP.getCode());
        for (Encounter row : rows) {
            if (row.getId() != null && StringUtils.isNotBlank(row.getPriorityEnum())) {
                result.put(row.getId(), row.getPriorityEnum().trim());
            }
        }
        return result;
    }

    /**
     * 按床位主键批量解析活动占床就诊（AS-IP-NUR-002 ATD-01，DM-ADM-002 §6.2.12）。
     *
     * @param locationIds 床位级 {@code adm_location.id} 集合
     * @return 床位 id → 占床就诊 id；无活动占床则不包含该键
     * @throws BusinessException 同一床位存在多条活动占床时抛出
     */
    public Map<Long, Long> mapActiveBedEncounterIdByLocationIds(Collection<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinctBedIds = new LinkedHashSet<>();
        for (Long bedId : locationIds) {
            if (bedId != null) {
                distinctBedIds.add(bedId);
            }
        }
        if (distinctBedIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EncounterLocation> rows = locationRepository.findAllByLocationIdInAndFormEnumAndStatusEnum(
            distinctBedIds,
            LocationForm.BED.getCode(),
            EncounterActivityStatus.ACTIVE.getCode());
        Map<Long, Long> bedIdToEncounterId = new HashMap<>(rows.size() * 2);
        for (EncounterLocation row : rows) {
            if (row == null || row.getLocationId() == null || row.getEncounterId() == null) {
                continue;
            }
            if (bedIdToEncounterId.containsKey(row.getLocationId())) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                    "同一床位存在多条活动占床记录");
            }
            bedIdToEncounterId.put(row.getLocationId(), row.getEncounterId());
        }
        return bedIdToEncounterId;
    }

    /**
     * 解析就诊当前唯一活动位置 Location 主键（DM-ADM-002 §6.2.13；AS-IP-NUR-002 ATD-02 Pre）。
     *
     * <p>在全部 {@code status_enum=ACTIVE} 轨迹中按 {@code form_enum} 筛选；调用方传入错误码与文案
     * （与 {@link #mustGetEncounterOfClass} 一致，应用层与写侧可各用各码）。</p>
     *
     * @param encounterId 就诊主键
     * @param formEnum 位置形态 code（如 {@link LocationForm#BED}、{@link LocationForm#HOUSE}）
     * @param notFoundErrorCode 无匹配活动行时的业务错误码
     * @param notFoundMessage 无匹配活动行时的提示
     * @param duplicateErrorCode 多条匹配活动行时的业务错误码
     * @param duplicateMessage 多条匹配活动行时的提示
     * @return 活动位置 locationId
     * @throws BusinessException 无匹配或重复时抛出
     */
    public Long requireActiveLocationId(
        Long encounterId,
        String formEnum,
        String notFoundErrorCode,
        String notFoundMessage,
        String duplicateErrorCode,
        String duplicateMessage) {
        List<EncounterLocation> activeLocations = locationRepository.findAllByEncounterIdAndStatusEnum(
            encounterId, EncounterActivityStatus.ACTIVE.getCode());
        Long matchedLocationId = null;
        for (EncounterLocation location : activeLocations) {
            if (location == null || !formEnum.equals(location.getFormEnum())) {
                continue;
            }
            if (matchedLocationId != null) {
                throw new BusinessException(duplicateErrorCode, duplicateMessage);
            }
            matchedLocationId = location.getLocationId();
        }
        if (matchedLocationId == null) {
            throw new BusinessException(notFoundErrorCode, notFoundMessage);
        }
        return matchedLocationId;
    }

    /**
     * 按主键与就诊类别加载聚合根；不存在或类别不符时区分抛出业务异常。
     *
     * @param encounterId 就诊标识
     * @param classEnum 期望类别编码
     * @param wrongClassErrorCode 类别不符错误码
     * @param wrongClassMessage 类别不符提示
     * @return 非空就诊实体
     * @throws BusinessException 记录不存在或类别不符
     */
    private Encounter mustGetEncounterEntityOfClass(
            Long encounterId,
            String classEnum,
            String wrongClassErrorCode,
            String wrongClassMessage) {
        Optional<Encounter> matched = encounterRepository.findByIdAndClassEnum(encounterId, classEnum);
        if (matched.isPresent()) {
            return matched.get();
        }
        // 区分「无此就诊」与「有就诊但类别不对」，避免住院登记误操作门诊线。
        Optional<Encounter> anyRow = encounterRepository.findById(encounterId);
        if (anyRow.isPresent()) {
            throw new BusinessException(wrongClassErrorCode, wrongClassMessage);
        }
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在");
    }
}
