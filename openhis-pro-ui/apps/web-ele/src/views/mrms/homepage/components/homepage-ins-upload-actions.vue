<script setup lang="ts">
import type { HomepageListRow } from '../homepage-list.types';
import type { HomepageInsActionCode, HomepageInsActionDef } from '../homepage.constants';

import { computed } from 'vue';

import { ArrowDown } from '@element-plus/icons-vue';
import { ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu, ElIcon } from 'element-plus';

import { MRMS_HOMEPAGE_4401_UPLOAD_ENABLED } from '#/views/mrms/homepage/homepage-4401-upload.config';

import {
  HOMEPAGE_INS_MORE_ACTIONS,
  HOMEPAGE_INS_ONE_CLICK,
  HOMEPAGE_INS_SETTLE_PRIMARY,
} from '../homepage.constants';

defineOptions({ name: 'HomepageInsUploadActions' });

interface Props {
  row: HomepageListRow;
  /** 当前行医保操作进行中 */
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const emit = defineEmits<{
  action: [payload: { code: HomepageInsActionCode; row: HomepageListRow }];
}>();

/** 主按钮：4401 开通用一键上报，否则一键上传（清单+提交审核） */
const primaryAction = computed<HomepageInsActionDef>(() => {
  if (MRMS_HOMEPAGE_4401_UPLOAD_ENABLED) {
    return HOMEPAGE_INS_ONE_CLICK;
  }
  return HOMEPAGE_INS_SETTLE_PRIMARY;
});

/**
 * 「更多」列表：关闭 4401 时去掉首页上传。
 */
const moreActions = computed(() => {
  return HOMEPAGE_INS_MORE_ACTIONS.filter((action) => {
    // 未开通首页上传业务：隐藏 4401 入口
    if (!MRMS_HOMEPAGE_4401_UPLOAD_ENABLED && action.code === 'upload-homepage') {
      return false;
    }
    return true;
  });
});

/** 触发指定医保入口 */
function emitAction(code: HomepageInsActionCode, current: HomepageListRow) {
  if (props.loading) {
    return;
  }
  emit('action', { code, row: current });
}

/** 「更多」下拉选中 */
function onMoreCommand(code: string | number, current: HomepageListRow) {
  emitAction(String(code) as HomepageInsActionCode, current);
}
</script>

<template>
  <div class="hp-ins-actions">
    <ElButton
      link
      type="primary"
      :loading="loading"
      :disabled="loading"
      @click="emitAction(primaryAction.code, row)"
    >
      {{ primaryAction.label }}
    </ElButton>
    <ElDropdown
      v-if="moreActions.length > 0"
      trigger="click"
      :disabled="loading"
      @command="(c) => onMoreCommand(c, row)"
    >
      <ElButton link type="primary" :disabled="loading">
        更多
        <ElIcon class="hp-ins-actions__caret"><ArrowDown /></ElIcon>
      </ElButton>
      <template #dropdown>
        <ElDropdownMenu>
          <ElDropdownItem v-for="action in moreActions" :key="action.code" :command="action.code">
            {{ action.label }}
          </ElDropdownItem>
        </ElDropdownMenu>
      </template>
    </ElDropdown>
  </div>
</template>

<style scoped>
.hp-ins-actions {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.hp-ins-actions__caret {
  margin-left: 2px;
}
</style>
