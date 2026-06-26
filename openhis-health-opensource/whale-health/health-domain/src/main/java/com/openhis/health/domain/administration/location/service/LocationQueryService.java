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

package com.openhis.health.domain.administration.location.service;

import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.repository.LocationRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationOperational;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.enums.SupplyRequestType;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 位置（院区/科室/病区/床位等）读侧查询服务。
 *
 * <p>职责：按机构与位置形态查询病区、药房、药库及床位级空闲统计，供住院与药品业务选点使用。</p>
 *
 * <p>边界：只读；床位空闲统计在内存遍历子床位，规模受单病区床位数约束。</p>
 */
@Service
@RequiredArgsConstructor
public class LocationQueryService {

    private final LocationRepository locationRepository;

    /**
     * 按机构列出启用中的病区位置。
     *
     * @param organizationId 机构（科室）标识
     * @return 病区 {@link Location} 列表
     */
    public List<Location> listWardsByOrganization(Long organizationId) {
        return locationRepository.findAllByOrganizationIdAndFormEnumAndStatusEnum(
            organizationId, LocationForm.WARD.getCode(), LocationStatus.ACTIVE.getCode());
    }

    /**
     * 按物理形态查询全部位置（维护页下拉等，条件在仓储层过滤）。
     *
     * @param formCode 物理形式 code
     * @return 位置列表；{@code formCode} 为空时返回空列表
     */
    public List<Location> listAllByFormCode(String formCode) {
        if (StringUtils.isBlank(formCode)) {
            return List.of();
        }
        return locationRepository.findAllByFormEnum(formCode.trim());
    }

    /**
     * 按机构与物理形态查询位置（含草稿/停用，供病区床位维护列表）。
     *
     * @param organizationId 机构主键
     * @param formCode         物理形式 code
     * @return 位置列表
     */
    public List<Location> listByOrganizationIdAndFormCode(Long organizationId, String formCode) {
        if (organizationId == null || StringUtils.isBlank(formCode)) {
            return List.of();
        }
        return locationRepository.findAllByOrganizationIdAndFormEnum(organizationId, formCode.trim());
    }

    /**
     * 查询病区下的病房节点（仅 {@link LocationForm#HOUSE}）。
     *
     * @param wardId 病区位置主键
     * @return 病房位置列表
     */
    public List<Location> listRoomsUnderWard(Long wardId) {
        if (wardId == null) {
            return List.of();
        }
        return locationRepository.findAllByParentIdAndFormEnum(
            wardId, LocationForm.HOUSE.getCode());
    }

    /**
     * 查询病区下启用中的病房节点（护士站左侧树子节点，对齐 **AS-IP-NUR-001** IF-IP-NUR-01）。
     *
     * @param wardId 病区位置主键
     * @return 启用中的病房列表，按 {@code display_order}、主键排序
     */
    public List<Location> listHousesUnderWard(Long wardId) {
        if (wardId == null) {
            return List.of();
        }
        List<Location> houses = locationRepository.findAllByParentIdAndFormEnumAndStatusEnum(
            wardId, LocationForm.HOUSE.getCode(), LocationStatus.ACTIVE.getCode());
        return this.sortByDisplayOrder(houses);
    }

