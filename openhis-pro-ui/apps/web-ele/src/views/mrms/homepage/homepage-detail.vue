<script setup lang="ts">
/**
 * 病案首页详情页签入口。
 *
 * <p>与列表共用业务实现，但组件名必须与路由名一致，避免 KeepAlive
 * 与列表页同名缓存串台（偶发页签标题已是详情、内容仍是列表）。</p>
 */
import HomepageWorkbench from './index.vue';

defineOptions({ name: 'MrmsCaseRecordHomepageDetail' });
</script>

<template>
  <HomepageWorkbench />
</template>
