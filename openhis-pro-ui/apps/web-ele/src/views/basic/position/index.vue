<script lang="ts" setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

defineOptions({
  name: 'PositionWorkbenchDeprecated',
});

const router = useRouter();

/** 岗位页已废弃：兼容旧书签/菜单路径，跳转用户管理 */
onMounted(() => {
  ElMessage.info('岗位维护已合并至用户管理');
  router.replace('/system/user');
});
</script>

<template>
  <Page auto-content-height>
    <div class="flex min-h-[240px] items-center justify-center text-[var(--el-text-color-regular)]">
      正在跳转到用户管理…
    </div>
  </Page>
</template>
