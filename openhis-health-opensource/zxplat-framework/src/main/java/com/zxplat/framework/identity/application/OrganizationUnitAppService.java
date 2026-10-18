/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application;

import com.zxplat.framework.core.application.dto.BaseQueryRequest;
import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.PageRequestUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.framework.core.infrastructure.permission.RequirePermission;
import com.zxplat.framework.identity.application.dto.*;
import com.zxplat.framework.identity.domain.OrganizationUnit;
import com.zxplat.framework.identity.domain.UserOrganizationUnit;
import com.zxplat.framework.identity.infrastructure.repository.OrganizationUnitRepository;
import com.zxplat.framework.identity.infrastructure.repository.UserOrganizationUnitRepository;
import com.zxplat.framework.identity.infrastructure.repository.UserRepository;
import com.zxplat.framework.identity.domain.User;
import com.zxplat.framework.core.application.util.SpecificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织单元应用服务
 */
@Service
public class OrganizationUnitAppService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationUnitAppService.class);

    @Autowired
    private OrganizationUnitRepository organizationUnitRepository;

    @Autowired
    private UserOrganizationUnitRepository userOrganizationUnitRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 获取组织单元
     */
    @RequirePermission("Identity.OrganizationUnits.View")
    public OrganizationUnitDto getById(IdRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("组织单元ID不能为空");
        }
        OrganizationUnit ou = organizationUnitRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("组织单元", request.getId()));
        return toDto(ou);
    }

    /**
     * 获取组织单元列表（树形结构）
     */
    public List<OrganizationUnitDto> getTree() {
        Long tenantId = TenantUtils.getCurrentTenantId();

        // 获取根组织单元并过滤租户
        List<OrganizationUnit> rootOus = organizationUnitRepository.findByParentIdIsNullOrderBySortOrderAsc();

        // 租户过滤
        if (tenantId != null) {
            rootOus = rootOus.stream()
                    .filter(ou -> tenantId.equals(ou.getTenantId()))
                    .collect(Collectors.toList());
        } else {
            // 宿主用户只看tenantId为null的
            rootOus = rootOus.stream()
                    .filter(ou -> ou.getTenantId() == null)
                    .collect(Collectors.toList());
        }

        return rootOus.stream()
                .map(this::toDtoWithChildren)
                .collect(Collectors.toList());
    }

    /**
     * 获取组织单元列表（分页）
     */
    public PagedResult<OrganizationUnitDto> getList(BaseQueryRequest request) {
        Specification<OrganizationUnit> spec = buildSpecification(request);
        var pageRequest = PageRequestUtils.createDefault(
                request.getPageNo(),
                request.getPageSize()
        );

        Page<OrganizationUnit> page = organizationUnitRepository.findAll(spec, pageRequest);
        return PagedResult.fromPage(page.map(this::toDto));
    }

    /**
     * 创建组织单元（编码自动生成）
     */
    @RequirePermission("Identity.OrganizationUnits.Create")
    public OrganizationUnitDto create(CreateOrganizationUnitRequest request) {
        // 检查父组织单元
        if (request.getParentId() != null) {
            organizationUnitRepository.findById(request.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException("父组织单元", request.getParentId()));
        }

        OrganizationUnit ou = new OrganizationUnit();
        ou.setDisplayName(request.getDisplayName());
        ou.setParentId(request.getParentId());
        ou.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        ou.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true);
        ou.setRemark(request.getRemark());
        ou.setTenantId(TenantUtils.getCurrentTenantId());

        // 自动生成级联编码
        ou.setCode(generateCode(request.getParentId()));

        ou = organizationUnitRepository.save(ou);
        return toDto(ou);
    }

    /**
     * 生成级联编码
     */
    private String generateCode(Long parentId) {
        if (parentId == null) {
            // 根节点：001, 002, 003...
            List<OrganizationUnit> roots = organizationUnitRepository.findByParentIdIsNullOrderBySortOrderAsc();
            int nextNumber = roots.size() + 1;
            return String.format("%03d", nextNumber);
        } else {
            // 子节点：父编码.001
            OrganizationUnit parent = organizationUnitRepository.findById(parentId).orElseThrow();
            List<OrganizationUnit> siblings = organizationUnitRepository.findByParentIdOrderBySortOrderAsc(parentId);
            int nextNumber = siblings.size() + 1;
            return parent.getCode() + "." + String.format("%03d", nextNumber);
        }
    }

    /**
     * 更新组织单元
     */
    @RequirePermission("Identity.OrganizationUnits.Update")
    public OrganizationUnitDto update(UpdateOrganizationUnitRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("组织单元ID不能为空");
        }

        OrganizationUnit ou = organizationUnitRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("组织单元", request.getId()));

        // 检查父组织单元（不能将自己设为父）
        if (request.getParentId() != null && request.getParentId().equals(request.getId())) {
            throw new BusinessException("不能将自己设为父组织单元");
        }

        if (request.getParentId() != null) {
            organizationUnitRepository.findById(request.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException("父组织单元", request.getParentId()));
        }

        if (request.getDisplayName() != null) {
            ou.setDisplayName(request.getDisplayName());
        }
        if (request.getParentId() != null) {
            ou.setParentId(request.getParentId());
        }
        if (request.getSortOrder() != null) {
            ou.setSortOrder(request.getSortOrder());
        }
        if (request.getIsEnabled() != null) {
            ou.setIsEnabled(request.getIsEnabled());
        }
        if (request.getRemark() != null) {
            ou.setRemark(request.getRemark());
        }

        ou = organizationUnitRepository.save(ou);
        return toDto(ou);
    }

    /**
     * 删除组织单元（软删除）
     */
    @RequirePermission("Identity.OrganizationUnits.Delete")
    public void delete(IdRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("组织单元ID不能为空");
        }

        OrganizationUnit ou = organizationUnitRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("组织单元", request.getId()));

        // 检查是否有子组织单元
        List<OrganizationUnit> children = organizationUnitRepository.findByParentIdOrderBySortOrderAsc(request.getId());
        if (!children.isEmpty()) {
            throw new BusinessException("存在子组织单元，无法删除");
        }

        // 软删除
        ou.setIsDeleted(true);
        ou.setDeletedAt(java.time.LocalDateTime.now());
        organizationUnitRepository.save(ou);
    }

    /**
     * 获取组织单元的用户列表
     */
    public List<UserDto> getUsers(IdRequest request) {
        if (request.getId() == null) {
            throw new ValidationException("组织单元ID不能为空");
        }

        organizationUnitRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("组织单元", request.getId()));

        List<UserOrganizationUnit> userOus = userOrganizationUnitRepository.findByOrganizationUnitId(request.getId());

        // 获取用户ID列表
        List<Long> userIds = userOus.stream()
                .map(UserOrganizationUnit::getUserId)
                .collect(Collectors.toList());

        if (userIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询用户详情（避免懒加载问题）
        List<User> users = userRepository.findAllById(userIds);
        return users.stream()
                .map(user -> {
                    UserDto dto = new UserDto();
                    BeanUtils.copyProperties(user, dto);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 分配用户到组织单元
     */
    public void assignUsers(AssignUsersRequest request) {
        if (request.getOrganizationUnitId() == null) {
            throw new ValidationException("组织单元ID不能为空");
        }
        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            throw new ValidationException("用户ID列表不能为空");
        }

        organizationUnitRepository.findById(request.getOrganizationUnitId())
                .orElseThrow(() -> new EntityNotFoundException("组织单元", request.getOrganizationUnitId()));

        Long tenantId = TenantUtils.getCurrentTenantId();

        for (Long userId : request.getUserIds()) {
            if (!userOrganizationUnitRepository.existsByUserIdAndOrganizationUnitId(userId, request.getOrganizationUnitId())) {
                UserOrganizationUnit uou = new UserOrganizationUnit();
                uou.setUserId(userId);
                uou.setOrganizationUnitId(request.getOrganizationUnitId());
                uou.setTenantId(tenantId);
                userOrganizationUnitRepository.save(uou);
            }
        }
    }

    /**
     * 从组织单元移除用户
     */
    public void removeUsers(RemoveUsersRequest request) {
        if (request.getOrganizationUnitId() == null) {
            throw new ValidationException("组织单元ID不能为空");
        }
        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            throw new ValidationException("用户ID列表不能为空");
        }

        organizationUnitRepository.findById(request.getOrganizationUnitId())
                .orElseThrow(() -> new EntityNotFoundException("组织单元", request.getOrganizationUnitId()));

        for (Long userId : request.getUserIds()) {
            userOrganizationUnitRepository.deleteByUserIdAndOrganizationUnitId(userId, request.getOrganizationUnitId());
        }
    }

    /**
     * 将用户的可登录科室与给定 ZXPlat OU 主键列表对齐（全量替换，供人员岗位维护派生）。
     *
     * <p>集合已对齐时短路不写库；增删均走批量 I/O，避免登录选科室时逐条 delete/save。</p>
     *
     * @param userId 系统用户主键（{@code zxplat_users.id}）
     * @param organizationUnitIds ZXPlat {@code OrganizationUnit.id} 列表（可为空表示清空）；非 HIS 组织 id
     */
    public void syncOrganizationUnitsForUser(Long userId, List<Long> organizationUnitIds) {
        // Gate：用户主键必填且须存在，否则无法写入 zxplat_user_organization_units
        if (userId == null) {
            throw new ValidationException("用户ID不能为空");
        }
        userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("用户", userId));

        // Pre：目标 OU 去重；与现网绑定对比后决定是否写库
        List<Long> desiredIds;
        if (organizationUnitIds != null) {
            desiredIds = organizationUnitIds;
        } else {
            desiredIds = List.of();
        }
        Set<Long> desiredSet = new HashSet<>();
        for (Long orgUnitId : desiredIds) {
            if (orgUnitId != null) {
                desiredSet.add(orgUnitId);
            }
        }

        Long tenantId = TenantUtils.getCurrentTenantId();
        List<UserOrganizationUnit> existing = userOrganizationUnitRepository.findByUserIdAndTenantId(userId, tenantId);
        Set<Long> existingIds = new HashSet<>();
        for (UserOrganizationUnit row : existing) {
            if (row != null && row.getOrganizationUnitId() != null) {
                existingIds.add(row.getOrganizationUnitId());
            }
        }
        // 已与岗位/全院科室对齐：跳过写库，登录二次拉列表不再刷表
        if (existingIds.equals(desiredSet)) {
            return;
        }

        // Cmd：批量删除多余绑定
        List<UserOrganizationUnit> toDelete = new ArrayList<>();
        for (UserOrganizationUnit row : existing) {
            if (row == null || row.getOrganizationUnitId() == null) {
                continue;
            }
            if (!desiredSet.contains(row.getOrganizationUnitId())) {
                toDelete.add(row);
            }
        }
        if (!toDelete.isEmpty()) {
            userOrganizationUnitRepository.deleteAll(toDelete);
        }

        // Cmd：批量校验并插入缺失绑定；库中无对应 OU 时跳过（避免登录选科整页失败）
        Set<Long> toAdd = new HashSet<>(desiredSet);
        toAdd.removeAll(existingIds);
        if (toAdd.isEmpty()) {
            return;
        }
        List<OrganizationUnit> foundUnits = organizationUnitRepository.findAllById(toAdd);
        Set<Long> foundIds = new HashSet<>();
        for (OrganizationUnit unit : foundUnits) {
            if (unit != null && unit.getId() != null) {
                foundIds.add(unit.getId());
            }
        }
        Set<Long> missingIds = new HashSet<>();
        for (Long orgUnitId : toAdd) {
            if (!foundIds.contains(orgUnitId)) {
                missingIds.add(orgUnitId);
            }
        }
        // 岗位派生出的 ZXPlat OU 可能尚未建链或已软删：跳过缺失项，其余科室仍可登录
        if (!missingIds.isEmpty()) {
            log.warn("同步用户可登录科室时跳过不存在的组织单元: userId={}, missingOuIds={}", userId, missingIds);
            toAdd.removeAll(missingIds);
        }
        if (toAdd.isEmpty()) {
            return;
        }
        // 软删行仍占 uk_zxplat_uou_user_ou：优先恢复，禁止盲目 insert
        List<UserOrganizationUnit> toSave = new ArrayList<>(toAdd.size());
        for (Long orgUnitId : toAdd) {
            Optional<UserOrganizationUnit> includingDeleted =
                userOrganizationUnitRepository.findFirstByUserIdAndOrganizationUnitId(userId, orgUnitId);
            if (includingDeleted.isPresent()) {
                UserOrganizationUnit existingRow = includingDeleted.get();
                // 恢复软删绑定并回写租户
                existingRow.setIsDeleted(false);
                existingRow.setDeletedAt(null);
                existingRow.setDeletedBy(null);
                existingRow.setTenantId(tenantId);
                toSave.add(existingRow);
                continue;
            }
            UserOrganizationUnit uou = new UserOrganizationUnit();
            uou.setUserId(userId);
            uou.setOrganizationUnitId(orgUnitId);
            uou.setTenantId(tenantId);
            toSave.add(uou);
        }
        userOrganizationUnitRepository.saveAll(toSave);
    }

    /**
     * 按主键加载组织单元（不存在时为空）。
     *
     * @param id 组织单元主键
     * @return 组织单元或空
     */
    public Optional<OrganizationUnit> findOptionalById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return organizationUnitRepository.findById(id);
    }

    /**
     * 按编码加载组织单元（不存在时为空）。
     *
     * @param code 组织单元编码
     * @return 组织单元或空
     */
    public Optional<OrganizationUnit> findOptionalByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return organizationUnitRepository.findByCode(code.trim());
    }

    /**
     * 按编码加载组织单元（含已软删行），供导入/登录补链时恢复同 code OU。
     *
     * @param code 组织单元编码
     * @return 组织单元或空
     */
    public Optional<OrganizationUnit> findOptionalByCodeIncludingDeleted(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return organizationUnitRepository.findFirstByCode(code.trim());
    }

    /**
     * 按编码批量加载组织单元（登录科室 HIS→ZXPlat 桥接避免 N+1 findByCode）。
     *
     * @param codes OU code 集合；{@code null} 或空时返回空映射
     * @return code → 组织单元；空白 code 与未命中不写入
     */
    public Map<String, OrganizationUnit> mapByCodes(Collection<String> codes) {
        Map<String, OrganizationUnit> unitByCode = new HashMap<>();
        if (codes == null || codes.isEmpty()) {
            return unitByCode;
        }
        Set<String> distinctCodes = new HashSet<>();
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            distinctCodes.add(code.trim());
        }
        if (distinctCodes.isEmpty()) {
            return unitByCode;
        }
        for (OrganizationUnit unit : organizationUnitRepository.findByCodeIn(distinctCodes)) {
            if (unit == null || unit.getCode() == null || unit.getCode().isBlank()) {
                continue;
            }
            unitByCode.put(unit.getCode().trim(), unit);
        }
        return unitByCode;
    }

    /**
     * 持久化组织单元（新建或更新）。
     */
    public OrganizationUnit persistOrganizationUnit(OrganizationUnit organizationUnit) {
        if (organizationUnit == null) {
            return null;
        }
        return organizationUnitRepository.save(organizationUnit);
    }

    private Specification<OrganizationUnit> buildSpecification(BaseQueryRequest request) {
        return SpecificationUtils.<OrganizationUnit>tenantFilter()
            .and(SpecificationUtils.<OrganizationUnit>notDeleted())
            .and((root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();

                // 关键字搜索
                if (request.getSearchKey() != null && !request.getSearchKey().trim().isEmpty()) {
                    String pattern = "%" + request.getSearchKey().trim() + "%";
                    predicates.add(cb.or(
                            cb.like(root.get("code"), pattern),
                            cb.like(root.get("displayName"), pattern)
                    ));
                }

                return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
            });
    }

    private OrganizationUnitDto toDto(OrganizationUnit ou) {
        OrganizationUnitDto dto = new OrganizationUnitDto();
        BeanUtils.copyProperties(ou, dto);
        // 不访问懒加载的 parent 关联，避免 "no session" 错误
        // 如需 parentDisplayName，可以在前端通过 parentId 查找
        return dto;
    }

    private OrganizationUnitDto toDtoWithChildren(OrganizationUnit ou) {
        OrganizationUnitDto dto = toDto(ou);
        List<OrganizationUnit> children = organizationUnitRepository.findByParentIdOrderBySortOrderAsc(ou.getId());
        dto.setChildren(children.stream()
                .map(this::toDtoWithChildren)
                .collect(Collectors.toList()));
        return dto;
    }
}
