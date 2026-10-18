<script lang="ts" setup>
import { ref } from 'vue';

import { useElementPlusDesignTokens } from '@zxplat/hooks/es/use-design-tokens';

import { ElConfigProvider } from 'element-plus';
import defaultLocale from 'element-plus/es/locale/lang/zh-cn';

defineOptions({ name: 'App' });

useElementPlusDesignTokens();

const elementLocale = ref(defaultLocale);

void import('#/locales').then((mod) => {
  elementLocale.value = mod.elementLocale.value;
});
</script>

<template>
  <ElConfigProvider :locale="elementLocale">
    <div class="h-full min-h-0">
      <RouterView />
    </div>
  </ElConfigProvider>
</template>

<style>
/* 为 Select 选项添加省略号和 tooltip 支持 - 全局样式 */
.el-select-v2__option-item {
  display: block !important;
  max-width: 100% !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
  white-space: nowrap !important;
}

.el-select-v2__option {
  cursor: pointer;
}

/* 确保选项文本可以被截断 */
.el-select-v2__option-item span {
  display: block !important;
  max-width: 100% !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
  white-space: nowrap !important;
}
</style>
