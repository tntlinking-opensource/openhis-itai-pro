<script setup lang="ts">
import { ref } from 'vue';

import { ElAlert, ElMessage, ElSwitch } from 'element-plus';

import {
  applyHiprintAutoConnectPolicy,
  isHiprintAutoConnectDisabled,
  setHiprintAutoConnectDisabled,
} from '#/utils/hiprint-connect-pref';

const disableAutoConnect = ref(isHiprintAutoConnectDisabled());

async function handleToggle(value: boolean) {
  setHiprintAutoConnectDisabled(value);
  disableAutoConnect.value = value;
  await applyHiprintAutoConnectPolicy();
  if (value) {
    ElMessage.success('已屏蔽 Hiprint 本地打印连接');
    return;
  }
  ElMessage.info('已恢复自动连接；若仍有报错请刷新页面');
}
</script>

<template>
  <div class="max-w-2xl">
    <div class="mb-4 text-base font-medium">客户端</div>
    <ElAlert class="mb-4" :closable="false" show-icon title="Hiprint 本地打印" type="info">
      开启屏蔽后，将不再连接本机 <code>localhost:17521</code> 打印中转服务，可避免控制台 WebSocket
      报错。需要静默直连打印机时，请关闭此项并安装运行 electron-hiprint。
    </ElAlert>
    <div class="border-border flex items-center justify-between rounded-md border px-4 py-3">
      <div>
        <div class="text-sm font-medium">屏蔽 Hiprint 本地打印连接</div>
        <div class="text-muted-foreground mt-1 text-xs">
          设置保存在本机浏览器 localStorage，仅对当前浏览器生效。
        </div>
      </div>
      <ElSwitch :model-value="disableAutoConnect" @change="handleToggle" />
    </div>
  </div>
</template>
