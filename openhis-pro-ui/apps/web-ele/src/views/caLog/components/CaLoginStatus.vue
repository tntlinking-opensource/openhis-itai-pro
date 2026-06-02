<script lang="ts" setup>
import { computed } from 'vue';

defineOptions({ name: 'CaLoginStatus' });

const props = defineProps<{
  status: 'error' | 'fetching_challenge' | 'idle' | 'logging_in' | 'signing' | 'success';
  errorMessage?: string;
}>();

const statusConfig = computed(() => {
  const configs: Record<string, { icon: string; text: string; type: string }> = {
    idle: { icon: '🔌', text: '准备就绪，请点击登录按钮', type: 'info' },
    fetching_challenge: { icon: '🔄', text: '正在获取密钥...', type: 'warning' },
    signing: { icon: '✍️', text: '正在获取证书并签名...', type: 'warning' },
    logging_in: { icon: '🔐', text: '正在验证登录...', type: 'warning' },
    success: { icon: '✅', text: '登录成功，正在跳转...', type: 'success' },
    error: { icon: '❌', text: '登录失败', type: 'danger' },
  };
  return configs[props.status] || configs.idle;
});
</script>

<template>
  <div v-if="status !== 'idle'" class="mb-4">
    <el-alert :type="statusConfig.type as any" :closable="false" show-icon>
      <template #title>
        <span>{{ statusConfig.text }}</span>
      </template>
      <template v-if="status === 'error' && errorMessage" #default>
        <span class="text-sm">{{ errorMessage }}</span>
      </template>
    </el-alert>

    <!-- 签名进度步骤 -->
    <el-steps
      v-if="['fetching_challenge', 'signing', 'logging_in', 'success'].includes(status)"
      :active="
        ['idle', 'fetching_challenge', 'signing', 'logging_in', 'success'].indexOf(status) - 1
      "
      class="mt-3"
      simple
      finish-status="success"
    >
      <el-step title="获取密钥" />
      <el-step title="证书签名" />
      <el-step title="验证登录" />
    </el-steps>
  </div>
</template>
