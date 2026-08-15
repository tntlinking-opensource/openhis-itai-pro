<script setup lang="ts">
import type { HomepageListRow } from '../homepage-list.types';
import type { HomepageRowActionCode } from '../homepage.constants';

import { ArrowDown } from '@element-plus/icons-vue';
import { ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu, ElIcon } from 'element-plus';

import { HOMEPAGE_MORE_ACTIONS, HOMEPAGE_PRIMARY_ACTIONS } from '../homepage.constants';

defineOptions({ name: 'HomepageRowActions' });

interface Props {
  row: HomepageListRow;
}

withDefaults(defineProps<Props>(), {});

const emit = defineEmits<{
  action: [payload: { code: HomepageRowActionCode; row: HomepageListRow }];
}>();

/** 触发指定入口 */
function emitAction(code: HomepageRowActionCode, row: HomepageListRow) {
  emit('action', { code, row });
}

/** 「更多」下拉选中 */
function onMoreCommand(code: string | number, row: HomepageListRow) {
  emitAction(String(code) as HomepageRowActionCode, row);
}
</script>

<template>
  <div class="hp-row-actions">
    <ElButton
      v-for="action in HOMEPAGE_PRIMARY_ACTIONS"
      :key="action.code"
      link
      type="primary"
      @click="emitAction(action.code, row)"
    >
      {{ action.label }}
    </ElButton>
    <ElDropdown trigger="click" @command="(c) => onMoreCommand(c, row)">
      <ElButton link type="primary">
        更多
        <ElIcon class="hp-row-actions__caret"><ArrowDown /></ElIcon>
      </ElButton>
      <template #dropdown>
        <ElDropdownMenu>
          <ElDropdownItem
            v-for="action in HOMEPAGE_MORE_ACTIONS"
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
.hp-row-actions {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.hp-row-actions__caret {
  margin-left: 2px;
}
</style>
