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

package com.openhis.health.domain.administration.devicedefinition.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionPurchasePageQuery;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.repository.DeviceDefinitionRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 器材定义读侧查询服务。
 *
 * <p>职责：提供器材定义的只读查询能力，包括分页浏览、关键词检索、详情加载等。</p>
 *
 * <p>边界：只读，不写库；联查与报表走读模型，不在本类展开复杂联表。</p>
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
     * <p>关键字同时匹配名称、拼音码、五笔码、业务编码、医保编码、医药机构目录编码（OR）。</p>
     *
     * @param pageNum      页码（0-based）
     * @param pageSize     每页大小
     * @param searchKey    综合关键字，空白则不作为条件
     * @param categoryCode 分类编码，空白则不作为条件
     * @param typeCode     耗材种类编码（{@code device_type_code}），空白则不作为条件
     * @return 分页结果
     */
    public Page<DeviceDefinition> pageDeviceDefinition(
        int pageNum, int pageSize, String searchKey, String categoryCode, String typeCode) {
        String sk = trimToEmpty(searchKey);
        String cc = trimToEmpty(categoryCode);
        String tc = trimToEmpty(typeCode);
        if (sk.isEmpty() && cc.isEmpty() && tc.isEmpty()) {
            Specification<DeviceDefinition> catalogPage = (root, query, cb) ->
                DeviceDefinitionQueryService.notDeletedPredicate(root, cb);
            return repository.findAll(catalogPage, PageRequest.of(pageNum, pageSize));
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
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.YB_NO)), likeIc));
                ors.add(cb.like(cb.lower(root.get(FieldNameConstants.YB_ORG_NO)), likeIc));
                ands.add(cb.or(ors.toArray(Predicate[]::new)));
            }
            return cb.and(ands.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, PageRequest.of(pageNum, pageSize));
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
     * <p>关键词同时匹配名称、拼音首码、五笔首码；返回启用（{@link PublicationStatus#ACTIVE}）记录。</p>
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
                    cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
                ));
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
        return repository.findAll(this.buildPurchasePageSpecification(query), pageable);
    }

    /** 医嘱开立目录分页（读耗材主表）。 */
    public Page<DeviceDefinition> pageForCatalog(ProductDefinitionCatalogPageQuery query, Pageable pageable) {
        ProductDefinitionPurchasePageQuery purchase = new ProductDefinitionPurchasePageQuery();
        if (query != null) {
            purchase.setCategoryCode(query.getCategoryCode());
            purchase.setActiveFlag(query.getActiveFlag());
            purchase.setSupplierId(query.getSupplierId());
            purchase.setManufacturer(query.getManufacturer());
            purchase.setKeyword(query.getKeyword());
            purchase.setPrepickedProductKeys(query.getPrepickedProductKeys());
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

    private Specification<DeviceDefinition> buildPurchasePageSpecification(ProductDefinitionPurchasePageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> ands = new ArrayList<>();
            ands.add(DeviceDefinitionQueryService.notDeletedPredicate(root, cb));
            if (query != null) {
                if (StringUtils.isNotBlank(query.getCategoryCode())) {
                    ands.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), query.getCategoryCode().trim()));
                }
                if (query.getActiveFlag() != null) {
                    ands.add(cb.equal(root.get(FieldNameConstants.ACTIVE_FLAG), query.getActiveFlag()));
                }
                if (query.getSupplierId() != null) {
                    ands.add(cb.equal(root.get(FieldNameConstants.SUPPLY_ID), query.getSupplierId()));
                }
                if (StringUtils.isNotBlank(query.getManufacturer())) {
                    ands.add(cb.equal(root.get(FieldNameConstants.MANUFACTURER_TEXT), query.getManufacturer().trim()));
                }
                Predicate keywordPred = this.buildPurchaseKeywordPredicate(root, cb, query.getKeyword());
                Predicate prepPred = this.buildDevicePrepickedPredicate(root, cb, query.getPrepickedProductKeys());
                this.appendKeywordOrPrepicked(ands, cb, keywordPred, prepPred);
            }
            return cb.and(ands.toArray(Predicate[]::new));
        };
    }

    private Predicate buildPurchaseKeywordPredicate(Root<DeviceDefinition> root, CriteriaBuilder cb, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return null;
        }
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(keyword.trim());
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
            if (StringUtils.isNotBlank(lookup.getProductTableName())
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
     * 按库存明细关键字解析耗材定义主键（编码、商品名、拼音/五笔等，与采购选品口径一致）。
     *
     * @param keyword 检索词；空白时返回空列表
     * @return 未删除且命中关键字的耗材定义主键列表
     */
    public List<Long> listIdsByInventoryDetailKeyword(String keyword) {
        if (StringUtils.isBlank(keyword)) {
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
