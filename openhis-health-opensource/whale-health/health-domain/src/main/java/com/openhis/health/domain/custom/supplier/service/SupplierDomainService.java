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

package com.openhis.health.domain.custom.supplier.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.custom.supplier.command.*;
import com.openhis.health.domain.custom.supplier.entity.Supplier;
import com.openhis.health.domain.custom.supplier.repository.SupplierRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.SupplierType;
import com.openhis.health.domain.share.events.custom.supplier.*;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import com.openhis.whale.core.infrastructure.util.ChineseConvertUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 供应商写侧领域服务。
 */
@Service
@RequiredArgsConstructor
public class SupplierDomainService {

    private final SupplierRepository repository;
    private final IDomainEventPublisher domainEventPublisher;
    private final BusinessSequenceService businessSequenceService;


    /**
     * 注册供应商。
     *
     * @param command 注册命令
     * @return 供应商主键
     */
    public Long registerSupplier(RegisterSupplierCommand command) {
        if (command == null || !StringUtils.hasText(command.getName())) {
            throw new BusinessException(ErrorCodeConstants.SUPPLIER_INVALID_ARGUMENT, "供应商名称不能为空");
        }
        String busNo = resolveBusNo(command.getBusNo());
        if (repository.existsByBusNo(busNo)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLIER_BUS_NO_CONFLICT, "业务编号已存在");
        }

