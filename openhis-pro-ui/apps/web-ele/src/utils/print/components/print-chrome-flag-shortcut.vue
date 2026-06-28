<script setup lang="ts">
import { ElButton } from 'element-plus';

import {
  copyPrintChromeFlagUrl,
  PRINT_CHROME_FLAG_INTRO,
  PRINT_CHROME_FLAG_URL,
} from '../runtime/print-user-error';

defineOptions({ name: 'PrintChromeFlagShortcut' });

/** 复制 Chrome flags 地址 */
async function handleCopy(): Promise<void> {
  await copyPrintChromeFlagUrl();
}
</script>

<template>
  <div>
    <p class="m-0 whitespace-pre-line text-sm leading-relaxed text-[var(--el-text-color-regular)]">
      {{ PRINT_CHROME_FLAG_INTRO }}
    </p>
    <div class="mt-3 flex flex-wrap items-center gap-2">
      <code
        class="break-all rounded bg-[var(--el-fill-color-light)] px-2 py-1 text-xs text-[var(--el-text-color-primary)]"
      >
        {{ PRINT_CHROME_FLAG_URL }}
      </code>
      <ElButton size="small" type="primary" @click="handleCopy">复制设置地址</ElButton>
    </div>
    <p class="mb-0 mt-2 text-xs text-[var(--el-text-color-secondary)]">
      复制后粘贴到 Chrome 地址栏回车，找到 Local network access checks 并设为 Disabled。
    </p>
  </div>
</template>
