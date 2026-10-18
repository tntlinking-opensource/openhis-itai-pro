/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.command;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

/**
 * 支持单条与批量双形态输入的命令基类。
 *
 * @param <T> 子命令类型
 */
public abstract class BatchCommand<T> {

    /**
     * 单条输入。
     */
    private T item;

    /**
     * 批量输入。
     */
    private List<T> items;

    /**
     * 获取单条输入。
     *
     * @return 单条输入
     */
    public T getItem() {
        return item;
    }

    /**
     * 设置单条输入。
     *
     * @param item 单条输入
     */
    public void setItem(T item) {
        this.item = item;
    }

    /**
     * 获取批量输入。
     *
     * @return 批量输入
     */
    public List<T> getItems() {
        return items;
    }

    /**
     * 设置批量输入。
     *
     * @param items 批量输入
     */
    public void setItems(List<T> items) {
        this.items = items;
    }

    /**
     * 解析输入集合。优先使用批量输入，其次使用单条输入。
     *
     * @return 标准化后的输入集合
     */
    public List<T> resolveItems() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        if (item != null) {
            List<T> normalized = new ArrayList<>();
            normalized.add(item);
            return normalized;
        }
        throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "入参 item/items 不能同时为空");
    }
}