        String creditCode = trimToEmpty(command.getCreditCode());
        if (StringUtils.hasText(creditCode) && repository.existsByCreditCode(creditCode)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLIER_CREDIT_CODE_CONFLICT, "统一社会信用代码已存在");
        }

        Supplier supplier = new Supplier();
        supplier.setBusNo(busNo);
        supplier.setName(command.getName().trim());
        String typeEnum = trimToEmpty(command.getTypeEnum());
        if (!StringUtils.hasText(typeEnum)) {
            typeEnum = SupplierType.SUPPLIER.getCode();
        }
        supplier.setTypeEnum(typeEnum);
        supplier.setCreditCode(creditCode);
        supplier.setLegalRepresentative(trimToNull(command.getLegalRepresentative()));
        supplier.setAddress(trimToNull(command.getAddress()));
        String pyStr = trimToNull(command.getPyStr());
        if (!StringUtils.hasText(pyStr)) {
            pyStr = ChineseConvertUtils.toPinyinFirstLetter(supplier.getName());
        }
        String wbStr = trimToNull(command.getWbStr());
        if (!StringUtils.hasText(wbStr)) {
            wbStr = ChineseConvertUtils.toWBFirstLetter(supplier.getName());
        }
        supplier.setPyStr(pyStr);
        supplier.setWbStr(wbStr);
        supplier.setPhone(trimToNull(command.getPhone()));
        supplier.setEmail(trimToNull(command.getEmail()));
        supplier.setBankName(trimToNull(command.getBankName()));
        supplier.setBankAccount(trimToNull(command.getBankAccount()));
        if (command.getOrgId() != null) {
            supplier.setOrgId(command.getOrgId());
        } else {
            // 未指定机构时落 0，表示全院/未挂靠机构供应商。
            supplier.setOrgId(0L);
        }
        supplier.setDisplayOrder(command.getDisplayOrder());
        supplier.setNote(trimToNull(command.getNote()));
        supplier.initDraftStatus();
        supplier.assertRequiredFields();

        Supplier saved = repository.save(supplier);
        domainEventPublisher.publish(new SupplierRegisteredEvent(saved.getId(), saved.getBusNo(), saved.getName(), saved.getTypeEnum(), command.getOperatorId()));
        return saved.getId();
    }

    /**
     * 启用供应商（支持单条与批量）。
     *
     * @param command 启用命令
     */
    public void activateSupplier(ActivateSupplierCommand command) {
        List<ActivateSupplierCommand.ActivateSupplierItemCommand> items = command.resolveItems();
        List<Supplier> toSave = new ArrayList<>();
        List<SupplierActivatedEvent> activatedEvents = new ArrayList<>();
        List<SupplierReactivatedEvent> reactivatedEvents = new ArrayList<>();
        for (ActivateSupplierCommand.ActivateSupplierItemCommand item : items) {
            Supplier supplier = this.requireSupplier(item.getSupplierId());
            // 在状态变更前先判断来源状态，用于区分发布"从停用恢复"事件还是"首次启用"事件。
            boolean fromSuspended = supplier.isFromSuspended();
            supplier.assertActivatable();
            supplier.activate();
            toSave.add(supplier);
            activatedEvents.add(new SupplierActivatedEvent(supplier.getId(), supplier.getBusNo(), command.getOperatorId()));
            // 业务分支处理。
            if (fromSuspended) {
                reactivatedEvents.add(new SupplierReactivatedEvent(supplier.getId(), supplier.getBusNo(), command.getOperatorId()));
            }
        }
        repository.saveAll(toSave);
        for (SupplierActivatedEvent event : activatedEvents) {
            domainEventPublisher.publish(event);
        }
        for (SupplierReactivatedEvent event : reactivatedEvents) {
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 停用供应商（支持单条与批量）。
     *
     * @param command 停用命令
     */
    public void suspendSupplier(SuspendSupplierCommand command) {
        List<SuspendSupplierCommand.SuspendSupplierItemCommand> items = command.resolveItems();
        List<Supplier> toSave = new ArrayList<>();
        List<SupplierSuspendedEvent> events = new ArrayList<>();
        for (SuspendSupplierCommand.SuspendSupplierItemCommand item : items) {
            Supplier supplier = this.requireSupplier(item.getSupplierId());
            supplier.assertSuspendable();
            supplier.suspend();
            toSave.add(supplier);
            events.add(new SupplierSuspendedEvent(supplier.getId(), supplier.getBusNo(), item.getReason(), command.getOperatorId()));
        }
        repository.saveAll(toSave);
        for (SupplierSuspendedEvent event : events) {
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 废弃供应商（支持单条与批量）。
     *
     * @param command 废弃命令
     */
    public void retireSupplier(RetireSupplierCommand command) {
        List<RetireSupplierCommand.RetireSupplierItemCommand> items = command.resolveItems();
        List<Supplier> toSave = new ArrayList<>();
        List<SupplierRetiredEvent> events = new ArrayList<>();
        for (RetireSupplierCommand.RetireSupplierItemCommand item : items) {
            Supplier supplier = this.requireSupplier(item.getSupplierId());
            supplier.retire(command.getOperatorId());
            toSave.add(supplier);
            events.add(new SupplierRetiredEvent(supplier.getId(), supplier.getBusNo(), item.getReason(), command.getOperatorId()));
        }
        repository.saveAll(toSave);
        for (SupplierRetiredEvent event : events) {
            domainEventPublisher.publish(event);
        }
    }

    /**
     * 维护供应商基础信息。
     *
     * @param command 维护命令
     */
    public void maintainSupplierInfo(MaintainSupplierInfoCommand command) {
        Supplier supplier = this.requireSupplier(command.getSupplierId());
        supplier.assertInfoMaintainable();
        // 业务分支处理。
        if (command.getName() != null) {
            supplier.setName(command.getName());
        }
        // 业务分支处理。
        if (command.getTypeEnum() != null) {
            supplier.setTypeEnum(command.getTypeEnum());
        }
        // 业务分支处理。
        if (command.getLegalRepresentative() != null) {
            supplier.setLegalRepresentative(command.getLegalRepresentative());
        }
        // 业务分支处理。
        if (command.getAddress() != null) {
            supplier.setAddress(command.getAddress());
        }
        // 业务分支处理。
        if (command.getPyStr() != null) {
            supplier.setPyStr(command.getPyStr());
        }
        // 业务分支处理。
        if (command.getWbStr() != null) {
            supplier.setWbStr(command.getWbStr());
        }
        // 业务分支处理。
        if (command.getPhone() != null) {
            supplier.setPhone(command.getPhone());
        }
        // 业务分支处理。
        if (command.getEmail() != null) {
            supplier.setEmail(command.getEmail());
        }
        // 业务分支处理。
        if (command.getBankName() != null) {
            supplier.setBankName(command.getBankName());
        }
        // 业务分支处理。
        if (command.getBankAccount() != null) {
            supplier.setBankAccount(command.getBankAccount());
        }
        // 业务分支处理。
        if (command.getDisplayOrder() != null) {
            supplier.setDisplayOrder(command.getDisplayOrder());
        }
        // 业务分支处理。
        if (command.getNote() != null) {
            supplier.setNote(command.getNote());
        }
        supplier.assertRequiredFields();
        repository.save(supplier);
        domainEventPublisher.publish(new SupplierInfoUpdatedEvent(
            supplier.getId(),
            CustomConstants.ChangedField.DEFAULT_CHANGED_FIELDS
        , command.getOperatorId()));
    }

    /**
     * 读取未删除供应商。
     *
     * @param supplierId 供应商标识
     * @return 供应商实体
     */
    private Supplier requireSupplier(Long supplierId) {
        return repository.findById(supplierId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SUPPLIER_NOT_FOUND, "供应商不存在"));
    }

    /**
     * 优先使用前端传入编号；未传则按序列生成（前缀 {@link AggregateTypeConstants#SUPPLIER}）。
     */
    private String resolveBusNo(String requested) {
        if (StringUtils.hasText(requested)) {
            return requested.trim();
        }
        for (int i = 0; i < 10; i++) {
            String code = businessSequenceService.getSeq(AggregateTypeConstants.SUPPLIER, 10);
            if (!repository.existsByBusNo(code)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCodeConstants.SUPPLIER_BUS_NO_CONFLICT, "生成供应商编号失败，请重试");
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 文本 trim 后回落为空串（持久化可选字段「未填」与 null 区分场景）。
     *
     * @param value 原始文本
     * @return trim 后非空串，或 {@link GeneralSharedConstants#EMPTY_STRING}
     */
    private static String trimToEmpty(String value) {
        String t = trimToNull(value);
        if (t == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return t;
    }
}

