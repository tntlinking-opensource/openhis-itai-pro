/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.devicedefinition.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionPurchasePageQuery;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.repository.DeviceDefinitionRepository;
import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 器材定义读侧查询服务。
 *
 * <p>职责：提供器材定义的只读查询能力，包括分页浏览、关键词检索、详情加载等。</p>
 *
 * <p>边界：只读，不写库；联查与报表走读模型，不在本类展开复杂联表。</p>
 *
 * <p>聚合边界：仅本聚合 {@link DeviceDefinitionRepository} 持久化；
 * 目录 {@code positiveQuantityOnly} 仅用 Criteria EXISTS 判定库存是否存在可用正库存行，
 * 可选按 {@code positiveQuantityLocationIds} 收窄库位；不注入库存 Repository。</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceDefinitionQueryService {

    private final DeviceDefinitionRepository repository;

    /**
     * 获取启用中的器材定义（初始化列表）。
     *
     * @return 启用中的器材定义列表
     */
    public List<DeviceDefinition> getDeviceManageInit() {
        // 初始化列表仅展示启用状态器材，避免运维页加载停用/草稿数据。
        Specification<DeviceDefinition> spec = (root, query, cb) -> cb.and(
            DeviceDefinitionQueryService.notDeletedPredicate(root, cb),
            DeviceDefinitionQueryService.activeStatusPredicate(root, cb));
        return repository.findAll(spec);
    }

    /**
     * 分页查询器材定义。
     *
     * @param pageNum  页码（0-based）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public Page<DeviceDefinition> pageDeviceDefinition(int pageNum, int pageSize) {

        // 分页下推到仓储，调用方传入的 pageNum 须与 Spring Data 约定一致（通常为 0-based）。
        return this.pageDeviceDefinition(pageNum, pageSize, null, null, null);
    }

    /**
     * 分页查询器材定义（可选关键字、分类与耗材种类；未删除记录，含启用/停用全状态）。
     *
     * <p>基础数据维护页需展示停用项以便启停操作，口径对齐诊疗/药品目录分页（不按 status 收窄）。</p>
     * <p>关键字同时匹配名称、拼音码、五笔码、业务编码、医保编码（OR）。</p>
     *
     * @param pageNum      页码（0-based）
     * @param pageSize     每页大小
     * @param searchKey    综合关键字，空白则不作为条件
     * @param categoryCode 分类编码，空白则不作为条件
     * @param typeCode     耗材种类编码（{@code device_type_code}），空白则不作为条件
     * @return 分页结果（固定按业务号倒序，避免无排序时编辑后行位跳动）
     */
    public Page<DeviceDefinition> pageDeviceDefinition(
        int pageNum, int pageSize, String searchKey, String categoryCode, String typeCode) {
        String sk = trimToEmpty(searchKey);
        String cc = trimToEmpty(categoryCode);
        String tc = trimToEmpty(typeCode);
        // 目录列表固定按 busNo 倒序，与药品/诊疗目录维护页一致；有关键字时改由 Spec 写「精确优先 + busNo」
        Pageable pageable;
        if (sk.isEmpty()) {
            pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, FieldNameConstants.BUS_NO));
        } else {
            pageable = PageRequest.of(pageNum, pageSize);
        }
        if (sk.isEmpty() && cc.isEmpty() && tc.isEmpty()) {
            Specification<DeviceDefinition> catalogPage = (root, query, cb) ->
                DeviceDefinitionQueryService.notDeletedPredicate(root, cb);
            return repository.findAll(catalogPage, pageable);
        }
        Specification<DeviceDefinition> spec = (root, query, cb) -> {
            List<Predicate> ands = new ArrayList<>();
            ands.add(DeviceDefinitionQueryService.notDeletedPredicate(root, cb));
            if (!cc.isEmpty()) {
                ands.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), cc));
            }
            if (!tc.isEmpty()) {
                ands.add(cb.equal(root.get(FieldNameConstants.TYPE_CODE), tc));
            }
            if (!sk.isEmpty()) {
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(sk);
                List<Predicate> ors = new ArrayList<>();
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_NAME)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_PY_STR)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_WB_STR)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.YB_NO)), likeIc));
                ands.add(cb.or(ors.toArray(Predicate[]::new)));
                // B.2.1：精确全等置顶，次级仍按 busNo 倒序
                List<Order> secondary = List.of(cb.desc(root.get(FieldNameConstants.BUS_NO)));
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    query,
                    cb,
                    sk,
                    secondary,
                    root.get(FieldNameConstants.NAME),
                    root.get(FieldNameConstants.MERCHANDISE_NAME),
                    root.get(FieldNameConstants.PY_STR),
                    root.get(FieldNameConstants.WB_STR),
                    root.get(FieldNameConstants.MERCHANDISE_PY_STR),
                    root.get(FieldNameConstants.MERCHANDISE_WB_STR),
                    root.get(FieldNameConstants.BUS_NO),
                    root.get(FieldNameConstants.YB_NO));
            }
            return cb.and(ands.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable);
    }

    /**
     * 按主键加载器材定义详情。
     *
     * @param id 器材定义主键
     * @return 器材定义（不存在时返回 null）
     */
    public DeviceDefinition getDeviceDefinition(Long id) {
        // 详情加载：不存在时返回 null，由应用层决定提示或 404。
        return repository.findById(id).orElse(null);
    }

    /**
     * 按耗材定义主键解析目录类型码（耗材请求 FIFO 签发等读侧用）。
     *
     * @param deviceDefinitionId 耗材定义主键
     * @return 类型码；未命中时 {@code null}
     */
    public String resolveCategoryCodeByDeviceDefinitionId(Long deviceDefinitionId) {
        if (deviceDefinitionId == null) {
            return null;
        }
        DeviceDefinition definition = this.getDeviceDefinition(deviceDefinitionId);
        if (definition == null) {
            return null;
        }
        return definition.getCategoryCode();
    }

    /**
     * 按关键词检索启用中的器材定义（DM-ADM-008 §6.2.4）。
     *
     * <p>关键词同时匹配名称、商品名、拼音首码、五笔首码；返回启用（{@link PublicationStatus#ACTIVE}）记录。</p>
     *
     * @param keyword 检索关键词（不含通配符，内部拼接）
     * @return 匹配的器材定义列表
     */
    public List<DeviceDefinition> searchByKeyWord(String keyword) {
        // 与分页检索同口径：用户关键字驱动之 `LIKE` 一律 `lower(列)` + {@link SearchCodeUtil#toCaseInsensitiveLikePattern}。
        String sk = trimToEmpty(keyword);
        Specification<DeviceDefinition> spec = (root, query, cb) -> {
            List<Predicate> ands = new ArrayList<>();
            ands.add(DeviceDefinitionQueryService.notDeletedPredicate(root, cb));
            ands.add(DeviceDefinitionQueryService.activeStatusPredicate(root, cb));
            if (!sk.isEmpty()) {
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(sk);
                ands.add(cb.or(
                    cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_NAME)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_PY_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_WB_STR)), likeIc)
                ));
                // B.2.1：下拉选品精确全等置顶
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    query,
                    cb,
                    sk,
                    List.of(cb.desc(root.get(FieldNameConstants.ID))),
                    root.get(FieldNameConstants.NAME),
                    root.get(FieldNameConstants.MERCHANDISE_NAME),
                    root.get(FieldNameConstants.PY_STR),
                    root.get(FieldNameConstants.WB_STR),
                    root.get(FieldNameConstants.MERCHANDISE_PY_STR),
                    root.get(FieldNameConstants.MERCHANDISE_WB_STR));
            }
            return cb.and(ands.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, Pageable.unpaged()).getContent();
    }

    /**
     * 按检索码查找启用中的器材定义（DM-ADM-008 §6.2.5）。
     *
     * <p>与 {@link #searchByKeyWord(String)} 语义等价，为兼容旧调用链保留此方法名。</p>
     *
     * @param searchKey 检索关键词
     * @return 匹配的器材定义列表
     */
    public List<DeviceDefinition> findBySearchKey(String searchKey) {
        // 与关键词检索同口径，保留旧接口名以兼容调用链。
        return this.searchByKeyWord(searchKey);
    }

    /**
     * 按主键集合批量加载器材定义。
     *
     * @param ids 主键集合
     * @return 器材定义列表
     */
    public List<DeviceDefinition> getAllDeviceDefinitions(Collection<Long> ids) {
        // 批量主键 IN 查询，供医嘱/收费等场景一次解析多条器材定义。
        return repository.findAllByIdIn(ids);
    }

    /**
     * 采购/进货选品分页（读 {@code adm_device_definition}，谓词对齐原产品读模型采购分支）。
     */
    public Page<DeviceDefinition> pageForPurchase(ProductDefinitionPurchasePageQuery query, Pageable pageable) {
        // 关键字非空时剥掉 Pageable Sort，保留 Spec 内「精确优先」ORDER BY（B.2.1）
        Pageable effective = pageable;
        if (query != null && StringUtils.isNotEmpty(query.getKeyword())
            && pageable != null && pageable.getSort().isSorted()) {
            effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return repository.findAll(this.buildPurchasePageSpecification(query), effective);
    }

    /** 医嘱开立目录分页（读耗材主表）。 */
    public Page<DeviceDefinition> pageForCatalog(ProductDefinitionCatalogPageQuery query, Pageable pageable) {
        ProductDefinitionPurchasePageQuery purchase = new ProductDefinitionPurchasePageQuery();
        if (query != null) {
            purchase.setCategoryCode(query.getCategoryCode());
            purchase.setActiveFlag(query.getActiveFlag());
            purchase.setSupplierId(query.getSupplierId());
            purchase.setManufacturerId(query.getManufacturerId());
            purchase.setManufacturerIds(query.getManufacturerIds());
            purchase.setManufacturer(query.getManufacturer());
            purchase.setKeyword(query.getKeyword());
            purchase.setPrepickedProductKeys(query.getPrepickedProductKeys());
            purchase.setPositiveQuantityOnly(query.getPositiveQuantityOnly());
            // 目录「只看库存>0」库位收窄与采购 Spec 共用 EXISTS
            purchase.setPositiveQuantityLocationIds(query.getPositiveQuantityLocationIds());
            purchase.setPageNo(query.getPageNo());
            purchase.setPageSize(query.getPageSize());
        }
        return this.pageForPurchase(purchase, pageable);
    }

    /**
     * 按业务编码批量加载器材定义（外部目录同步后解析实例主键）。
     *
     * @param busNos 业务编码列表
     * @return 命中实体列表；入参为空时返回空列表
     */
    public List<DeviceDefinition> findByBusNos(List<String> busNos) {
        if (busNos == null || busNos.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findByBusNoIn(busNos);
    }

    /**
     * 加载器材定义（含可编辑性判断入口）。
     *
     * @param id 器材定义主键
     * @return 器材定义（不存在时返回 null）
     */
    public DeviceDefinition getDeviceDefinitionEditability(Long id) {
        // 与 getDeviceDefinition 同源，预留编辑前校验入口命名。
        return repository.findById(id).orElse(null);
    }

    /**
     * 下载器材目录模板（返回模板标识，实际文件生成由应用层处理）。
     *
     * @return 模板标识占位字符串
     */
    public String downloadDeviceDefinitionCatalogTemplate() {
        // 占位：真实模板文件由应用层生成；领域层仅返回约定标识。
        return "template";
    }

    /**
     * 导出启用中的器材定义目录。
     *
     * @return 器材定义列表（导出候选集）
     */
    public List<DeviceDefinition> exportDeviceDefinitionCatalog() {
        // 导出与初始化列表口径一致：仅启用数据进入导出候选集。
        Specification<DeviceDefinition> spec = (root, query, cb) -> cb.and(
            DeviceDefinitionQueryService.notDeletedPredicate(root, cb),
            DeviceDefinitionQueryService.activeStatusPredicate(root, cb));
        return repository.findAll(spec);
    }

    /**
     * 器材采购/选品分页谓词：未删除、分类、启用、供应商、关键字与预选 OR 组合。
     *
     * @param query 采购分页查询
     * @return JPA Specification
     */
    private Specification<DeviceDefinition> buildPurchasePageSpecification(ProductDefinitionPurchasePageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> ands = new ArrayList<>();
            ands.add(DeviceDefinitionQueryService.notDeletedPredicate(root, cb));
            if (query != null) {
                if (StringUtils.isNotEmpty(query.getCategoryCode())) {
                    ands.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), query.getCategoryCode().trim()));
                }
                if (query.getActiveFlag() != null) {
                    ands.add(cb.equal(root.get(FieldNameConstants.ACTIVE_FLAG), query.getActiveFlag()));
                }
                if (query.getSupplierId() != null) {
                    ands.add(cb.equal(root.get(FieldNameConstants.SUPPLY_ID), query.getSupplierId()));
                }
                this.appendManufacturerIdPredicates(ands, root, cb, query.getManufacturerId(),
                    query.getManufacturerIds(), query.getManufacturer());
                Predicate keywordPred = this.buildPurchaseKeywordPredicate(root, cb, query.getKeyword());
                Predicate prepPred = this.buildDevicePrepickedPredicate(root, cb, query.getPrepickedProductKeys());
                this.appendKeywordOrPrepicked(ands, cb, keywordPred, prepPred);
                // 医生站选品「只看库存>0」：须在分页 limit/offset 前过滤
                if (Boolean.TRUE.equals(query.getPositiveQuantityOnly())) {
                    ands.add(this.buildPositiveAvailableInventoryExistsPredicate(
                        root, cq, cb, TableNameConstants.ADM_DEVICE_DEFINITION, query.getPositiveQuantityLocationIds()));
                }
                // B.2.1：有关键字时精确命中置顶（与 LIKE 列同集合）
                this.applyPurchaseKeywordExactFirstOrder(root, cq, cb, query.getKeyword());
            }
            return cb.and(ands.toArray(Predicate[]::new));
        };
    }

    /**
     * 耗材定义存在「可用正库存」行：ACTIVE、quantity&gt;0、未过期、未删除。
     *
     * <p>可选按库位集合收窄（医生站候选发药库位）；{@code locationIds == null} 不收窄；
     * 非 null 且空列表恒不命中。</p>
     *
     * @param root         耗材定义根
     * @param cq           外层查询
     * @param cb           CriteriaBuilder
     * @param itemTable    库存 item_table（耗材定义表名）
     * @param locationIds  可选库位收窄；null=不收窄
     * @return EXISTS 谓词；库位列表为空时返回恒假
     */
    private Predicate buildPositiveAvailableInventoryExistsPredicate(
        Root<DeviceDefinition> root,
        CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        String itemTable,
        List<Long> locationIds
    ) {
        // 医生站已解析候选库位但结果为空：无可开立库存维度，耗材定义一律不命中
        if (locationIds != null && locationIds.isEmpty()) {
            return cb.disjunction();
        }
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<InventoryItem> inv = sub.from(InventoryItem.class);
        sub.select(inv.get(FieldNameConstants.ID));
        LocalDateTime now = LocalDateTime.now();
        List<Predicate> where = new ArrayList<>();
        where.add(cb.isFalse(inv.get(FieldNameConstants.IS_DELETED)));
        where.add(cb.equal(inv.get(FieldNameConstants.ITEM_TABLE), itemTable));
        where.add(cb.equal(inv.get(FieldNameConstants.ITEM_ID), root.get(FieldNameConstants.ID)));
        where.add(cb.equal(inv.get(FieldNameConstants.INVENTORY_STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
        where.add(cb.greaterThan(inv.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
        where.add(cb.or(
            cb.isNull(inv.get(FieldNameConstants.EXPIRATION_DATE)),
            cb.greaterThan(inv.get(FieldNameConstants.EXPIRATION_DATE), now)));
        // 有候选库位时与目录展示同口径收窄
        if (locationIds != null) {
            where.add(inv.get(FieldNameConstants.LOCATION_ID).in(locationIds));
        }
        sub.where(where.toArray(Predicate[]::new));
        return cb.exists(sub);
    }

    /**
     * 采购关键字 OR 谓词：名称、商品名、拼音五笔、业务号不区分大小写模糊匹配。
     *
     * @param root    器材定义根
     * @param cb      CriteriaBuilder
     * @param keyword 用户关键字；空时返回 null
     * @return 关键字谓词或 null
     */
    private Predicate buildPurchaseKeywordPredicate(Root<DeviceDefinition> root, CriteriaBuilder cb, String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
        // 纯拼音/五笔字母输入：只扫检索码列
        if (SearchCodeUtil.isAsciiLetterKeyword(trimmed)) {
            return cb.or(
                cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_PY_STR)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_WB_STR)), likeIc)
            );
        }
        return cb.or(
            cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
            cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_NAME)), likeIc),
            cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
            cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc),
            cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_PY_STR)), likeIc),
            cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_WB_STR)), likeIc),
            cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc)
        );
    }

    /**
     * 采购/开立选品关键字：SQL 层「精确全等在前、模糊在后」（B.2.1）。
     *
     * @param root    器材定义根
     * @param cq      CriteriaQuery（count 由工具跳过 orderBy）
     * @param cb      CriteriaBuilder
     * @param keyword 用户关键字；空则不写排序
     */
    private void applyPurchaseKeywordExactFirstOrder(
        Root<DeviceDefinition> root,
        jakarta.persistence.criteria.CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return;
        }
        String trimmed = keyword.trim();
        List<Order> secondary = List.of(cb.desc(root.get(FieldNameConstants.CREATED_AT)));
        if (SearchCodeUtil.isAsciiLetterKeyword(trimmed)) {
            KeywordExactFirstOrderSupport.applyExactFirstOrder(
                cq,
                cb,
                trimmed,
                secondary,
                root.get(FieldNameConstants.PY_STR),
                root.get(FieldNameConstants.WB_STR),
                root.get(FieldNameConstants.MERCHANDISE_PY_STR),
                root.get(FieldNameConstants.MERCHANDISE_WB_STR));
            return;
        }
        KeywordExactFirstOrderSupport.applyExactFirstOrder(
            cq,
            cb,
            trimmed,
            secondary,
            root.get(FieldNameConstants.NAME),
            root.get(FieldNameConstants.MERCHANDISE_NAME),
            root.get(FieldNameConstants.PY_STR),
            root.get(FieldNameConstants.WB_STR),
            root.get(FieldNameConstants.MERCHANDISE_PY_STR),
            root.get(FieldNameConstants.MERCHANDISE_WB_STR),
            root.get(FieldNameConstants.BUS_NO));
    }

    /**
     * 预选器材主键 OR 谓词：仅匹配 {@code adm_device_definition} 表行的 productDefinitionId。
     *
     * @param root 器材定义根
     * @param cb   CriteriaBuilder
     * @param keys 批量查找键；空时返回 null
     * @return 预选 OR 谓词或 null
     */
    private Predicate buildDevicePrepickedPredicate(
        Root<DeviceDefinition> root,
        CriteriaBuilder cb,
        List<ProductDefinitionBatchLookup> keys
    ) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        List<Predicate> parts = new ArrayList<>();
        for (ProductDefinitionBatchLookup lookup : keys) {
            if (lookup == null || lookup.getProductDefinitionId() == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(lookup.getProductTableName())
                && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(lookup.getProductTableName().trim())) {
                continue;
            }
            parts.add(cb.equal(root.get(FieldNameConstants.ID), lookup.getProductDefinitionId()));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return cb.or(parts.toArray(Predicate[]::new));
    }

    /**
     * 将关键字谓词与预选谓词合并进 AND 列表：二者皆有则 OR，否则追加非空者。
     *
     * @param predicates   累积 AND 谓词列表
     * @param cb           CriteriaBuilder
     * @param keywordPred  关键字谓词
     * @param prepPred     预选谓词
     */
    private void appendKeywordOrPrepicked(
        List<Predicate> predicates,
        CriteriaBuilder cb,
        Predicate keywordPred,
        Predicate prepPred
    ) {
        if (keywordPred != null && prepPred != null) {
            predicates.add(cb.or(prepPred, keywordPred));
            return;
        }
        if (keywordPred != null) {
            predicates.add(keywordPred);
            return;
        }
        if (prepPred != null) {
            predicates.add(prepPred);
        }
    }

    /**
     * 器材定义未逻辑删除谓词（复用导出、分页等多处 Specification）。
     *
     * @param root 器材定义根
     * @param cb   CriteriaBuilder
     * @return is_deleted = false 谓词
     */
    private static Predicate notDeletedPredicate(Root<DeviceDefinition> root, CriteriaBuilder cb) {
        return cb.isFalse(root.get(FieldNameConstants.IS_DELETED));
    }

    /**
     * 启用态过滤：新域 {@code ACTIVE} 与历史存量 {@code "1"} 并存期兼容。
     */
    private static Predicate activeStatusPredicate(Root<DeviceDefinition> root, CriteriaBuilder cb) {
        return cb.or(
            cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()),
            cb.equal(root.get(FieldNameConstants.STATUS_ENUM), "1"));
    }

    /**
     * 按耗材类型编码批量解析主键（库存明细筛选，禁止 inventory 包内 Subquery 耗材主表）。
     *
     * @param categoryCodes 类型编码集合
     * @return 未删除且 category_code 命中的主键列表
     */
    public List<Long> listIdsByCategoryCodesIn(Collection<String> categoryCodes) {
        if (categoryCodes == null || categoryCodes.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<DeviceDefinition> specification = (root, criteriaQuery, cb) -> cb.and(
            DeviceDefinitionQueryService.notDeletedPredicate(root, cb),
            root.get(FieldNameConstants.CATEGORY_CODE).in(categoryCodes));
        List<DeviceDefinition> rows = this.repository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (DeviceDefinition row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 按医保等级批量解析主键（库存明细 chrgitm_lv 筛选）。
     *
     * @param chrgitmLvList 医保等级集合
     * @return 未删除且等级命中的主键列表
     */
    public List<Long> listIdsByChrgitmLvIn(Collection<Integer> chrgitmLvList) {
        if (chrgitmLvList == null || chrgitmLvList.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<DeviceDefinition> specification = (root, criteriaQuery, cb) -> cb.and(
            DeviceDefinitionQueryService.notDeletedPredicate(root, cb),
            root.get("chrgitmLv").in(chrgitmLvList));
        List<DeviceDefinition> rows = this.repository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (DeviceDefinition row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 批量查询库内已存在的耗材唯一键 token（同机构、未删除、未废弃）。
     *
     * <p>token 格式：{@code trim(name) + '\\u0001' + trim(totalVolume) + '\\u0001' + manufacturerId字符串}。</p>
     *
     * @param orgId  机构主键
     * @param tokens 待比对 token 集合
     * @return 库内已存在的 token 子集；入参无效时返回空集
     */
    public Set<String> findExistingDuplicateKeyTokens(Long orgId, Collection<String> tokens) {
        if (orgId == null || tokens == null || tokens.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> tokenSet = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (String token : tokens) {
            if (StringUtils.isEmpty(token)) {
                continue;
            }
            tokenSet.add(token.trim());
            String[] parts = token.split("\u0001", -1);
            if (parts.length > 0 && StringUtils.isNotEmpty(parts[0])) {
                names.add(parts[0].trim());
            }
        }
        if (tokenSet.isEmpty() || names.isEmpty()) {
            return Collections.emptySet();
        }
        Specification<DeviceDefinition> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(DeviceDefinitionQueryService.notDeletedPredicate(root, cb));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORG_ID), orgId));
            predicates.add(cb.notEqual(
                root.get(FieldNameConstants.STATUS_ENUM),
                PublicationStatus.DISCARDED.getCode()));
            predicates.add(root.get(FieldNameConstants.NAME).in(names));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<DeviceDefinition> candidates = repository.findAll(specification);
        Set<String> existing = new HashSet<>();
        for (DeviceDefinition row : candidates) {
            if (row == null) {
                continue;
            }
            String candidateToken = this.buildDuplicateKeyToken(
                row.getName(), row.getTotalVolume(), row.getManufacturerId());
            if (tokenSet.contains(candidateToken)) {
                existing.add(candidateToken);
            }
        }
        return existing;
    }

    /**
     * 构造与导入校验一致的唯一键 token（厂家段为 manufacturerId 字符串）。
     *
     * @param name           耗材名称
     * @param totalVolume    包装规格
     * @param manufacturerId 生产厂家主键；null 时厂家段为空串
     * @return token 字符串
     */
    private String buildDuplicateKeyToken(String name, String totalVolume, Long manufacturerId) {
        String n = "";
        if (name != null) {
            n = name.trim();
        }
        String v = "";
        if (totalVolume != null) {
            v = totalVolume.trim();
        }
        String m = this.manufacturerIdToken(manufacturerId);
        return n + '\u0001' + v + '\u0001' + m;
    }

    /**
     * 将生产厂家主键转为重复键中的厂家段（null→空串）。
     *
     * @param manufacturerId 生产厂家主键
     * @return 非 null 字符串
     */
    private String manufacturerIdToken(Long manufacturerId) {
        if (manufacturerId == null) {
            return "";
        }
        return String.valueOf(manufacturerId);
    }

    /**
     * 追加生产厂家筛选谓词：优先主键/主键集合；兼容旧 manufacturer 字符串仅当可解析为 Long。
     *
     * @param predicates       外层 AND 列表（就地追加）
     * @param root             耗材定义根
     * @param cb               CriteriaBuilder
     * @param manufacturerId   单值主键；可空
     * @param manufacturerIds  主键集合；可空
     * @param manufacturerText 兼容旧字符串入参；可空
     */
    private void appendManufacturerIdPredicates(
        List<Predicate> predicates,
        Root<DeviceDefinition> root,
        CriteriaBuilder cb,
        Long manufacturerId,
        List<Long> manufacturerIds,
        String manufacturerText
    ) {
        List<Long> ids = new ArrayList<>();
        if (manufacturerId != null) {
            ids.add(manufacturerId);
        }
        if (manufacturerIds != null) {
            for (Long id : manufacturerIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        // 旧入参仅传名称串：仅数字可当 id；名称解析留 AppService
        if (ids.isEmpty() && StringUtils.isNotEmpty(manufacturerText)) {
            Long parsed = this.tryParseLongId(manufacturerText.trim());
            if (parsed != null) {
                ids.add(parsed);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        if (ids.size() == 1) {
            predicates.add(cb.equal(root.get(FieldNameConstants.MANUFACTURER_ID), ids.get(0)));
            return;
        }
        predicates.add(root.get(FieldNameConstants.MANUFACTURER_ID).in(ids));
    }

    /**
     * 尝试将纯数字字符串解析为 Long；非数字或溢出时返回 null。
     *
     * @param raw 已 trim 的候选串
     * @return 解析成功的主键，或 null
     */
    private Long tryParseLongId(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        for (int i = 0; i < raw.length(); i++) {
            if (!Character.isDigit(raw.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 按库存明细关键字解析耗材定义主键（编码、商品名、拼音/五笔等，与采购选品口径一致）。
     *
     * @param keyword 检索词；空白时返回空列表
     * @return 未删除且命中关键字的耗材定义主键列表
     */
    public List<Long> listIdsByInventoryDetailKeyword(String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return Collections.emptyList();
        }
        Specification<DeviceDefinition> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> ands = new ArrayList<>();
            ands.add(DeviceDefinitionQueryService.notDeletedPredicate(root, cb));
            Predicate keywordPred = this.buildPurchaseKeywordPredicate(root, cb, keyword);
            if (keywordPred != null) {
                ands.add(keywordPred);
            }
            return cb.and(ands.toArray(Predicate[]::new));
        };
        List<DeviceDefinition> rows = this.repository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (DeviceDefinition row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }
}
