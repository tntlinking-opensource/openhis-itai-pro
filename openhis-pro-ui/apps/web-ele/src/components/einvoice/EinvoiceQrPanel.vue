<script setup lang="ts">
import type { BossCashierQrDto } from '#/api/einvoice-boss/cashier';

import { computed } from 'vue';

import { ElImage } from 'element-plus';

const props = defineProps<{
  issued?: boolean;
  qr?: BossCashierQrDto | null;
}>();

const qrSrc = computed(() => {
  const code = props.qr?.billQrCode?.trim();
  if (!code) {
    return '';
  }
  if (code.startsWith('data:') || code.startsWith('http')) {
    return code;
  }
  return `data:image/png;base64,${code}`;
});

const hasQr = computed(() => Boolean(qrSrc.value));
</script>

<template>
  <div class="flex flex-col items-center gap-2 py-2">
    <template v-if="issued && hasQr">
      <ElImage :src="qrSrc" fit="contain" class="h-40 w-40" />
      <p class="text-sm text-[var(--el-text-color-regular)]">请患者扫码查看电子发票</p>
      <p v-if="qr?.billNo" class="text-xs text-[var(--el-text-color-secondary)]">
        票号 {{ qr.billNo }}
      </p>
    </template>
    <template v-else>
      <div
        class="flex h-40 w-40 items-center justify-center rounded border border-dashed border-[var(--el-border-color)] bg-[var(--el-fill-color-light)] text-sm text-[var(--el-text-color-placeholder)]"
      >
        二维码生成中
      </div>
    </template>
  </div>
</template>
