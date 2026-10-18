/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.labdevice.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;

import com.zxplat.domain.administration.labdevice.command.ActivateLabDevicesCommand;
import com.zxplat.domain.administration.labdevice.command.CreateLabDeviceCommand;
import com.zxplat.domain.administration.labdevice.command.DeactivateLabDevicesCommand;
import com.zxplat.domain.administration.labdevice.command.DeleteLabDeviceCommand;
import com.zxplat.domain.administration.labdevice.command.UpdateLabDeviceCommand;
import com.zxplat.domain.administration.labdevice.entity.LabDevice;
import com.zxplat.domain.administration.labdevice.repository.LabDeviceRepository;
import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.utils.CommandBatchUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 检验设备写侧领域服务（DM-ADM-015）。
 *
 * <p>负责设备台账的新增、维护、逻辑删除与批量启停；业务编码由领域层按 LAD 前缀生成。</p>
 */
@Service
@RequiredArgsConstructor
public class LabDeviceDomainService {

    private final LabDeviceRepository repository;
    /** 检验设备 busNo：主数据全局递增，前缀 {@link AggregateTypeConstants#LAB_DEVICE}。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 新建检验设备，初始化为启用态。
     *
     * @param command 新建命令
     * @return 新建主键
     */
    public Long createLabDevice(CreateLabDeviceCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_INVALID_ARGUMENT, "新建检验设备命令不能为空");
        }
        LabDevice entity = new LabDevice();
        entity.setBusNo(businessSequenceService.getSeq(
            AggregateTypeConstants.LAB_DEVICE, BusinessSequenceConstants.MASTER_DATA_3_PREFIX_SEQ_LENGTH));
        entity.setName(trimToEmpty(command.getName()));
        applyOptionalFieldsOnCreate(entity, command);
        entity.initAsActive();
        entity.assertBusNoNotBlank();
        entity.assertNameNotBlank();
        entity.refreshSearchCodeFromName();
        this.assertBusNoNotConflict(entity);
        LabDevice saved = repository.save(entity);
        return saved.getId();
    }

    /**
     * 维护检验设备；名称变更时重算检索码。
     *
     * @param command 更新命令
     */
    public void updateLabDevice(UpdateLabDeviceCommand command) {
        if (command == null || command.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_INVALID_ARGUMENT, "更新检验设备时主键不能为空");
        }
        LabDevice entity = this.loadLabDevice(command.getId());
        List<String> changedFields = new ArrayList<>();
        String oldName = entity.getName();
        if (command.getName() != null) {
            String newName = command.getName().trim();
            if (!Objects.equals(entity.getName(), newName)) {
                entity.setName(newName);
                changedFields.add(FieldNameConstants.NAME);
            }
        }
        applyOptionalFieldsOnUpdate(entity, command, changedFields);
        if (!Objects.equals(oldName, entity.getName())) {
            entity.setPyStr(null);
            entity.setWbStr(null);
            if (command.getPyStr() != null) {
                entity.setPyStr(command.getPyStr().trim());
            }
            if (command.getWbStr() != null) {
                entity.setWbStr(command.getWbStr().trim());
            }
            entity.refreshSearchCodeFromName();
            if (!changedFields.contains(FieldNameConstants.PY_STR)) {
                changedFields.add(FieldNameConstants.PY_STR);
            }
            if (!changedFields.contains(FieldNameConstants.WB_STR)) {
                changedFields.add(FieldNameConstants.WB_STR);
            }
        }
        entity.assertBusNoNotBlank();
        entity.assertNameNotBlank();
        this.assertBusNoNotConflict(entity);
        repository.save(entity);
    }

    /**
     * 批量逻辑删除检验设备。
     *
     * @param command 删除命令
     */
    public void deleteLabDevice(DeleteLabDeviceCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_INVALID_ARGUMENT, "删除检验设备命令不能为空");
        }
        List<DeleteLabDeviceCommand.DeleteLabDeviceItemCommand> items = command.resolveItems();
        List<Long> ids = CommandBatchUtil.extractIds(items, DeleteLabDeviceCommand.DeleteLabDeviceItemCommand::getId);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_INVALID_ARGUMENT, "删除检验设备时 id 不能为空");
        }
        List<LabDevice> entities = repository.findByIdIn(ids);
        if (entities.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_NOT_FOUND, "删除检验设备时存在无效主键");
        }
        for (LabDevice entity : entities) {
            if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_NOT_FOUND, "检验设备不存在或已删除");
            }
            entity.markDeleted(command.getOperatorId());
        }
        repository.saveAll(entities);
    }

    /**
     * 批量停用检验设备（ACTIVE → INACTIVE）。
     *
     * @param command 停用命令
     */
    public void deactivateLabDevices(DeactivateLabDevicesCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_INVALID_ARGUMENT, "停用检验设备命令不能为空");
        }
        List<DeactivateLabDevicesCommand.DeactivateLabDeviceItemCommand> items = command.resolveItems();
        List<Long> ids = CommandBatchUtil.extractIds(items, DeactivateLabDevicesCommand.DeactivateLabDeviceItemCommand::getId);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_INVALID_ARGUMENT, "停用检验设备时 id 不能为空");
        }
        List<LabDevice> entities = repository.findByIdIn(ids);
        if (entities.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_NOT_FOUND, "停用检验设备时存在无效主键");
        }
        List<LabDevice> toSave = new ArrayList<>();
        for (LabDevice entity : entities) {
            if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_NOT_FOUND, "检验设备不存在或已删除");
            }
            entity.assertActive();
            entity.deactivate();
            toSave.add(entity);
        }
        repository.saveAll(toSave);
    }

    /**
     * 批量启用检验设备（INACTIVE → ACTIVE）。
     *
     * @param command 启用命令
     */
    public void activateLabDevices(ActivateLabDevicesCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_INVALID_ARGUMENT, "启用检验设备命令不能为空");
        }
        List<ActivateLabDevicesCommand.ActivateLabDeviceItemCommand> items = command.resolveItems();
        List<Long> ids = CommandBatchUtil.extractIds(items, ActivateLabDevicesCommand.ActivateLabDeviceItemCommand::getId);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_INVALID_ARGUMENT, "启用检验设备时 id 不能为空");
        }
        List<LabDevice> entities = repository.findByIdIn(ids);
        if (entities.size() != ids.size()) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_NOT_FOUND, "启用检验设备时存在无效主键");
        }
        List<LabDevice> toSave = new ArrayList<>();
        for (LabDevice entity : entities) {
            if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_NOT_FOUND, "检验设备不存在或已删除");
            }
            entity.assertInactive();
            entity.activate();
            toSave.add(entity);
        }
        repository.saveAll(toSave);
    }

    /**
     * 按主键加载未软删的检验设备实体。
     *
     * @param id 主键
     * @return 实体
     */
    private LabDevice loadLabDevice(Long id) {
        LabDevice entity = repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.LAB_DEVICE_NOT_FOUND, "检验设备不存在"));
        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_NOT_FOUND, "检验设备不存在或已删除");
        }
        return entity;
    }

    /**
     * 校验 bus_no 全局唯一（新建与维护均须通过）。
     *
     * @param entity 待校验实体
     */
    private void assertBusNoNotConflict(LabDevice entity) {
        if (entity.getId() == null) {
            if (repository.existsByBusNo(entity.getBusNo())) {
                throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_BUS_NO_CONFLICT, "检验设备业务编码冲突");
            }
            return;
        }
        if (repository.existsByBusNoAndIdNot(entity.getBusNo(), entity.getId())) {
            throw new BusinessException(ErrorCodeConstants.LAB_DEVICE_BUS_NO_CONFLICT, "检验设备业务编码冲突");
        }
    }

    /**
     * 新建时写入可选字段（未传则保持 null）。
     *
     * @param entity 目标实体
     * @param command 新建命令
     */
    private void applyOptionalFieldsOnCreate(LabDevice entity, CreateLabDeviceCommand command) {
        if (StringUtils.isNotEmpty(command.getPyStr())) {
            entity.setPyStr(command.getPyStr().trim());
        }
        if (StringUtils.isNotEmpty(command.getWbStr())) {
            entity.setWbStr(command.getWbStr().trim());
        }
        entity.setOrgId(command.getOrgId());
        entity.setLocationId(command.getLocationId());
        if (command.getCategoryCode() != null) {
            entity.setCategoryCode(command.getCategoryCode().trim());
        }
        if (command.getModelNumber() != null) {
            entity.setModelNumber(command.getModelNumber().trim());
        }
        if (command.getSerialNumber() != null) {
            entity.setSerialNumber(command.getSerialNumber().trim());
        }
        entity.setManufacturerId(command.getManufacturerId());
        entity.setManufactureDate(command.getManufactureDate());
        entity.setExpirationDate(command.getExpirationDate());
        if (command.getDescription() != null) {
            entity.setDescription(command.getDescription().trim());
        }
    }

    /**
     * 维护时按命令增量更新可选字段，并记录变更字段名供审计扩展。
     *
     * @param entity 目标实体
     * @param command 更新命令
     * @param changedFields 变更字段名收集器
     */
    private void applyOptionalFieldsOnUpdate(
        LabDevice entity,
        UpdateLabDeviceCommand command,
        List<String> changedFields) {
        if (command.getPyStr() != null) {
            String newPyStr = command.getPyStr().trim();
            if (!Objects.equals(entity.getPyStr(), newPyStr)) {
                entity.setPyStr(newPyStr);
                changedFields.add(FieldNameConstants.PY_STR);
            }
        }
        if (command.getWbStr() != null) {
            String newWbStr = command.getWbStr().trim();
            if (!Objects.equals(entity.getWbStr(), newWbStr)) {
                entity.setWbStr(newWbStr);
                changedFields.add(FieldNameConstants.WB_STR);
            }
        }
        if (command.getOrgId() != null && !Objects.equals(entity.getOrgId(), command.getOrgId())) {
            entity.setOrgId(command.getOrgId());
            changedFields.add(FieldNameConstants.ORG_ID);
        }
        if (command.getLocationId() != null && !Objects.equals(entity.getLocationId(), command.getLocationId())) {
            entity.setLocationId(command.getLocationId());
            changedFields.add(FieldNameConstants.LOCATION_ID);
        }
        if (command.getCategoryCode() != null) {
            String newCategory = command.getCategoryCode().trim();
            if (!Objects.equals(entity.getCategoryCode(), newCategory)) {
                entity.setCategoryCode(newCategory);
                changedFields.add(FieldNameConstants.CATEGORY_CODE);
            }
        }
        if (command.getModelNumber() != null) {
            String newModel = command.getModelNumber().trim();
            if (!Objects.equals(entity.getModelNumber(), newModel)) {
                entity.setModelNumber(newModel);
                changedFields.add("modelNumber");
            }
        }
        if (command.getSerialNumber() != null) {
            String newSerial = command.getSerialNumber().trim();
            if (!Objects.equals(entity.getSerialNumber(), newSerial)) {
                entity.setSerialNumber(newSerial);
                changedFields.add("serialNumber");
            }
        }
        if (command.getManufacturerId() != null
            && !Objects.equals(entity.getManufacturerId(), command.getManufacturerId())) {
            entity.setManufacturerId(command.getManufacturerId());
            changedFields.add(FieldNameConstants.MANUFACTURER_ID);
        }
        if (command.getManufactureDate() != null
            && !Objects.equals(entity.getManufactureDate(), command.getManufactureDate())) {
            entity.setManufactureDate(command.getManufactureDate());
            changedFields.add("manufactureDate");
        }
        if (command.getExpirationDate() != null
            && !Objects.equals(entity.getExpirationDate(), command.getExpirationDate())) {
            entity.setExpirationDate(command.getExpirationDate());
            changedFields.add("expirationDate");
        }
        if (command.getDescription() != null) {
            String newDesc = command.getDescription().trim();
            if (!Objects.equals(entity.getDescription(), newDesc)) {
                entity.setDescription(newDesc);
                changedFields.add(FieldNameConstants.DESCRIPTION);
            }
        }
    }
}
