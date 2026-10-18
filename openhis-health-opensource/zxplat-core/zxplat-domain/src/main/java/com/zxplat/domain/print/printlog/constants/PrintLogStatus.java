/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.print.printlog.constants;

/**
 * 打印审计日志状态（存库大写 VARCHAR）。
 */
public final class PrintLogStatus {

    /** 打印成功并已落审计日志。 */
    public static final String SUCCESS = "SUCCESS";
    /** 打印失败（客户端或渲染异常）。 */
    public static final String FAILED = "FAILED";

    private PrintLogStatus() {
    }
}
