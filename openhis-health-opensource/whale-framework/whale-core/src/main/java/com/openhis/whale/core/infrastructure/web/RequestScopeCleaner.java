/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.web;

/**
 * 请求结束时清理 ThreadLocal 等请求级状态，避免线程池复用串数据。
 */
public interface RequestScopeCleaner {

    void clearRequestScope();
}
