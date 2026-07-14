/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.supplier.service;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.custom.supplier.entity.Supplier;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierDetailQueryModel;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.custom.supplier.repository.SupplierRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplierStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 供应商主数据读侧查询服务。
 *
 * <p>职责：为采购、库存与主数据维护提供供应商列表与详情只读能力；过滤与分页下推到仓储。</p>
 *
 * <p>聚合边界：仅本聚合 {@link SupplierRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class SupplierQueryService {

    private final SupplierRepository repository;


    /**
     * 按主键查询供应商详情。
     *
     * @param supplierId 供应商标识
     * @return 详情读模型
     * @throws BusinessException 供应商不存在
     */
    public SupplierDetailQueryModel findSupplierById(Long supplierId) {
        Supplier supplier = repository.findById(supplierId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SUPPLIER_NOT_FOUND, "供应商不存在"));
        return this.toDetailModel(supplier);
    }

    /**
     * 按主键解析供应商名称（不存在时不抛错），用于主数据写入侧回填生产厂家文本等场景。
     *
     * @param supplierId 供应商标识，可为 {@code null}
     * @return 名称；无主键或未命中时为空
     */
    public Optional<String> findSupplierNameIfPresent(Long supplierId) {
        if (supplierId == null) {
            return Optional.empty();
        }
        return repository.findById(supplierId).map(Supplier::getName);
    }

    /**
     * 查询当前租户下全部有效供应商简表（软删由仓储/底座口径排除）。
     *
     * @return 列表项集合
     */
    public List<SupplierListItemQueryModel> listSuppliers() {
        List<Supplier> suppliers = repository.findAllBy();
        List<SupplierListItemQueryModel> result = new ArrayList<>();
        // 转为列表行，供下拉或表格只读展示
        for (Supplier supplier : suppliers) {
            result.add(this.toListItemModel(supplier));
        }
        return result;
    }

    /**
     * 按类型与「启用」状态筛选供应商，用于业务单据选择有效供货方。
     *
     * @param typeEnum 供应商类型编码
     * @return 列表项集合
     */
    public List<SupplierListItemQueryModel> listSuppliersByTypeAndActive(String typeEnum) {
        List<Supplier> suppliers = repository.findAllByTypeEnumAndStatusEnum(
            typeEnum,
            SupplierStatus.ACTIVE.getCode()
        );
        List<SupplierListItemQueryModel> result = new ArrayList<>();
        for (Supplier supplier : suppliers) {
            result.add(this.toListItemModel(supplier));
        }
        return result;
    }

    /**
     * 按名称模糊匹配分页查询供应商，支持管理端检索；分页由数据库完成。
     *
     * @param name 名称关键字，null 视为空串即不按名称收窄
     * @param pageNo 页码（0 基），null 时默认首页
     * @param pageSize 每页条数，null 时使用批量上限常量
     * @return 分页列表项
     */
    public Page<SupplierListItemQueryModel> pageSuppliers(String name, Integer pageNo, Integer pageSize) {
        int normalizedPageNo;
        // 未传页码时从第一页查起，避免调用方 NPE
        if (pageNo == null) {
            normalizedPageNo = 0;
        } else {
            normalizedPageNo = pageNo;
        }
        int normalizedPageSize;
        // 未传页大小时用统一批量上限，防止一次拉取过大拖垮接口
        if (pageSize == null) {
            normalizedPageSize = GeneralSharedConstants.BATCH_LIMIT;
        } else {
            normalizedPageSize = pageSize;
        }
        Pageable pageable = PageRequest.of(normalizedPageNo, normalizedPageSize, Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT));
        String normalizedName;
        // null 名称按空串处理，语义为「不按名称过滤」由仓储 like 实现
        if (name == null) {
            normalizedName = "";
        } else {
            normalizedName = name;
        }
        Page<Supplier> page = repository.findAllByNameContaining(normalizedName, pageable);
        return page.map(this::toListItemModel);
    }

    /**
     * 列表行映射：仅暴露检索与选择所需字段。
     *
     * @param supplier 实体
     * @return 列表项模型
     */
    private SupplierListItemQueryModel toListItemModel(Supplier supplier) {
        SupplierListItemQueryModel model = new SupplierListItemQueryModel();
        model.setId(supplier.getId());
        model.setBusNo(supplier.getBusNo());
        model.setName(supplier.getName());
        model.setTypeEnum(supplier.getTypeEnum());
        model.setStatusEnum(supplier.getStatusEnum());
        model.setPyStr(supplier.getPyStr());
        model.setAddress(supplier.getAddress());
        model.setPhone(supplier.getPhone());
        model.setEmail(supplier.getEmail());
        return model;
    }

    /**
     * 详情映射：包含联系方式与账户等维护字段。
     *
     * @param supplier 实体
     * @return 详情模型
     */
    private SupplierDetailQueryModel toDetailModel(Supplier supplier) {
        SupplierDetailQueryModel model = new SupplierDetailQueryModel();
        model.setId(supplier.getId());
        model.setBusNo(supplier.getBusNo());
        model.setName(supplier.getName());
        model.setTypeEnum(supplier.getTypeEnum());
        model.setCreditCode(supplier.getCreditCode());
        model.setLegalRepresentative(supplier.getLegalRepresentative());
        model.setAddress(supplier.getAddress());
        model.setPyStr(supplier.getPyStr());
        model.setWbStr(supplier.getWbStr());
        model.setPhone(supplier.getPhone());
        model.setEmail(supplier.getEmail());
        model.setBankName(supplier.getBankName());
        model.setBankAccount(supplier.getBankAccount());
        model.setOrgId(supplier.getOrgId());
        model.setDisplayOrder(supplier.getDisplayOrder());
        model.setStatusEnum(supplier.getStatusEnum());
        model.setNote(supplier.getNote());
        return model;
    }
}
