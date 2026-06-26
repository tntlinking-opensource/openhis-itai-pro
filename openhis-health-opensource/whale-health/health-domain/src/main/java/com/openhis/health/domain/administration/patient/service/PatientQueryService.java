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

package com.openhis.health.domain.administration.patient.service;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.entity.PatientIdentifier;
import com.openhis.health.domain.administration.patient.entity.PatientStudent;
import com.openhis.health.domain.administration.patient.queries.DuplicatePatientCheckQuery;
import com.openhis.health.domain.administration.patient.queries.PatientPagedQuery;
import com.openhis.health.domain.administration.patient.queries.model.*;
import com.openhis.health.domain.administration.patient.repository.PatientIdentifierRepository;
import com.openhis.health.domain.administration.patient.repository.PatientRepository;
import com.openhis.health.domain.administration.patient.repository.PatientStudentRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.utils.AgeCalculatorUtil;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.share.enums.PatientStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 患者读侧查询服务。
 *
 * <p>职责：患者主档与标识/学籍子资源的只读加载、分页检索、公费资格与建档同人校验等。</p>
 *
 * <p>边界：只读；强依赖场景通过 {@link #requirePatient(Long)} 抛 {@link BusinessException}；
 * 同人校验优先身份证精确路径以控制候选集规模（通用规范 B.2）。</p>
 */
@Service
@RequiredArgsConstructor
public class PatientQueryService {
    private final PatientRepository patientRepository;

    private final PatientIdentifierRepository patientIdentifierRepository;

    private final PatientStudentRepository patientStudentRepository;

    /**
     * 就诊列表关键字：解析匹配患者主键（姓名/院内编码/身份证/拼音/五笔），供应用层收窄 {@code adm_encounter.patient_id}。
     *
     * @param keyword 用户输入关键字；空白时返回空列表
     * @return 未删除且命中任一文本字段的患者主键列表
     */
    public List<Long> listPatientIdsByEncounterListKeyword(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return Collections.emptyList();
        }
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(keyword.trim());
        Specification<Patient> specification = (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.and(
            criteriaBuilder.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get(FieldNameConstants.NAME)), likeIc),
                criteriaBuilder.like(criteriaBuilder.lower(root.get(FieldNameConstants.BUS_NO)), likeIc),
                criteriaBuilder.like(criteriaBuilder.lower(root.get(FieldNameConstants.ID_CARD)), likeIc),
                criteriaBuilder.like(criteriaBuilder.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                criteriaBuilder.like(criteriaBuilder.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
            ));
        List<Patient> rows = this.patientRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (Patient row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 按姓名关键字模糊匹配患者主键（adapter 票据检索读路径）。
     */
    public List<Long> listPatientIdsByNameKeyword(String keyword, int limit) {
        if (StringUtils.isBlank(keyword)) {
            return Collections.emptyList();
        }
        int pageSize = limit <= 0 ? 50 : limit;
        Specification<Patient> specification = (root, query, cb) -> cb.and(
            cb.like(root.get(FieldNameConstants.NAME), "%" + keyword.trim() + "%"),
            cb.or(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)), cb.isNull(root.get(FieldNameConstants.IS_DELETED))));
        Page<Patient> page = patientRepository.findAll(specification, PageRequest.of(0, pageSize));
        List<Long> ids = new ArrayList<>();
        for (Patient row : page.getContent()) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 分页检索患者。
     *
     * @param query 查询参数
     * @return 分页结果
     */
    public Page<PatientPageItemQueryModel> searchPatientsPaged(PatientPagedQuery query) {
        int pageNo = 0;
        int pageSize = 20;
        if (query.getPageNo() != null && query.getPageNo() > 0) {
            pageNo = query.getPageNo() - 1;
        }
        if (query.getPageSize() != null && query.getPageSize() > 0) {
            pageSize = query.getPageSize();
        }
        Sort sort = Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Specification<Patient> specification = (root, criteriaQuery, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            // 关键字模糊：与门诊选患者、B.10.11 一致，优先 keyword，兼容历史 name 入参；OR 命中姓名/病历号/证件/拼音/五笔。
            String searchKey = null;
            if (StringUtils.isNotBlank(query.getKeyword())) {
                searchKey = query.getKeyword().trim();
            } else if (StringUtils.isNotBlank(query.getName())) {
                searchKey = query.getName().trim();
            }
            if (StringUtils.isNotBlank(searchKey)) {
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(searchKey);
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get(FieldNameConstants.NAME)), likeIc),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get(FieldNameConstants.BUS_NO)), likeIc),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get(FieldNameConstants.ID_CARD)), likeIc),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
                ));
            }
            if (StringUtils.isNotBlank(query.getIdCard())) {
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getIdCard().trim());
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(FieldNameConstants.ID_CARD)), likeIc));
            }
            if (StringUtils.isNotBlank(query.getBusNo())) {
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getBusNo().trim());
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
            }
            if (query.getGenderEnum() != null) {
                predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.GENDER_ENUM), query.getGenderEnum()));
            }
            if (query.getStatusEnum() != null) {
                predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
            }
            if (query.getActiveFlag() != null) {
                predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.ACTIVE_FLAG), query.getActiveFlag()));
            }
            if (query.getTempFlag() != null) {
                predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.TEMP_FLAG), query.getTempFlag()));
            }
            if (query.getOrganizationId() != null) {
                predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()));
            }
            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<Patient> patientPage = patientRepository.findAll(specification, pageable);
        // Loop：遍历结果集，逐条装配展示字段
        List<PatientPageItemQueryModel> models = new ArrayList<>();
        for (Patient patient : patientPage.getContent()) {
            models.add(this.toPageItemModel(patient));
        }
        return new PageImpl<>(models, pageable, patientPage.getTotalElements());
    }

    /**
     * 加载患者详情。
     *
     * @param patientId 患者标识
     * @return 详情模型
     */
    public PatientDetailQueryModel loadPatientDetail(Long patientId) {
        Patient patient = this.requirePatient(patientId);
        PatientDetailQueryModel model = new PatientDetailQueryModel();
        model.setPatient(this.toCoreModel(patient));
        model.setIdentifiers(this.toIdentifierModels(patientIdentifierRepository.findAllByPatientId(patientId)));
        model.setStudents(this.toStudentModels(patientStudentRepository.findAllByPatientId(patientId)));
        return model;
    }

    /**
     * 加载学籍合并视图。
     *
     * @param patientId 患者标识
     * @return 视图模型
     */
    public PatientStudentProfileQueryModel loadPatientStudentProfile(Long patientId) {
        Patient patient = this.requirePatient(patientId);
        PatientStudentProfileQueryModel model = new PatientStudentProfileQueryModel();
        model.setPatientId(patient.getId());
        model.setName(patient.getName());
        model.setIdCard(patient.getIdCard());
        model.setPhone(patient.getPhone());
        model.setStudentRecords(this.toStudentModels(patientStudentRepository.findAllByPatientId(patientId)));
        return model;
    }

    /**
     * 公费学生资格校验。
     *
     * @param patientId 患者标识
     * @param studentId 学生记录标识
     * @return 校验结果
     */
    /**
     * 公费学生资格校验。
     *
     * @param patientId 患者标识
     * @param studentId 学生记录标识
     * @return 校验结果
     */
    public StudentPublicExpenseEligibilityQueryModel checkStudentPublicExpenseEligibility(Long patientId, Long studentId) {
        Patient patient = this.requirePatient(patientId);
        PatientStudent student = patientStudentRepository.findByIdAndPatientId(studentId, patientId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PATIENT_NOT_FOUND, "学籍记录不存在"));
        StudentPublicExpenseEligibilityQueryModel model = new StudentPublicExpenseEligibilityQueryModel();
        model.setPatientId(patientId);
        model.setStudentId(studentId);
        if (StringUtils.isBlank(patient.getIdCard()) || StringUtils.isBlank(student.getIdCard())) {
            model.setEligible(Boolean.FALSE);
            model.setReasonCode("PATIENT_STUDENT_NO_ID_CARD");
            model.setReasonMessage("缺少身份证信息");
            return model;
        }
        if (!patient.getIdCard().equals(student.getIdCard())) {
            model.setEligible(Boolean.FALSE);
            model.setReasonCode("PATIENT_STUDENT_ID_CARD_MISMATCH");
            model.setReasonMessage("学籍证件号与主档不一致");
            return model;
        }
        if (student.getLeaveTime() != null && student.getLeaveTime().isBefore(LocalDateTime.now())) {
            model.setEligible(Boolean.FALSE);
            model.setReasonCode("PATIENT_STUDENT_LEFT_SCHOOL");
            model.setReasonMessage("学生已离校");
            return model;
        }
        if (!GeneralSharedConstants.STUDENT_PHYSICAL_EXAM_PASS.equals(student.getPhysicalExamResult())) {
            model.setEligible(Boolean.FALSE);
            model.setReasonCode("PATIENT_STUDENT_PHYSICAL_EXAM_FAILED");
            model.setReasonMessage("体检结果不合格");
            return model;
        }
        model.setEligible(Boolean.TRUE);
        model.setReasonCode("OK");
        model.setReasonMessage("通过");
        return model;
    }

    /**
     * 建档同一人校验。
     *
     * @param query 查询参数
     * @return 校验结果
     */
    /**
     * 建档同一人校验。
     *
     * @param query 查询参数
     * @return 校验结果
     */
    public DuplicatePatientCheckQueryModel checkRegistrationDuplicatePatient(DuplicatePatientCheckQuery query) {
        if (StringUtils.isBlank(query.getName()) || query.getGenderEnum() == null) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "姓名和性别为必填");
        }
        List<Patient> candidates;
        if (StringUtils.isNotBlank(query.getIdCard())) {
            candidates = patientRepository.findAllByIdCardOrderByCreatedAtDesc(query.getIdCard());
            List<Patient> filtered = new ArrayList<>();
            // Loop：身份证命中后仍须姓名性别一致，排除证件录入错误导致的假阳性
            for (Patient candidate : candidates) {
                if (Objects.equals(candidate.getGenderEnum(), query.getGenderEnum())
                    && Objects.equals(candidate.getName(), query.getName())) {
                    filtered.add(candidate);
                }
            }
            candidates = filtered;
        } else if (query.getBirthDate() != null) {
            candidates = patientRepository.findAllByNameAndGenderEnumAndBirthDate(query.getName(),
                query.getGenderEnum(), query.getBirthDate());
        } else {
            candidates = patientRepository.findAllByNameAndGenderEnum(query.getName(), query.getGenderEnum());
        }
        // Loop：遍历候选集，逐条校验匹配规则
        List<PatientSimpleQueryModel> matched = new ArrayList<>();
        List<String> hits = new ArrayList<>();
        for (Patient candidate : candidates) {
            boolean matchedBirth = this.matchBirthDate(query.getBirthDate(), candidate.getBirthDate());
            boolean matchedIdCard = this.matchIdCard(query.getIdCard(), candidate.getIdCard());
            boolean matchedAge = this.matchAge(query.getAge(), candidate.getBirthDate());
            if (matchedBirth && matchedIdCard && matchedAge) {
                matched.add(this.toSimpleModel(candidate));
            }
        }
        if (!matched.isEmpty()) {
            hits.add("BR-PAT-014");
        }
        DuplicatePatientCheckQueryModel model = new DuplicatePatientCheckQueryModel();
        model.setDuplicated(!matched.isEmpty());
        model.setMatchedPatients(matched);
        model.setRuleHits(hits);
        return model;
    }

    /**
     * 根据身份证号查询患者。
     *
     * @param idCard 身份证号
     * @return 患者简要信息
     */
    /**
     * 根据身份证号查询患者。
     *
     * @param idCard 身份证号
     * @return 患者简要信息
     */
    public PatientSimpleQueryModel findPatientByIdCard(String idCard) {
        if (StringUtils.isBlank(idCard)) {
            return null;
        }
        List<String> terminalStatus = Arrays.asList(PatientStatus.MERGED.getCode(), PatientStatus.CANCELLED.getCode());
        List<Patient> patients = patientRepository.findAllByIdCardAndStatusEnumNotInOrderByCreatedAtDesc(
            idCard, terminalStatus);
        if (patients.isEmpty()) {
            return null;
        }
        if (patients.size() > 1) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_ID_CARD_CONFLICT, "身份证号存在重复患者数据");
        }
        return this.toSimpleModel(patients.get(0));
    }

    /**
     * 批量加载患者详情。
     *
     * @param patientIds 患者标识集合
     * @return 患者详情映射
     */
    /**
     * 批量加载患者详情。
     *
     * @param patientIds 患者标识集合
     * @return 患者详情映射
     */
    public Map<Long, BatchPatientDetailQueryModel> batchLoadPatientDetails(List<Long> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        if (patientIds.size() > GeneralSharedConstants.BATCH_LIMIT) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "批量加载数量超过上限");
        }
        List<Patient> patients = patientRepository.findAllByIdIn(patientIds);
        List<PatientIdentifier> identifiers = patientIdentifierRepository.findAllByPatientIdIn(patientIds);
        List<PatientStudent> students = patientStudentRepository.findAllByPatientIdIn(patientIds);
        Map<Long, List<PatientIdentifier>> identifierMap = new HashMap<>();
        for (PatientIdentifier identifier : identifiers) {
            List<PatientIdentifier> list = identifierMap.computeIfAbsent(identifier.getPatientId(), key -> new ArrayList<>());
            list.add(identifier);
        }
        Map<Long, List<PatientStudent>> studentMap = new HashMap<>();
        for (PatientStudent student : students) {
            List<PatientStudent> list = studentMap.computeIfAbsent(student.getPatientId(), key -> new ArrayList<>());
            list.add(student);
        }
        // Loop：遍历患者列表，组装批量详情模型
        Map<Long, BatchPatientDetailQueryModel> result = new HashMap<>();
        for (Patient patient : patients) {
            PatientDetailQueryModel detail = new PatientDetailQueryModel();
            detail.setPatient(this.toCoreModel(patient));
            detail.setIdentifiers(this.toIdentifierModels(identifierMap.get(patient.getId())));
            detail.setStudents(this.toStudentModels(studentMap.get(patient.getId())));
            BatchPatientDetailQueryModel item = new BatchPatientDetailQueryModel();
            item.setPatientId(patient.getId());
            item.setPatientDetail(detail);
            result.put(patient.getId(), item);
        }
        return result;
    }

    /**
     * 按主键批量解析患者姓名（工作台列表等轻量展示）。
     *
     * @param patientIds 患者标识集合
     * @return 患者主键 → 姓名字符串（无记录则无条目）
     */
    public Map<Long, String> findPatientNamesByIds(Collection<Long> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> idSet = new LinkedHashSet<>();
        for (Long id : patientIds) {
            if (id != null) {
                idSet.add(id);
            }
        }
        if (idSet.isEmpty()) {
            return Collections.emptyMap();
        }
        if (idSet.size() > GeneralSharedConstants.BATCH_LIMIT) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "批量加载数量超过上限");
        }
        List<Patient> patients = patientRepository.findAllByIdIn(idSet);
        Map<Long, String> out = new HashMap<>(patients.size());
        for (Patient p : patients) {
            if (p == null || p.getId() == null || Boolean.TRUE.equals(p.getIsDeleted())) {
                continue;
            }
            if (StringUtils.isNotBlank(p.getName())) {
                out.put(p.getId(), p.getName());
            }
        }
        return out;
    }

    /**
     * 按主键批量加载患者实体（querysupport 上下文组装；不含 identifier/student 子表）。
     *
     * @param patientIds 患者主键集合
     * @return 患者 ID → 实体；未命中或已软删则无条目
     */
    public Map<Long, Patient> loadPatientEntityMapByIds(Collection<Long> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> idSet = new LinkedHashSet<>();
        for (Long id : patientIds) {
            if (id != null) {
                idSet.add(id);
            }
        }
        if (idSet.isEmpty()) {
            return Collections.emptyMap();
        }
        if (idSet.size() > GeneralSharedConstants.BATCH_LIMIT) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "批量加载数量超过上限");
        }
        List<Patient> patients = patientRepository.findAllByIdIn(idSet);
        Map<Long, Patient> out = new HashMap<>(patients.size());
        for (Patient patient : patients) {
            if (patient != null && patient.getId() != null && !Boolean.TRUE.equals(patient.getIsDeleted())) {
                out.put(patient.getId(), patient);
            }
        }
        return out;
    }

    /**
     * 批量加载住院信息读模型投影所需的患者人口学字段（DM-ADM-001 §6.2.9）。
     *
     * @param patientIds 患者主键集合；{@code null} 或空时返回空映射
     * @return 患者 ID → 人口学快照
     */
    public Map<Long, PatientInpatientProjectionSnapshot> mapInpatientProjectionSnapshotsByIds(
        Collection<Long> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> idSet = new LinkedHashSet<>();
        for (Long id : patientIds) {
            if (id != null) {
                idSet.add(id);
            }
        }
        if (idSet.isEmpty()) {
            return Collections.emptyMap();
        }
        if (idSet.size() > GeneralSharedConstants.BATCH_LIMIT) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "批量加载数量超过上限");
        }
        List<Patient> patients = patientRepository.findAllByIdIn(idSet);
        Map<Long, PatientInpatientProjectionSnapshot> out = new HashMap<>(patients.size());
        for (Patient patient : patients) {
            if (patient.getId() == null || Boolean.TRUE.equals(patient.getIsDeleted())) {
                continue;
            }
            PatientInpatientProjectionSnapshot snapshot = new PatientInpatientProjectionSnapshot();
            snapshot.setPatientName(StringUtils.defaultString(patient.getName()));
            snapshot.setGenderEnum(patient.getGenderEnum());
            LocalDate birthLocal = patient.getBirthDate();
            if (birthLocal != null) {
                snapshot.setBirthDate(birthLocal.atStartOfDay());
                Date birthDate = Date.from(birthLocal.atStartOfDay(ZoneId.systemDefault()).toInstant());
                String ageText = AgeCalculatorUtil.getAge(birthDate);
                if (StringUtils.isNotBlank(ageText)) {
                    snapshot.setAgeText(ageText);
                }
            }
            out.put(patient.getId(), snapshot);
        }
        return out;
    }

    /**
     * 按主键加载患者；不存在或已不可见时抛出业务异常，供详情类读接口复用。
     *
     * @param patientId 患者标识
     * @return 非空患者实体
     * @throws BusinessException 记录不存在时
     */
    private Patient requirePatient(Long patientId) {
        return patientRepository.findById(patientId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PATIENT_NOT_FOUND, "患者不存在或已删除"));
    }

    /**
     * 按主键加载患者实体（Port/bridge 读路径）；不存在或已软删时 empty。
     *
     * @param patientId 患者标识
     * @return 患者实体
     */
    public Optional<Patient> findOptionalPatientEntityById(Long patientId) {
        if (patientId == null) {
            return Optional.empty();
        }
        return patientRepository.findById(patientId)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()));
    }

    /**
     * 转换分页行模型。
     *
     * @param patient 患者实体
     * @return 分页模型
     */
    private PatientPageItemQueryModel toPageItemModel(Patient patient) {
        PatientPageItemQueryModel model = new PatientPageItemQueryModel();
        model.setId(patient.getId());
        model.setBusNo(patient.getBusNo());
        model.setName(patient.getName());
        model.setGenderEnum(patient.getGenderEnum());
        model.setBirthDate(patient.getBirthDate());
        model.setIdCard(patient.getIdCard());
        model.setPhone(patient.getPhone());
        model.setStatusEnum(patient.getStatusEnum());
        model.setActiveFlag(patient.getActiveFlag());
        model.setTempFlag(patient.getTempFlag());
        model.setOrganizationId(patient.getOrganizationId());
        return model;
    }

    /**
     * 转换核心模型。
     *
     * @param patient 患者实体
     * @return 核心模型
     */
    private PatientCoreQueryModel toCoreModel(Patient patient) {
        PatientCoreQueryModel model = new PatientCoreQueryModel();
        model.setId(patient.getId());
        model.setBusNo(patient.getBusNo());
        model.setName(patient.getName());
        model.setNameJson(patient.getNameJson());
        model.setGenderEnum(patient.getGenderEnum());
        model.setBirthDate(patient.getBirthDate());
        model.setIdCard(patient.getIdCard());
        model.setMaritalStatusEnum(patient.getMaritalStatusEnum());
        model.setPrfsEnum(patient.getPrfsEnum());
        model.setPhone(patient.getPhone());
        if (patient.getAddress() == null) {
            model.setAddress(null);
            model.setAddressProvince(null);
            model.setAddressCity(null);
            model.setAddressDistrict(null);
            model.setAddressStreet(null);
        } else {
            model.setAddress(patient.getAddress().getText());
            model.setAddressProvince(patient.getAddress().getProvince());
            model.setAddressCity(patient.getAddress().getCity());
            model.setAddressDistrict(patient.getAddress().getDistrict());
            model.setAddressStreet(patient.getAddress().getStreet());
        }
        model.setAddressExtendJson(patient.getAddressExtendJson());
        model.setNationalityCode(patient.getNationalityCode());
        model.setPyStr(patient.getPyStr());
        model.setWbStr(patient.getWbStr());
        if (patient.getBloodType() == null) {
            model.setBloodTypeAbo(null);
            model.setBloodTypeRh(null);
        } else {
            model.setBloodTypeAbo(patient.getBloodType().getAbo());
            model.setBloodTypeRh(patient.getBloodType().getRh());
        }
        model.setWorkCompany(patient.getWorkCompany());
        model.setNativePlace(patient.getNativePlace());
        model.setCountryCode(patient.getCountryCode());
        if (patient.getLinkName() == null) {
            model.setLinkName(null);
            model.setLinkRelationCode(null);
            model.setLinkTelcom(null);
        } else {
            model.setLinkName(patient.getLinkName().getName());
            model.setLinkRelationCode(patient.getLinkName().getRelationCode());
            model.setLinkTelcom(patient.getLinkName().getTelcom());
        }
        model.setLinkJsons(patient.getLinkJsons());
        model.setStatusEnum(patient.getStatusEnum());
        model.setActiveFlag(patient.getActiveFlag());
        model.setTempFlag(patient.getTempFlag());
        model.setOrganizationId(patient.getOrganizationId());
        model.setDeceasedDate(patient.getDeceasedDate());
        model.setDeceasedReason(patient.getDeceasedReason());
        model.setMergedTargetPatientId(patient.getMergedTargetPatientId());
        model.setMergedAt(patient.getMergedAt());
        return model;
    }

    /**
     * 转换患者简要模型。
     *
     * @param patient 患者实体
     * @return 简要模型
     */
    private PatientSimpleQueryModel toSimpleModel(Patient patient) {
        PatientSimpleQueryModel model = new PatientSimpleQueryModel();
        model.setId(patient.getId());
        model.setBusNo(patient.getBusNo());
        model.setName(patient.getName());
        model.setGenderEnum(patient.getGenderEnum());
        model.setBirthDate(patient.getBirthDate());
        model.setIdCard(patient.getIdCard());
        return model;
    }

    /**
     * 批量转换标识模型。
     *
     * @param identifiers 标识实体集合
     * @return 标识模型集合
     */
    /**
     * 批量转换标识模型。
     *
     * @param identifiers 标识实体集合
     * @return 标识模型集合
     */
    private List<PatientIdentifierQueryModel> toIdentifierModels(List<PatientIdentifier> identifiers) {
        List<PatientIdentifierQueryModel> models = new ArrayList<>();
        if (identifiers == null) {
            return models;
        }
        // Loop：遍历标识实体列表，逐条转换
        for (PatientIdentifier identifier : identifiers) {
            PatientIdentifierQueryModel model = new PatientIdentifierQueryModel();
            model.setId(identifier.getId());
            model.setTypeCode(identifier.getTypeCode());
            model.setIdentifierNo(identifier.getIdentifierNo());
            model.setStateEnum(identifier.getStateEnum());
            model.setStartTime(identifier.getStartTime());
            model.setEndTime(identifier.getEndTime());
            models.add(model);
        }
        return models;
    }

    /**
     * 批量转换学生模型。
     *
     * @param students 学生实体集合
     * @return 学生模型集合
     */
    private List<PatientStudentQueryModel> toStudentModels(List<PatientStudent> students) {
        List<PatientStudentQueryModel> models = new ArrayList<>();
        if (students == null) {
            return models;
        }
        for (PatientStudent student : students) {
            PatientStudentQueryModel model = new PatientStudentQueryModel();
            model.setId(student.getId());
            model.setName(student.getName());
            model.setStudentNo(student.getStudentNo());
            model.setIdCard(student.getIdCard());
            model.setPhone(student.getPhone());
            model.setCollege(student.getCollege());
            model.setMajor(student.getMajor());
            model.setEduLevel(student.getEduLevel());
            model.setEnrollTime(student.getEnrollTime());
            model.setLeaveTime(student.getLeaveTime());
            model.setGrade(student.getGrade());
            model.setStudyType(student.getStudyType());
            model.setSchoolStatus(student.getSchoolStatus());
            model.setPhysicalExamResult(student.getPhysicalExamResult());
            model.setCounselor(student.getCounselor());
            model.setCounselorPhone(student.getCounselorPhone());
            models.add(model);
        }
        return models;
    }

    /**
     * 校验出生日期是否匹配。
     *
     * @param queryBirthDate 入参出生日期
     * @param actualBirthDate 实际出生日期
     * @return 匹配返回 true
     */
    private boolean matchBirthDate(LocalDate queryBirthDate, LocalDate actualBirthDate) {
        // 调用方未传出生日时不在此维度拦截。
        if (queryBirthDate == null) {
            return true;
        }
        if (actualBirthDate == null) {
            return false;
        }
        return queryBirthDate.equals(actualBirthDate);
    }

    /**
     * 校验身份证是否匹配。
     *
     * @param queryIdCard 入参身份证
     * @param actualIdCard 实际身份证
     * @return 匹配返回 true
     */
    private boolean matchIdCard(String queryIdCard, String actualIdCard) {
        if (StringUtils.isBlank(queryIdCard)) {
            return true;
        }
        if (StringUtils.isBlank(actualIdCard)) {
            return false;
        }
        return queryIdCard.equals(actualIdCard);
    }

    /**
     * 校验年龄是否匹配。
     *
     * @param queryAge 入参年龄
     * @param actualBirthDate 实际出生日期
     * @return 匹配返回 true
     */
    private boolean matchAge(Integer queryAge, LocalDate actualBirthDate) {
        if (queryAge == null) {
            return true;
        }
        if (actualBirthDate == null) {
            return false;
        }
        int age = LocalDate.now().getYear() - actualBirthDate.getYear();
        int diff = Math.abs(age - queryAge);
        return diff <= 1;
    }
}