    /**
     * 住院发药关键字：按床位名称模糊匹配启用态床位 location 主键。
     *
     * @param keyword 用户关键字；空白时返回空列表
     * @return 床位 location 主键
     */
    public List<Long> listActiveBedIdsByNameKeyword(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return List.of();
        }
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(keyword.trim());
        Specification<Location> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.FORM_ENUM), LocationForm.BED.getCode()),
            cb.equal(root.get(FieldNameConstants.STATUS_ENUM), LocationStatus.ACTIVE.getCode()),
            cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc));
        List<Location> rows = this.locationRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (Location row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 列出病区下全部启用态床位 location 主键（经 HOUSE 子节点）。
     *
     * @param wardId 病区主键
     * @return 床位 location 主键；{@code wardId} 为空时返回空列表
     */
    public List<Long> listActiveBedIdsUnderWard(Long wardId) {
        if (wardId == null) {
            return List.of();
        }
        List<Location> houses = this.listHousesUnderWard(wardId);
        if (houses.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> bedIds = new LinkedHashSet<>();
        for (Location house : houses) {
            if (house == null || house.getId() == null) {
                continue;
            }
            List<Location> beds = this.locationRepository.findAllByParentIdAndFormEnumAndStatusEnum(
                house.getId(), LocationForm.BED.getCode(), LocationStatus.ACTIVE.getCode());
            for (Location bed : beds) {
                if (bed != null && bed.getId() != null) {
                    bedIds.add(bed.getId());
                }
            }
        }
        return new ArrayList<>(bedIds);
    }

    /**
     * 按主键批量解析 location 物理形态 code（岗位管辖范围分类用）。
     *
     * @param locationIds location 主键
     * @return id → formEnum code
     */
    public Map<Long, String> mapFormEnumCodeByIds(Collection<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : locationIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (Location row : this.locationRepository.findAllById(distinct)) {
            if (row == null || row.getId() == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            result.put(row.getId(), row.getFormEnum());
        }
        return result;
    }

    /**
     * 按主键批量加载启用态病区。
     *
     * @param wardIds 病区位置主键
     * @return 病区列表，永不为 {@code null}
     */
    public List<Location> findActiveWardsByIds(Collection<Long> wardIds) {
        if (wardIds == null || wardIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long wardId : wardIds) {
            if (wardId != null) {
                distinctIds.add(wardId);
            }
        }
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        List<Location> wards = locationRepository.findAllByIdInAndFormEnumAndStatusEnum(
            distinctIds, LocationForm.WARD.getCode(), LocationStatus.ACTIVE.getCode());
        return this.sortByDisplayOrder(wards);
    }

    /**
     * 按主键批量加载启用态药库（{@link LocationForm#WAREHOUSE}）。
     *
     * @param locationIds {@code adm_location.id} 集合
     * @return 药库位置列表，永不为 {@code null}
     */
    public List<Location> findActiveWarehousesByIds(Collection<Long> locationIds) {
        return this.findActiveLocationsByIdsAndForm(locationIds, LocationForm.WAREHOUSE.getCode());
    }

    /**
     * 按主键批量加载启用态药房（{@link LocationForm#PHARMACY}）。
     *
     * @param locationIds {@code adm_location.id} 集合
     * @return 药房位置列表，永不为 {@code null}
     */
    public List<Location> findActivePharmaciesByIds(Collection<Long> locationIds) {
        return this.findActiveLocationsByIdsAndForm(locationIds, LocationForm.PHARMACY.getCode());
    }

    /**
     * 列出全部启用中的病区（非严格模式 fallback 口径）。
     *
     * @return 病区列表
     */
    public List<Location> listAllActiveWards() {
        List<Location> wards = locationRepository.findAllByFormEnumAndStatusEnum(
            LocationForm.WARD.getCode(), LocationStatus.ACTIVE.getCode());
        return this.sortByDisplayOrder(wards);
    }

    /**
     * 查询指定父位置下的床位节点。
     *
     * @param parentLocationId 父位置主键（通常为病房）
     * @return 床位位置列表
     */
    public List<Location> listBedsUnderParent(Long parentLocationId) {
        if (parentLocationId == null) {
            return List.of();
        }
        return locationRepository.findAllByParentIdAndFormEnum(
            parentLocationId, LocationForm.BED.getCode());
    }

    /**
     * 统计某病区下「床位形态、未占用、仍启用」的床位数量（DM §6.2.2：{@code operational_enum} 非 {@code IN_USE}）。
     *
     * @param wardId 病区位置主键
     * @return 未占用床位数；病区 ID 为空时返回 {@code 0}
     */
    public Long countBedsByWard(Long wardId) {
        return this.countBedsByWard(wardId, true);
    }

    /**
     * 统计病区下床位数量。
     *
     * @param wardId 病区位置主键
     * @param vacantOnly     {@code true} 仅计未占用床（非 {@link LocationOperational#IN_USE}）；
     *                       {@code false} 计全部启用床位（总床位数）
     * @return 床位数；病区 ID 为空时返回 {@code 0}
     */
    public Long countBedsByWard(Long wardId, boolean vacantOnly) {
        if (wardId == null) {
            return 0L;
        }
        List<Location> rooms = locationRepository.findAllByParentIdAndFormEnum(
            wardId, LocationForm.HOUSE.getCode());
        long count = 0L;
        for (Location room : rooms) {
            if (room == null || Boolean.TRUE.equals(room.getIsDeleted())) {
                continue;
            }
            count += this.countBedsUnderParent(room.getId(), vacantOnly);
        }
        return count;
    }

    /**
     * 按父级 busNo 列出直接子位置（一层），用于病区→病房、病房→病床展开。
     *
     * @param parentBusNo 父级层级编码
     * @return 子位置列表
     */
    public List<Location> listChildrenByParentBusNo(String parentBusNo) {
        return this.listChildrenByParentBusNo(parentBusNo, null);
    }

    /**
     * 按父级 {@code bus_no} 列出直接子位置（仅一层），可选按物理形态过滤。
     *
     * @param parentBusNo 父级层级编码
     * @param formCode    物理形式 code；空则不过滤形态
     * @return 直接子节点列表，按 {@code display_order}、主键排序
     */
    public List<Location> listChildrenByParentBusNo(String parentBusNo, String formCode) {
        if (StringUtils.isBlank(parentBusNo)) {
            return List.of();
        }
        String prefix = parentBusNo.trim() + GeneralSharedConstants.POINT;
        List<Location> candidates = locationRepository.findAllByBusNoStartingWith(prefix);
        List<Location> children = new ArrayList<>();
        for (Location candidate : candidates) {
            if (candidate == null || Boolean.TRUE.equals(candidate.getIsDeleted())) {
                continue;
            }
            String busNo = candidate.getBusNo();
            if (busNo == null || !busNo.startsWith(prefix)) {
                continue;
            }
            String remainder = busNo.substring(prefix.length());
            if (remainder.contains(GeneralSharedConstants.POINT)) {
                continue;
            }
            if (StringUtils.isNotBlank(formCode) && !formCode.trim().equals(candidate.getFormEnum())) {
                continue;
            }
            children.add(candidate);
        }
        return this.sortByDisplayOrder(children);
    }

    /**
     * 按病区 {@code bus_no} 列出其下启用中的病房（{@code bus_no} 一层子节点 + 形态/状态过滤）。
     *
     * @param wardBusNo 病区层级编码
     * @return 病房列表
     */
    public List<Location> listActiveHousesByWardBusNo(String wardBusNo) {
        if (StringUtils.isBlank(wardBusNo)) {
            return List.of();
        }
        List<Location> directChildren =
            this.listChildrenByParentBusNo(wardBusNo.trim(), LocationForm.HOUSE.getCode());
        List<Location> activeHouses = new ArrayList<>();
        for (Location house : directChildren) {
            if (house == null) {
                continue;
            }
            if (LocationStatus.ACTIVE.getCode().equals(house.getStatusEnum())) {
                activeHouses.add(house);
            }
        }
        return this.sortByDisplayOrder(activeHouses);
    }

    /**
     * 统计某父位置下启用床位数量。
     *
     * @param parentLocationId 父位置主键（病区或病房）
     * @param vacantOnly       是否仅计空闲床
     * @return 床位数
     */
    private long countBedsUnderParent(Long parentLocationId, boolean vacantOnly) {
        if (parentLocationId == null) {
            return 0L;
        }
        List<Location> beds = locationRepository.findAllByParentId(parentLocationId);
        long count = 0L;
        for (Location bed : beds) {
            if (bed == null || Boolean.TRUE.equals(bed.getIsDeleted())) {
                continue;
            }
            if (!LocationForm.BED.getCode().equals(bed.getFormEnum())
                || !LocationStatus.ACTIVE.getCode().equals(bed.getStatusEnum())) {
                continue;
            }
            if (vacantOnly
                && LocationOperational.IN_USE.getCode().equals(bed.getOperationalEnum())) {
                continue;
            }
            count++;
        }
        return count;
    }

    /**
     * 统计病区总床位数（启用中的床位形态位置，含已占用）。
     *
     * @param wardId 病区位置主键
     * @return 总床位数
     */
    public Long countTotalBedsByWard(Long wardId) {
        return this.countBedsByWard(wardId, false);
    }

    /**
     * 按机构列出启用中的药房位置。
     *
     * @param organizationId 机构标识
     * @return 药房位置列表
     */
    public List<Location> listPharmaciesByOrganization(Long organizationId) {
        return locationRepository.findAllByOrganizationIdAndFormEnumAndStatusEnum(
            organizationId, LocationForm.PHARMACY.getCode(), LocationStatus.ACTIVE.getCode());
    }

    /**
     * 按机构列出启用中的药库（仓储）位置。
     *
     * @param organizationId 机构标识
     * @return 药库位置列表
     */
    public List<Location> listWarehousesByOrganization(Long organizationId) {
        return locationRepository.findAllByOrganizationIdAndFormEnumAndStatusEnum(
            organizationId, LocationForm.WAREHOUSE.getCode(), LocationStatus.ACTIVE.getCode());
    }

    /**
     * 列出全部启用中的药房位置（不按机构主键过滤；与 {@link #listPharmaciesByOrganization(Long)} 区分）。
     */
    public List<Location> listAllActivePharmacies() {
        return locationRepository.findAllByFormEnumAndStatusEnum(
            LocationForm.PHARMACY.getCode(), LocationStatus.ACTIVE.getCode());
    }

    /**
     * 列出全部启用中的药库位置（不按机构主键过滤；与 {@link #listWarehousesByOrganization(Long)} 区分）。
     */
    public List<Location> listAllActiveWarehouses() {
        return locationRepository.findAllByFormEnumAndStatusEnum(
            LocationForm.WAREHOUSE.getCode(), LocationStatus.ACTIVE.getCode());
    }

    /**
     * 列出全部库房与药房位置（含各启用/停用态，供基础维护页平铺列表）。
     *
     * @return 形态为仓库或药房的位置列表
     */
    public List<Location> listAllWarehouseAndPharmacyLocations() {
        List<Location> warehouses = locationRepository.findAllByFormEnum(LocationForm.WAREHOUSE.getCode());
        List<Location> pharmacies = locationRepository.findAllByFormEnum(LocationForm.PHARMACY.getCode());
        List<Location> combined = new ArrayList<>(warehouses.size() + pharmacies.size());
        combined.addAll(warehouses);
        combined.addAll(pharmacies);
        return combined;
    }

    /**
     * 按机构列出全部启用中的位置（各形态），用于岗位分配等选择物理点。
     *
     * @param organizationId 机构/科室标识；{@code null} 时返回空表
     */
    public List<Location> listActiveLocationsByOrganization(Long organizationId) {
        if (organizationId == null) {
            return List.of();
        }
        return locationRepository.findAllByOrganizationIdAndStatusEnum(
            organizationId, LocationStatus.ACTIVE.getCode());
    }

    /**
     * 按主键加载位置（读模型）；不存在或软删过滤无行时为空。
     */
    public Optional<Location> findOptionalById(Long locationId) {
        if (locationId == null) {
            return Optional.empty();
        }
        return locationRepository.findById(locationId);
    }

    /**
     * 按位置主键解析 {@link SupplyRequestType}，用于 {@code wkf_supply_request.source_type_enum} /
     * {@code purpose_type_enum} 等与供应单头一致的位置类型落库（B.10.8.1，持久化为枚举 {@code getCode()}）。
     *
     * <p>与 {@link #listLocationsByType(Long, int)} 的「形态 → 供应类型」口径对齐：
     * {@link LocationForm#PHARMACY}→{@link SupplyRequestType#PHARMACY}、{@link LocationForm#WAREHOUSE}→{@link SupplyRequestType#CABINET}、
     * {@link LocationForm#WARD}→{@link SupplyRequestType#DEPARTMENT}。床位等其它形态当前不参与库房供应链头，须拒绝以免误落库。</p>
     *
     * @param locationId 位置主键
     * @return 供应位置类型枚举，非 {@code null}
     * @throws BusinessException {@code locationId} 为空、位置不存在、形态为空或无法映射到 {@link SupplyRequestType}
     */
    public SupplyRequestType requireSupplyRequestTypeForLocationId(Long locationId) {
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_INVALID_ARGUMENT, "locationId 不能为空");
        }
        Optional<Location> row = locationRepository.findById(locationId);
        if (row.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_NOT_FOUND, "位置不存在: " + locationId);
        }
        Location location = row.get();
        String formCode = location.getFormEnum();
        if (StringUtils.isBlank(formCode)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_RULE_CONFLICT, "位置形态为空，无法解析供应类型");
        }
        if (LocationForm.PHARMACY.getCode().equals(formCode)) {
            return SupplyRequestType.PHARMACY;
        }
        if (LocationForm.WAREHOUSE.getCode().equals(formCode)) {
            return SupplyRequestType.CABINET;
        }
        if (LocationForm.WARD.getCode().equals(formCode)) {
            return SupplyRequestType.DEPARTMENT;
        }
        if (LocationForm.BED.getCode().equals(formCode)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_RULE_CONFLICT, "床位位置不能作为供应单源/目的类型");
        }
        throw new BusinessException(ErrorCodeConstants.LOCATION_RULE_CONFLICT, "不支持的位置形态，无法解析供应类型: " + formCode);
    }

    /**
     * 按机构与形态类型列出启用中的位置。
     * 用于供应请求模块按 source_type_enum / purpose_type_enum（对应 LocationForm）筛选下拉。
     *
     * <p>类型映射：SupplyRequestType.sortOrder → LocationForm.sortOrder。
     * 目前支持 DEPARTMENT(2)→WARD(4)、PHARMACY(16)→PHARMACY(16)。
     *
     * @param organizationId 机构标识
     * @param typeSortOrder 形态类型序号（来自 SupplyRequestType 或 LocationForm 的 sortOrder）
     * @return 符合条件的位置列表
     */
    public List<Location> listLocationsByType(Long organizationId, int typeSortOrder) {
        String formEnumCode = resolveFormEnumCode(typeSortOrder);
        if (formEnumCode == null) {
            return List.of();
        }
        return locationRepository.findAllByOrganizationIdAndFormEnumAndStatusEnum(
            organizationId, formEnumCode, LocationStatus.ACTIVE.getCode());
    }

    /**
     * 根据类型序号解析 LocationForm.code。
     * <ul>
     *   <li>2 (DEPARTMENT) → WARD</li>
     *   <li>16 (PHARMACY) → PHARMACY</li>
     *   <li>17 (WAREHOUSE/CABINET) → WAREHOUSE</li>
     * </ul>
     */
    /**
     * 根据类型序号解析 LocationForm.code。
     * <ul>
     *   <li>2 (DEPARTMENT) → WARD</li>
     *   <li>16 (PHARMACY) → PHARMACY</li>
     *   <li>17 (WAREHOUSE/CABINET) → WAREHOUSE</li>
     * </ul>
     *
     * @param typeSortOrder 类型序号
     * @return 位置形态编码；不支持的类型返回 null
     */
    private String resolveFormEnumCode(int typeSortOrder) {
        switch (typeSortOrder) {
            case 2:
                // DEPARTMENT(2) 映射到 WARD(4)
                return LocationForm.WARD.getCode();
            case 16:
                return LocationForm.PHARMACY.getCode();
            case 17:
                return LocationForm.WAREHOUSE.getCode();
            default:
                return null;
        }
    }

    /**
     * 按主键加载位置名称（用于岗位分配展示）；不存在或未删除过滤导致无行时返回 {@code null}。
     */
    public String findNameById(Long locationId) {
        return findOptionalById(locationId).map(Location::getName).orElse(null);
    }

    /**
     * 批量解析库位名称（医生站列表等场景避免 N+1）。
     *
     * @param locationIds 库位主键集合；{@code null} 或空时返回空映射
     * @return id → 名称；未命中或无名称时为 {@code null} 值
     */
    /**
     * 批量解析库位名称（医生站列表等场景避免 N+1）。
     *
     * @param locationIds 库位主键集合；{@code null} 或空时返回空映射
     * @return id → 名称；未命中或无名称时为 {@code null} 值
     */
    public Map<Long, String> mapNamesByIds(Collection<Long> locationIds) {
        Map<Long, Location> entities = this.loadLocationEntityMapByIds(locationIds);
        Map<Long, String> result = new HashMap<>(entities.size());
        for (Map.Entry<Long, Location> entry : entities.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue().getName());
            }
        }
        return result;
    }

    /**
     * 按主键批量加载位置实体（querysupport 住院床位/病区展示）。
     *
     * @param locationIds 位置主键集合
     * @return 位置 ID → 实体
     */
    public Map<Long, Location> loadLocationEntityMapByIds(Collection<Long> locationIds) {
        Map<Long, Location> result = new HashMap<>();
        if (locationIds == null || locationIds.isEmpty()) {
            return result;
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long id : locationIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        for (Location row : locationRepository.findAllById(distinct)) {
            if (row != null && row.getId() != null) {
                result.put(row.getId(), row);
            }
        }
        return result;
    }

    /**
     * 按主键批量解析位置名称（工作台列表等轻量展示）。
     *
     * @param locationIds 位置主键集合
     * @return 位置主键 → 名称
     */
    /**
     * 按展示顺序与主键稳定排序位置列表（护士站树、下拉）。
     *
     * @param locations 待排序列表
     * @return 新列表，不修改入参
     */
    private List<Location> findActiveLocationsByIdsAndForm(Collection<Long> locationIds, String formCode) {
        if (locationIds == null || locationIds.isEmpty() || formCode == null || formCode.isBlank()) {
            return List.of();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long locationId : locationIds) {
            if (locationId != null) {
                distinctIds.add(locationId);
            }
        }
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        List<Location> locations = locationRepository.findAllByIdInAndFormEnumAndStatusEnum(
            distinctIds, formCode.trim(), LocationStatus.ACTIVE.getCode());
        return this.sortByDisplayOrder(locations);
    }

    private List<Location> sortByDisplayOrder(List<Location> locations) {
        if (locations == null || locations.isEmpty()) {
            return List.of();
        }
        List<Location> sorted = new ArrayList<>(locations);
        sorted.sort(Comparator
            .comparing(Location::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Location::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
    }

    public Map<Long, String> findLocationNamesByIds(Collection<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> idSet = new LinkedHashSet<>();
        for (Long id : locationIds) {
            if (id != null) {
                idSet.add(id);
            }
        }
        if (idSet.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = new HashMap<>(idSet.size());
        for (Location loc : locationRepository.findAllById(idSet)) {
            out.put(loc.getId(), StringUtils.defaultString(loc.getName()));
        }
        return out;
    }
}
