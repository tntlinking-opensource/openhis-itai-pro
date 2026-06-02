<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { ElMessage } from 'element-plus';

import { getChallengeApi } from '#/api/core/ca-auth';
import {
  getCertByVendor,
  getUserList,
  initDetection,
  resetDetection,
  signMessageByVendor,
} from '#/api/vendor/ca-vendor';
import type { CaVendorApi } from '#/api/vendor/ca-vendor';
import { useAuthStore } from '#/store';
import CaLoginStatus from './CaLoginStatus.vue';

defineOptions({ name: 'CaLoginForm' });

const authStore = useAuthStore();

const emit = defineEmits<{
  loadingChange: [value: boolean];
  loginSuccess: [result: { tenants: any[]; userInfo: any }];
}>();

const status = ref<'idle' | 'fetching_challenge' | 'signing' | 'logging_in' | 'success' | 'error'>(
  'idle',
);
const errorMessage = ref('');

// CA客户端检测状态
const clientStatus = ref<CaVendorApi.ClientStatus | null>(null);
const detecting = ref(true);

// 用户证书列表
const userList = ref<CaVendorApi.UserCertItem[]>([]);
const selectedCertID = ref('');
const userListLoading = ref(false);

onMounted(async () => {
  try {
    detecting.value = true;
    clientStatus.value = await initDetection();

    if (clientStatus.value.available) {
      // 客户端可用，自动加载用户列表
      await loadUserList();
    }
  } catch (e: any) {
    clientStatus.value = {
      available: false,
      reason: e?.message || 'CA组件检测失败',
    };
  } finally {
    detecting.value = false;
  }
});

async function loadUserList() {
  userListLoading.value = true;
  try {
    const list = await getUserList();
    userList.value = list;
    if (list.length > 0) {
      selectedCertID.value = list[0]!.certID;
    }
  } catch (e: any) {
    console.warn('[CaLoginForm] 获取用户列表失败:', e?.message);
    userList.value = [];
    // 如果获取列表失败，可能客户端连接已断开，更新状态
    if (!clientStatus.value?.available) return;
    clientStatus.value = {
      available: false,
      reason: e?.message || '获取证书列表失败',
    };
  } finally {
    userListLoading.value = false;
  }
}

async function handleCaLogin() {
  if (!clientStatus.value?.available) {
    ElMessage.error('CA证书组件不可用，请确认客户端已安装并运行');
    return;
  }

  if (userList.value.length === 0) {
    ElMessage.warning('未检测到CA证书，请插入USB Key后刷新');
    return;
  }

  const certID = selectedCertID.value;
  if (!certID) {
    ElMessage.warning('请选择要使用的证书');
    return;
  }

  try {
    status.value = 'fetching_challenge';
    errorMessage.value = '';
    emit('loadingChange', true);

    // 1. 获取密钥
    const challengeRes = await getChallengeApi();
    const { challengeId, nonce } = challengeRes.data;

    // 2. 获取证书
    status.value = 'signing';
    const certResult = await getCertByVendor(certID);

    // 3. 对nonce进行PKCS7签名（Detached模式，包含完整签名算法信息）
    const signResult = await signMessageByVendor(nonce, certID);

    // 4. 通过authStore提交CA登录（自动存储token、获取租户列表）
    status.value = 'logging_in';
    await authStore.caLogin(
      {
        challengeId,
        nonce,
        signature: signResult.signature,
        certificate: certResult.certificate,
        algorithm: signResult.algorithm,
      },
      ({ tenants, userInfo }) => {
        emit('loginSuccess', { tenants, userInfo });
      },
    );

    status.value = 'success';
    ElMessage.success('CA证书登录成功');
  } catch (error: any) {
    status.value = 'error';
    const msg = error?.response?.data?.message || error?.message || 'CA证书登录失败';
    errorMessage.value = msg;
    ElMessage.error(msg);
  } finally {
    emit('loadingChange', false);
  }
}

