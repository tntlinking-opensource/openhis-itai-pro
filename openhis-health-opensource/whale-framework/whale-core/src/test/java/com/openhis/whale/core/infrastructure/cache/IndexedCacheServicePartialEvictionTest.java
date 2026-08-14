/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexedCacheServicePartialEvictionTest {

    @Test
    void getAll_returnsEmptyWhenItemMissingButIndexRemains() throws Exception {
        IndexedCacheService<String> cache = new IndexedCacheService<>();
        cache.putAll("DICT:unit_code", List.of("次", "支"), v -> v.equals("次") ? "6" : "branch");
        assertEquals(2, cache.getAll("DICT:unit_code").size());

        // 模拟 maximumSize 驱逐：只丢掉 item，保留 index
        @SuppressWarnings("unchecked")
        Cache<String, String> itemCache = (Cache<String, String>) readField(cache, "itemCache");
        itemCache.invalidate("DICT:unit_code:branch");

        assertTrue(cache.getAll("DICT:unit_code").isEmpty());
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
