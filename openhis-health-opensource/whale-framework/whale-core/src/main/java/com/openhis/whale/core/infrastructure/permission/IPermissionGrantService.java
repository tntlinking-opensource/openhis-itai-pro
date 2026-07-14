/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.permission;

/**
 * 权限授予服务接口
 *
 * <p>用于打破 whale-identity 和 whale-permission 之间的循环依赖</p>
 *
 * @author Whale Framework
 */
public interface IPermissionGrantService {

    /**
     * 授予权限
     *
     * @param permissionName 权限名称
     * @param providerName 提供者名称（R=角色, U=用户, OU=组织单元）
     * @param providerKey 提供者键（角色ID、用户ID等）
     * @throws com.openhis.whale.core.application.exception.ValidationException 权限定义不存在
     */
    void grant(String permissionName, String providerName, String providerKey);

    /**
     * 撤销权限
     *
     * @param permissionName 权限名称
     * @param providerName 提供者名称
     * @param providerKey 提供者键
     * @throws com.openhis.whale.core.application.exception.BusinessException 如果权限授予不存在
     */
    void revoke(String permissionName, String providerName, String providerKey);

    /**
     * 判断指定提供者是否存在任意有效授予（未软删除）
     *
     * @param providerName 提供者名称（R=角色, U=用户, OU=组织单元）
     * @param providerKey 提供者键（角色ID、用户ID等）
     */
    boolean hasGrants(String providerName, String providerKey);

    /**
     * 获取指定提供者当前授予的权限名称列表（仅返回未软删除记录）
     *
     * @param providerName 提供者名称（R=角色, U=用户, OU=组织单元）
     * @param providerKey 提供者键（角色ID、用户ID等）
     */
    java.util.List<String> getGrantedPermissionNames(String providerName, String providerKey);

    /**
     * 仅保留在权限定义表（{@code whale_permissions}）中存在的权限名。
     * 菜单树中可能存在历史/目录节点 permissionName，授权前须过滤。
     */
    java.util.List<String> retainExistingPermissionDefinitions(java.util.List<String> permissionNames);

    /**
     * 撤销某权限名下的全部有效授予（菜单隐藏/停用/删除时级联清理角色与用户授权）。
     *
     * @param permissionName 权限名称
     */
    void revokeAllGrantsForPermission(String permissionName);
}