async function handleRefresh() {
  resetDetection();
  detecting.value = true;
  try {
    clientStatus.value = await initDetection();
    if (clientStatus.value.available) {
      await loadUserList();
    }
  } catch (e: any) {
    clientStatus.value = {
      available: false,
      reason: e?.message || 'CA组件检测失败',
    };
  } finally {
    detecting.value = false;
    resetStatus();
  }
}

function resetStatus() {
  status.value = 'idle';
  errorMessage.value = '';
}

const statusTextMap: Record<string, string> = {
  idle: '准备就绪',
  fetching_challenge: '正在获取密钥...',
  signing: '正在获取证书并签名...',
  logging_in: '正在验证登录...',
  success: '登录成功',
  error: '登录失败',
};

const isProcessing = () => ['fetching_challenge', 'signing', 'logging_in'].includes(status.value);
</script>

<template>
  <div class="w-full space-y-4">
    <!-- 检测中 -->
    <div v-if="detecting" class="text-muted-foreground flex items-center gap-2 text-sm">
      <span class="animate-spin">⏳</span>
      正在检测CA证书组件...
    </div>

    <!-- 客户端不可用 -->
    <el-alert v-else-if="!clientStatus?.available" type="error" :closable="false" show-icon>
      <template #title> CA证书组件未连接 </template>
      <template #default>
        <div class="text-xs">
          <p>{{ clientStatus?.reason || '未知错误' }}</p>
          <ol class="mt-2 list-inside list-decimal space-y-1">
            <li>已安装BJCA证书应用环境</li>
            <li>证书服务正在运行（WebSocket: ws://127.0.0.1:21051）</li>
            <li>浏览器未阻止本地连接</li>
          </ol>
          <p class="mt-2">
            安装后点击
            <el-link type="primary" :underline="false" @click="handleRefresh">刷新检测</el-link>
          </p>
        </div>
      </template>
    </el-alert>

    <!-- 客户端可用 -->
    <template v-else>
      <CaLoginStatus :status="status" :error-message="errorMessage" />

      <!-- 客户端版本信息 -->
      <div v-if="clientStatus?.version" class="text-muted-foreground text-xs">
        BJCA客户端 v{{ clientStatus.version }}
      </div>

      <!-- 证书选择 -->
      <div class="space-y-2">
        <label class="text-foreground text-sm font-medium">选择证书</label>
        <el-select
          v-model="selectedCertID"
          :loading="userListLoading"
          placeholder="请选择证书"
          class="w-full"
          size="large"
        >
          <el-option
            v-for="user in userList"
            :key="user.certID"
            :label="user.certName"
            :value="user.certID"
          />
        </el-select>

        <div v-if="userList.length === 0 && !userListLoading" class="text-muted-foreground text-xs">
          未检测到证书，请插入USB Key后
          <el-link type="primary" :underline="false" @click="handleRefresh">刷新</el-link>
        </div>
      </div>

      <!-- 登录按钮 -->
      <div class="space-y-3">
        <el-button
          :loading="isProcessing()"
          :disabled="isProcessing() || userList.length === 0"
          type="primary"
          class="w-full"
          size="large"
          @click="handleCaLogin"
        >
          {{ isProcessing() ? statusTextMap[status] : 'CA证书登录' }}
        </el-button>

        <div class="flex gap-2">
          <el-button v-if="status === 'error'" class="flex-1" size="large" @click="resetStatus">
            重新登录
          </el-button>
          <el-button class="flex-1" size="large" @click="handleRefresh"> 刷新证书 </el-button>
        </div>
      </div>
    </template>

    <div class="text-muted-foreground mt-4 text-xs">
      <p>请确保：</p>
      <ul class="mt-1 list-inside list-disc space-y-1">
        <li>已插入CA证书介质（USB Key）</li>
        <li>已安装BJCA证书驱动和签名组件</li>
        <li>浏览器已允许本地签名服务连接</li>
      </ul>
    </div>
  </div>
</template>
