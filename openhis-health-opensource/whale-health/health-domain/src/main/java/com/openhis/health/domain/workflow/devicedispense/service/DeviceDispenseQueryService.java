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

package com.openhis.health.domain.workflow.devicedispense.service;

import com.openhis.health.domain.share.constants.DoctorStationOrderCatalogConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.workflow.devicedispense.entity.DeviceDispense;
import com.openhis.health.domain.workflow.devicedispense.queries.DeviceDispensePageQuery;
import com.openhis.health.domain.workflow.devicedispense.queries.EncounterInfoPageQuery;
import com.openhis.health.domain.workflow.devicedispense.queries.model.*;
import com.openhis.health.domain.workflow.devicedispense.repository.DeviceDispenseRepository;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 高值/耗材发放读侧查询服务。
 *
 * <p>职责：发放单详情与列表、状态校验门控及工作台筛选；只读不写库。</p>
 *
 * <p>与 {@code AS-OP-DOR-003} IF-DOR-11 对齐：提供同就诊候选库位上的耗材发放预占行批量加载（状态列为字符串码）。</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceDispenseQueryService {

    private final DeviceDispenseRepository deviceDispenseRepository;


    /**
     * 按主键加载发放单读模型。
     *
     * @param id 发放单主键
     * @return 详情模型
     * @throws BusinessException 发放单不存在
     */
    public DeviceDispenseModel loadDeviceDispenseById(Long id) {
        return this.toDetailModel(this.mustGetDeviceDispenseEntity(id));
    }

    /**
     * 按耗材医嘱申请列出关联发放单主键（签退清理费用锚点用）。
     *
     * @param deviceReqId 医嘱申请主键
     * @return 发放单 id 列表；{@code deviceReqId} 为空时返回空表
     */
    public List<Long> listDeviceDispenseIdsByDeviceRequest(Long deviceReqId) {
        if (deviceReqId == null) {
            return new ArrayList<>();
        }
        Map<Long, List<Long>> idsByRequest = this.mapDeviceDispenseIdsByDeviceRequestIds(List.of(deviceReqId));
        List<Long> dispenseIds = idsByRequest.get(deviceReqId);
        if (dispenseIds == null) {
            return new ArrayList<>();
        }
        return dispenseIds;
    }

    /**
     * 按耗材医嘱申请主键集合批量列出关联发放单主键（护士站 IF-NUR-02 等，一次 IN 查询，对齐 A.9.1）。
     *
     * @param deviceReqIds 医嘱申请主键集合；{@code null} 或空时返回空映射
     * @return 医嘱申请 id → 发放单 id 列表（顺序不保证，取主发放行时由调用方 {@code Collections.min}）
     */
    public Map<Long, List<Long>> mapDeviceDispenseIdsByDeviceRequestIds(Collection<Long> deviceReqIds) {
        Map<Long, List<Long>> dispenseIdsByDeviceRequest = new HashMap<>();
        if (deviceReqIds == null || deviceReqIds.isEmpty()) {
            return dispenseIdsByDeviceRequest;
        }
        List<Long> distinctIds = new ArrayList<>();
        for (Long deviceReqId : deviceReqIds) {
            if (deviceReqId != null) {
                distinctIds.add(deviceReqId);
            }
        }
        if (distinctIds.isEmpty()) {
            return dispenseIdsByDeviceRequest;
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByDeviceReqIdIn(distinctIds);
        for (DeviceDispense row : rows) {
            if (row.getDeviceReqId() == null || row.getId() == null) {
                continue;
            }
            dispenseIdsByDeviceRequest
                .computeIfAbsent(row.getDeviceReqId(), ignored -> new ArrayList<>())
                .add(row.getId());
        }
        return dispenseIdsByDeviceRequest;
    }

    /**
     * 医生站医嘱列表回显：对每个耗材医嘱取「发放主键升序第一条」的库位。
     *
     * @param deviceReqIds 医嘱申请主键集合
     * @return 医嘱 id → 首选发放库位 id
     */
    public Map<Long, Long> mapDeviceRequestToPrimaryDispenseLocationId(Collection<Long> deviceReqIds) {
        Map<Long, Long> locationByReq = new HashMap<>();
        Map<Long, Long> minDispenseIdByReq = new HashMap<>();
        if (deviceReqIds == null || deviceReqIds.isEmpty()) {
            return locationByReq;
        }
        List<Long> distinctIds = new ArrayList<>();
        // Loop：过滤空值构建有效ID列表
        for (Long id : deviceReqIds) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return locationByReq;
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByDeviceReqIdIn(distinctIds);
        // Loop：遍历发放记录，按发放ID升序取首条库位作为首选库位
        for (DeviceDispense row : rows) {
            if (row.getDeviceReqId() == null || row.getLocationId() == null) {
                continue;
            }
            Long prevMin = minDispenseIdByReq.get(row.getDeviceReqId());
            if (prevMin == null || row.getId() < prevMin) {
                minDispenseIdByReq.put(row.getDeviceReqId(), row.getId());
                locationByReq.put(row.getDeviceReqId(), row.getLocationId());
            }
        }
        return locationByReq;
    }

    /**
     * 按就诊列出全部耗材发放单摘要。
     *
     * @param encounterId 就诊标识
     * @return 列表模型
     */
    public List<DeviceDispenseListModel> listDeviceDispensesByEncounter(Long encounterId) {
        List<DeviceDispense> rows = deviceDispenseRepository.findByEncounterId(encounterId);
        List<DeviceDispenseListModel> result = new ArrayList<>();
        // Loop：遍历就诊下全部发放记录，逐条转换为列表模型
        for (DeviceDispense row : rows) {
            result.add(this.toListModel(row));
        }
        return result;
    }

    /**
     * 医嘱开立目录：同就诊、候选库位与目录耗材定义交集下，处于草稿/待发链上的发放数量行（IF-DOR-11 预减口径，与药品侧同构）。
     *
     * @param encounterId            就诊主键
     * @param candidateLocationIds   候选库房库位 id 列表
     * @param deviceDefinitionIds    当前页耗材定义 id 列表
     * @return 占用行；任一入参为空或集合空时返回空列表
     */
    public List<DeviceDispenseOrderCatalogOccupancyModel> listDeviceDispensesForOrderCatalogOccupancy(
        Long encounterId, List<Long> candidateLocationIds, List<Long> deviceDefinitionIds) {
        List<DeviceDispenseOrderCatalogOccupancyModel> result = new ArrayList<>();
        if (encounterId == null) {
            return result;
        }
        if (candidateLocationIds == null || candidateLocationIds.isEmpty()) {
            return result;
        }
        if (deviceDefinitionIds == null || deviceDefinitionIds.isEmpty()) {
            return result;
        }
        List<String> occupancyStatusCodes = new ArrayList<>();
        // Loop：遍历枚举集合，提取状态码构建IN条件
        for (DispenseStatus statusEnum : DoctorStationOrderCatalogConstants.DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG) {
            occupancyStatusCodes.add(statusEnum.getCode());
        }
        Specification<DeviceDispense> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), encounterId));
            predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(candidateLocationIds));
            predicates.add(root.get(FieldNameConstants.DEVICE_DEF_ID).in(deviceDefinitionIds));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(occupancyStatusCodes));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<DeviceDispense> rows = deviceDispenseRepository.findAll(specification);
        // Loop：遍历占用行，逐条装配为订单目录占用模型
        for (DeviceDispense row : rows) {
            DeviceDispenseOrderCatalogOccupancyModel model = new DeviceDispenseOrderCatalogOccupancyModel();
            model.setDeviceDefId(row.getDeviceDefId());
            model.setLocationId(row.getLocationId());
            model.setQuantity(row.getQuantity());
            model.setUnitCode(row.getUnitCode());
            model.setStatusEnum(row.getStatusEnum());
            result.add(model);
        }
        return result;
    }

    /**
     * 必取发放单读模型（不存在抛业务异常）。
     *
     * @param id 发放单主键
     * @return 详情模型
     */
    public DeviceDispenseModel mustGetDeviceDispense(Long id) {
        return this.toDetailModel(this.mustGetDeviceDispenseEntity(id));
    }

    /**
     * 门控：是否允许进入配药/备货准备阶段（委托聚合根断言）。
     *
     * @param id 发放单主键
     * @return 校验结果（allow + 原因码）
     */
    public DeviceDispenseValidationModel validateDeviceDispensePreparable(Long id) {
        return this.validate(() -> this.mustGetDeviceDispenseEntity(id).assertPreparable());
    }

    /**
     * 门控：是否允许实际发放出库。
     *
     * @param id 发放单主键
     * @return 校验结果
     */
    public DeviceDispenseValidationModel validateDeviceDispensable(Long id) {
        return this.validate(() -> this.mustGetDeviceDispenseEntity(id).assertDispensable());
    }

    /**
     * 门控：是否允许作废尚未发放完成的单据。
     *
     * @param id 发放单主键
     * @return 校验结果
     */
    public DeviceDispenseValidationModel validateDeviceDispenseCancellable(Long id) {
        return this.validate(() -> this.mustGetDeviceDispenseEntity(id).assertCancellableUndispensed());
    }

    /**
     * 门控：是否允许发起退库/退费关联（含幂等语义由聚合根承载）。
     *
     * @param id 发放单主键
     * @return 校验结果
     */
    public DeviceDispenseValidationModel validateDeviceDispenseRefundable(Long id) {
        return this.validate(() -> this.mustGetDeviceDispenseEntity(id).assertRefundableAndIdempotent());
    }

    /**
     * 按诊疗操作/手术过程标识查询关联发放单列表。
     *
     * @param procedureId 过程主键
     * @return 发放单摘要列表
     */
    public List<DeviceDispenseListModel> getDevDispenseByProcedureId(Long procedureId) {
        List<DeviceDispense> rows = deviceDispenseRepository.findByProcedureId(procedureId);
        List<DeviceDispenseListModel> result = new ArrayList<>();
        // Loop：遍历诊疗操作关联发放记录，逐条转换为列表模型
        for (DeviceDispense row : rows) {
            result.add(this.toListModel(row));
        }
        return result;
    }

    /**
     * 批量按耗材请求主键查询已生成的发放单（空入参避免无意义 IN 查询）。
     *
     * @param requestIdList 请求主键集合
     * @return 发放单摘要列表
     */
    public List<DeviceDispenseListModel> selectByRequestIdList(List<Long> requestIdList) {
        if (requestIdList == null || requestIdList.isEmpty()) {
            return new ArrayList<>();
        }
        List<DeviceDispense> rows = deviceDispenseRepository.findByDeviceReqIdIn(requestIdList);
        List<DeviceDispenseListModel> result = new ArrayList<>();
        // Loop：遍历关联发放记录，逐条转换为列表模型
        for (DeviceDispense row : rows) {
            result.add(this.toListModel(row));
        }
        return result;
    }

    /**
     * 工作台初始化（字典选项由上层注入，此处返回占位）。
     */
    public DeviceDispenseInitModel init(String context) {
        DeviceDispenseInitModel model = new DeviceDispenseInitModel();
        List<OptionDto> statusOptions = new ArrayList<>();
        long sort = 0L;
        // Loop：遍历发放状态枚举，构建前端下拉选项（排除UNKNOWN占位）
        for (DispenseStatus status : DispenseStatus.values()) {
            if (status == DispenseStatus.UNKNOWN) {
                continue;
            }
            sort++;
            statusOptions.add(new OptionDto(sort, String.valueOf(status.getCode()), status.name()));
        }
        model.setStatusOptions(statusOptions);
        return model;
    }

    /**
     * 就诊分页：按发放事实过滤；同一就诊多行发放时，分页行可能重复就诊维度。
     */
    public Page<EncounterInfoModel> getEncounterInfoListPage(EncounterInfoPageQuery query, Pageable pageable) {
        Specification<DeviceDispense> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null && query.getEncounterId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), query.getEncounterId()));
            }
            if (query != null && query.getPatientId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return deviceDispenseRepository.findAll(specification, pageable).map(row -> {
            EncounterInfoModel m = new EncounterInfoModel();
            m.setEncounterId(row.getEncounterId());
            m.setPatientId(row.getPatientId());
            return m;
        });
    }

    /**
     * 发放单分页列表：支持就诊、患者、状态与单号关键字组合过滤。
     *
     * @param query 筛选条件
     * @param pageable 分页
     * @return 分页列表模型
     */
    public Page<DeviceDispenseListModel> getDeviceDispenseOrderList(DeviceDispensePageQuery query, Pageable pageable) {
        Specification<DeviceDispense> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null) {
                if (query.getEncounterId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), query.getEncounterId()));
                }
                if (query.getPatientId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
                }
                if (query.getStatusEnum() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
                }
                if (StringUtils.isNotBlank(query.getBusNoKeyword())) {
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getBusNoKeyword().trim());
                    predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return deviceDispenseRepository.findAll(specification, pageable).map(this::toListModel);
    }

    /**
     * 加载发放聚合根供断言与映射复用。
     *
     * @param id 主键
     * @return 实体
     * @throws BusinessException 不存在
     */
    private DeviceDispense mustGetDeviceDispenseEntity(Long id) {
        return deviceDispenseRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_NOT_FOUND, "耗材发放不存在"));
    }

    /**
     * 按主键加载未软删耗材发放行（不存在或已软删时 {@code null}）。
     *
     * @param id 发放主键
     * @return 发放行或 {@code null}
     */
    public DeviceDispense findById(Long id) {
        if (id == null) {
            return null;
        }
        DeviceDispense row = deviceDispenseRepository.findById(id).orElse(null);
        if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
            return null;
        }
        return row;
    }

    /**
     * 详情字段映射。
     *
     * @param row 实体
     * @return 读模型
     */
    private DeviceDispenseModel toDetailModel(DeviceDispense row) {
        DeviceDispenseModel model = new DeviceDispenseModel();
        model.setId(row.getId());
        model.setStatusEnum(row.getStatusEnum());
        model.setQuantity(row.getQuantity());
        model.setBusNo(row.getBusNo());
        model.setEncounterId(row.getEncounterId());
        model.setDeviceReqId(row.getDeviceReqId());
        model.setDeviceDefId(row.getDeviceDefId());
        model.setLocationId(row.getLocationId());
        model.setLotNumber(row.getLotNumber());
        model.setDispenseQuantity(row.getDispenseQuantity());
        model.setTraceNo(row.getTraceNo());
        model.setUnitCode(row.getUnitCode());
        return model;
    }

    /**
     * 列表行映射。
     *
     * @param row 实体
     * @return 列表模型
     */
    private DeviceDispenseListModel toListModel(DeviceDispense row) {
        DeviceDispenseListModel model = new DeviceDispenseListModel();
        model.setId(row.getId());
        model.setEncounterId(row.getEncounterId());
        model.setStatusEnum(row.getStatusEnum());
        model.setBusNo(row.getBusNo());
        model.setDeviceReqId(row.getDeviceReqId());
        return model;
    }

    /**
     * 批量解析耗材发放单主键到耗材申请主键（医生站费用锚点归并，AS-OP-DOR-003 IF-DOR-10）。
     *
     * @param dispenseIds 发放单主键集合
     * @return 发放单 id → 耗材申请 id
     */
    public Map<Long, Long> mapDeviceDispenseIdsToDeviceReqIds(Collection<Long> dispenseIds) {
        Map<Long, Long> map = new HashMap<>();
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return map;
        }
        List<Long> idList = new ArrayList<>(dispenseIds);
        List<DeviceDispense> rows = deviceDispenseRepository.findByIdIn(idList);
        // Loop：遍历发放记录，构建发放ID到请求ID的映射
        for (DeviceDispense row : rows) {
            if (row.getDeviceReqId() != null) {
                map.put(row.getId(), row.getDeviceReqId());
            }
        }
        return map;
    }

    /**
     * 按耗材申请主键集合批量加载发放单（签发后迁移费用草稿用，AS-OP-DOR-003 IF-DOR-15）。
     *
     * @param deviceReqIds 耗材申请主键集合
     * @return 发放单实体列表
     */
    public List<DeviceDispense> listDeviceDispensesByDeviceReqIds(Collection<Long> deviceReqIds) {
        if (deviceReqIds == null || deviceReqIds.isEmpty()) {
            return List.of();
        }
        List<Long> idList = new ArrayList<>(deviceReqIds);
        return deviceDispenseRepository.findByDeviceReqIdIn(idList);
    }

    /**
     * 判断耗材是否存在非终态发放行（调价提交互斥 BR-PRC-SUB-003：在途发药）。
     *
     * @param deviceDefId 耗材定义主键
     * @return 存在在途发放时为 true
     */
    public boolean existsInTransitDispenseByDeviceDefId(Long deviceDefId) {
        if (deviceDefId == null) {
            return false;
        }
        // 非终态发放视为在途发药（与药品发放口径一致）
        Specification<DeviceDispense> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.DEVICE_DEF_ID), deviceDefId));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(this.inTransitDispenseStatusCodes()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return deviceDispenseRepository.count(specification) > 0;
    }

    /**
     * 在途发放状态码：草稿、待发、配药中、部分完成、退药中等非终态。
     *
     * @return 状态 code 列表
     */
    private List<String> inTransitDispenseStatusCodes() {
        List<String> codes = new ArrayList<>();
        codes.add(DispenseStatus.DRAFT.getCode());
        codes.add(DispenseStatus.PREPARATION.getCode());
        codes.add(DispenseStatus.PREPARED.getCode());
        codes.add(DispenseStatus.IN_PROGRESS.getCode());
        codes.add(DispenseStatus.SUMMARIZED.getCode());
        codes.add(DispenseStatus.PART_COMPLETED.getCode());
        codes.add(DispenseStatus.ON_HOLD.getCode());
        codes.add(DispenseStatus.PENDING_REFUND.getCode());
        codes.add(DispenseStatus.PART_REFUND.getCode());
        return codes;
    }

    /**
     * 将聚合根断言包装为「允许/拒绝 + 原因」供应用层统一展示。
     *
     * @param assertion 无参断言（内部抛 {@link BusinessException} 表示不允许）
     * @return 校验模型
     */
    /**
     * 将聚合根断言包装为「允许/拒绝 + 原因」供应用层统一展示。
     *
     * @param assertion 无参断言（内部抛 {@link BusinessException} 表示不允许）
     * @return 校验模型
     */
    private DeviceDispenseValidationModel validate(Runnable assertion) {
        DeviceDispenseValidationModel model = new DeviceDispenseValidationModel();
        try {
            assertion.run();
            model.setAllow(true);
            model.setReasonCode(null);
        } catch (BusinessException ex) {
            model.setAllow(false);
            model.setReasonCode(ex.getMessage());
        }
        return model;
    }
}
