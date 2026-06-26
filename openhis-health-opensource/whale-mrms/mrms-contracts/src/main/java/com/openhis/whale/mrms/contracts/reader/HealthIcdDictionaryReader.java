/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.contracts.reader;

import com.openhis.whale.mrms.contracts.snapshot.IcdItemSnapshot;

import java.util.List;

/**
 * ICD 字典检索 Port。
 */
public interface HealthIcdDictionaryReader {

    /**
     * ICD-10 检索。
     *
     * @param keyword 关键字
     * @param limit 最大条数
     * @return 匹配项
     */
    List<IcdItemSnapshot> searchIcd10(String keyword, int limit);

    /**
     * ICD-9-CM-3 检索。
     *
     * @param keyword 关键字
     * @param limit 最大条数
     * @return 匹配项
     */
    List<IcdItemSnapshot> searchIcd9Cm3(String keyword, int limit);
}
