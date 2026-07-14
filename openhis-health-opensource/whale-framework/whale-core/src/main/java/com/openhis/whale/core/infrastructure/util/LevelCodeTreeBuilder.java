/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.util;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 通用层级编码树形结构构建工具 适配基于"层级编码（如 1、1.1、1.1.1）"的树形结构构建场景
 * <p>
 * 该工具类支持将基于层级编码的扁平数据结构转换为树形结构，适用于组织机构、菜单等场景
 *
 * @param <T> 源数据类型（如 Organization）
 * @param <V> 树形节点类型（如 OrganizationTree）
 * @author OpenHIS
 * @since 1.0.0
 */
public class LevelCodeTreeBuilder<T, V> {

    // ========== 完整修正后的递归方法 + 核心方法（补充获取树形节点编码的参数） ==========
    /**
     * 构建树形结构（最终完整通用版，补充树形节点编码获取）
     * <p>
     * 相比基础版本，该方法增加了对树形节点层级编码的获取支持，实现完全的泛型化
     *
     * @param allData 所有扁平源数据，不能为 null
     * @param getSourceLevelCodeFunc 获取源数据的层级编码的函数（如 Organization::getBusNo）
     * @param getNodeLevelCodeFunc 获取树形节点的层级编码的函数（如 OrganizationTree::getBusNo）
     * @param convertFunc 源数据转换为树形节点的函数（方法引用）
     * @param setChildrenFunc 为树形节点设置子节点的函数（如 OrganizationTree::setChildren）
     * @param isRootFunc 根节点判断规则（可自定义，例如层级编码长度为1）
     * @return 根节点列表，包含完整的树形结构
     * @throws NullPointerException 如果任何参数为 null
     */
    public List<V> buildTree(List<T> allData, Function<T, String> getSourceLevelCodeFunc,
        Function<V, String> getNodeLevelCodeFunc, Function<T, V> convertFunc, BiConsumer<V, List<V>> setChildrenFunc,
        Predicate<T> isRootFunc) {
        List<V> rootList = allData.stream().filter(isRootFunc).map(convertFunc).collect(Collectors.toList());

        for (V root : rootList) {
            buildChildren(root, allData, getSourceLevelCodeFunc, getNodeLevelCodeFunc, convertFunc, setChildrenFunc);
        }
        return rootList;
    }

    /**
     * 构建树形结构（使用默认根节点规则）
     * <p>
     * 使用默认的根节点判断规则，将层级编码不包含"."的节点视为根节点
     *
     * @param allData 所有扁平源数据，不能为 null
     * @param getSourceLevelCodeFunc 获取源数据的层级编码的函数
     * @param getNodeLevelCodeFunc 获取树形节点的层级编码的函数
     * @param convertFunc 源数据转换为树形节点的函数
     * @param setChildrenFunc 为树形节点设置子节点的函数
     * @return 根节点列表，包含完整的树形结构
     * @see #buildTree(List, Function, Function, Function, BiConsumer, Predicate)
     */
    public List<V> buildTree(List<T> allData, Function<T, String> getSourceLevelCodeFunc,
        Function<V, String> getNodeLevelCodeFunc, Function<T, V> convertFunc, BiConsumer<V, List<V>> setChildrenFunc) {
        Predicate<T> defaultIsRoot = t -> getSourceLevelCodeFunc.apply(t).split("\\.").length == 1;
        return buildTree(allData, getSourceLevelCodeFunc, getNodeLevelCodeFunc, convertFunc, setChildrenFunc,
            defaultIsRoot);
    }

    /**
     * 递归构建子节点
     * <p>
     * 递归查找并构建当前节点的所有直接子节点，确保只匹配下一层级的子节点
     *
     * @param parentNode 父树形节点
     * @param allData 所有扁平源数据
     * @param getSourceLevelCodeFunc 获取源数据层级编码的函数
     * @param getNodeLevelCodeFunc 获取树形节点层级编码的函数
     * @param convertFunc 源数据转树形节点的函数
     * @param setChildrenFunc 设置子节点的函数
     */
    private void buildChildren(V parentNode, List<T> allData, Function<T, String> getSourceLevelCodeFunc,
        Function<V, String> getNodeLevelCodeFunc, Function<T, V> convertFunc, BiConsumer<V, List<V>> setChildrenFunc) {
        // 获取父节点的层级编码
        String parentCode = getNodeLevelCodeFunc.apply(parentNode);
        if (parentCode == null) {
            return;
        }
        int parentLevel = parentCode.split("\\.").length;

        // 筛选当前父节点的直接子节点
        List<V> childNodes = allData.stream().filter(data -> {
            String dataCode = getSourceLevelCodeFunc.apply(data);
            // 条件1：子节点编码以父节点编码 + "." 开头（如父是1，子是1.1）
            boolean isChild = dataCode.startsWith(parentCode + ".");
            // 条件2：子节点层级 = 父节点层级 + 1（避免跨级匹配，如父是1，不匹配1.1.1）
            int dataLevel = dataCode.split("\\.").length;
            boolean isDirectChild = dataLevel == parentLevel + 1;
            return isChild && isDirectChild;
        }).map(convertFunc).collect(Collectors.toList());

        // 为父节点设置子节点
        setChildrenFunc.accept(parentNode, childNodes);

        // 递归为每个子节点构建下一级子节点
        for (V child : childNodes) {
            buildChildren(child, allData, getSourceLevelCodeFunc, getNodeLevelCodeFunc, convertFunc, setChildrenFunc);
        }
    }
}
