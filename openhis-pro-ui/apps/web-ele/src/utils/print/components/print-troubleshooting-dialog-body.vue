<script setup lang="ts">
import { ElButton } from 'element-plus';

import {
  copyPrintChromeFlagUrl,
  PRINT_CHROME_FLAG_URL,
  PRINT_PLUGIN_INSTALL_BULLETS,
} from '../runtime/print-user-error';

defineOptions({ name: 'PrintTroubleshootingDialogBody' });

defineProps<{
  summary?: string;
}>();

/** 复制 Chrome flags 地址 */
async function handleCopy(): Promise<void> {
  await copyPrintChromeFlagUrl();
}
</script>

<template>
  <div class="print-troubleshoot-body max-h-[60vh] overflow-y-auto pr-1">
    <div
      v-if="summary"
      class="mb-4 rounded-md border border-[var(--el-color-danger-light-5)] bg-[var(--el-color-danger-light-9)] px-3 py-2.5 text-sm leading-relaxed text-[var(--el-color-danger)]"
    >
      {{ summary }}
    </div>

    <p class="mb-4 text-sm leading-relaxed text-[var(--el-text-color-regular)]">
      请点击右上角头像，打开
      <span class="font-medium text-[var(--el-text-color-primary)]">「打印设置」</span>
      查看连接状态，或按以下步骤排查：
    </p>

    <section
      class="rounded-md border border-[var(--el-border-color-lighter)] bg-[var(--el-fill-color-blank)] p-3"
    >
      <div class="mb-2 text-sm font-medium text-[var(--el-text-color-primary)]">
        第一步：确认本机打印插件
      </div>
      <p class="mb-2 text-xs leading-relaxed text-[var(--el-text-color-secondary)]">
        浏览器无法自动判断插件是否已安装，请自行确认：
      </p>
      <ul
        class="m-0 list-disc space-y-1 pl-4 text-sm leading-relaxed text-[var(--el-text-color-regular)]"
      >
        <li v-for="item in PRINT_PLUGIN_INSTALL_BULLETS" :key="item">{{ item }}</li>
      </ul>
    </section>

    <section
      class="mt-3 rounded-md border border-[var(--el-border-color-lighter)] bg-[var(--el-fill-color-blank)] p-3"
    >
      <div class="mb-2 text-sm font-medium text-[var(--el-text-color-primary)]">
        第二步：调整 Chrome 本地网络权限
      </div>
      <p class="mb-3 text-sm leading-relaxed text-[var(--el-text-color-regular)]">
        若插件已安装并启动，仍无法获取打印机或打印失败，请将
        <span class="font-medium">Local network access checks</span>
        设为
        <span class="font-medium">Disabled</span>
        ，然后重启 Chrome 后重试。
      </p>
      <div class="flex flex-wrap items-center gap-2">
        <code
          class="min-w-0 flex-1 break-all rounded bg-[var(--el-fill-color-light)] px-2 py-1.5 text-xs text-[var(--el-text-color-primary)]"
        >
          {{ PRINT_CHROME_FLAG_URL }}
        </code>
        <ElButton size="small" type="primary" @click="handleCopy">复制设置地址</ElButton>
      </div>
      <p class="mb-0 mt-2 text-xs text-[var(--el-text-color-secondary)]">
        复制后粘贴到 Chrome 地址栏回车即可打开对应设置页。
      </p>
    </section>
  </div>
</template>

<style scoped>
.print-troubleshoot-body {
  text-align: left;
}
</style>
