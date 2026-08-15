<script setup lang="ts">
import type { HomepageListRow } from '../homepage-list.types';
import type { HomepageInsActionCode } from '../homepage.constants';

import { ArrowDown } from '@element-plus/icons-vue';
import { ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu, ElIcon } from 'element-plus';

import { HOMEPAGE_INS_MORE_ACTIONS, HOMEPAGE_INS_ONE_CLICK } from '../homepage.constants';

defineOptions({ name: 'HomepageInsUploadActions' });

interface Props {
  row: HomepageListRow;
}

withDefaults(defineProps<Props>(), {});

const emit = defineEmits<{
  action: [payload: { code: HomepageInsActionCode; row: HomepageListRow }];
}>();

/** 触发指定医保入口 */
function emitAction(code: HomepageInsActionCode, current: HomepageListRow) {
  emit('action', { code, row: current });
}

/** 「更多」下拉选中 */
function onMoreCommand(code: string | number, current: HomepageListRow) {
  emitAction(String(code) as HomepageInsActionCode, current);
}
</script>

<template>
  <div class="hp-ins-actions">
    <!-- 主入口：一键上报 = 4401 → 4101A -->
    <ElButton link type="primary" @click="emitAction(HOMEPAGE_INS_ONE_CLICK.code, row)">
      {{ HOMEPAGE_INS_ONE_CLICK.label }}
    </ElButton>
    <ElDropdown trigger="click" @command="(c) => onMoreCommand(c, row)">
      <ElButton link type="primary">
        更多
        <ElIcon class="hp-ins-actions__caret"><ArrowDown /></ElIcon>
      </ElButton>
      <template #dropdown>
        <ElDropdownMenu>
          <ElDropdownItem
            v-for="action in HOMEPAGE_INS_MORE_ACTIONS"
            :key="action.code"
            :command="action.code"
          >
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
