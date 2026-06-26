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

package com.openhis.health.module.common.assembler.option;

import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureTreeQueryModel;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.dto.OptionDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 身体部位主数据与 {@link OptionDto} 树形下拉的只读装配器。
 *
 * <p>供医生站申请单、基础数据维护等场景复用；停用节点不可新引用，但其启用子节点可上提展示。</p>
 */
public final class BodyStructureOptionAssembler {

    /**
     * 禁止实例化：仅提供静态装配方法。
     */
    private BodyStructureOptionAssembler() {
    }

    /**
     * 将领域树模型转为仅含启用态节点的 OptionDto 树。
     *
     * @param roots 部位树根节点列表
     * @return 可供新申请引用的树形下拉；无数据时返回空列表
     */
    public static List<OptionDto> toActiveOptionTree(List<BodyStructureTreeQueryModel> roots) {
        if (roots == null || roots.isEmpty()) {
            return Collections.emptyList();
        }
        SortCursor sortCursor = new SortCursor();
        List<OptionDto> options = new ArrayList<>();
        // Loop：按根节点顺序递归装配，保持 busNo 既有次序
        for (BodyStructureTreeQueryModel root : roots) {
            options.addAll(mapActiveSubtree(root, sortCursor));
        }
        return options;
    }

    /**
     * 递归装配单节点：启用节点保留层级，停用节点剔除并上提其启用后代。
     *
     * @param node 当前树节点
     * @param sortCursor 全局排序游标
     * @return 当前层可展示的下拉项
     */
    private static List<OptionDto> mapActiveSubtree(BodyStructureTreeQueryModel node, SortCursor sortCursor) {
        List<OptionDto> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        List<OptionDto> childOptions = mapActiveChildren(node.getChildren(), sortCursor);
        if (isActiveStatus(node.getStatusEnum())) {
            String value = null;
            if (node.getId() != null) {
                value = String.valueOf(node.getId());
            }
            long sort = sortCursor.next();
            result.add(new OptionDto(sort, value, node.getName(), node.getBusNo(), childOptions));
            return result;
        }
        // 停用节点不可新引用，但其启用子节点仍应出现在下拉中
        result.addAll(childOptions);
        return result;
    }

    /**
     * 批量装配子节点列表。
     *
     * @param children 子树节点
     * @param sortCursor 全局排序游标
     * @return 子层下拉项
     */
    private static List<OptionDto> mapActiveChildren(
        List<BodyStructureTreeQueryModel> children,
        SortCursor sortCursor) {
        List<OptionDto> result = new ArrayList<>();
        if (children == null || children.isEmpty()) {
            return result;
        }
        for (BodyStructureTreeQueryModel child : children) {
            result.addAll(mapActiveSubtree(child, sortCursor));
        }
        return result;
    }

    /**
     * 判断部位是否处于可被新业务引用的启用态。
     *
     * @param statusEnum 持久化状态码
     * @return 启用态返回 true
     */
    private static boolean isActiveStatus(String statusEnum) {
        return PublicationStatus.ACTIVE.getCode().equals(statusEnum);
    }

    /**
     * 树形下拉排序游标，按深度优先遍历递增。
     */
    private static final class SortCursor {

        /** 当前已分配的最大排序值。 */
        private long value;

        /**
         * 取下一个排序值。
         *
         * @return 递增后的排序值
         */
        private long next() {
            value = value + 1L;
            return value;
        }
    }
}
