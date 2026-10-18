<script setup lang="ts">
/**
 * 主线占位页：无 Canvas 实现。
 * 国基通过 overrides 同名 `#/views/basic/emr-canvas-template/EmrCanvasTemplatePage.vue` 覆盖。
 */
import { Page } from '@zxplat/common-ui';

import { ElEmpty } from 'element-plus';

defineOptions({
  name: 'EmrCanvasTemplatePage',
});
</script>

<template>
  <Page auto-content-height>
    <div class="emr-canvas-template-unavailable">
      <ElEmpty description="当前院点未启用 Canvas 病历模板" />
    </div>
  </Page>
</template>

<style scoped>
.emr-canvas-template-unavailable {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  padding: 24px;
}
</style>
