<script setup lang="ts">
import type { Editor } from '@tiptap/core';

import { computed, ref, watch } from 'vue';

import { ArrowDown, ArrowUp, Close } from '@element-plus/icons-vue';
import { ElButton, ElCheckbox, ElIcon, ElInput } from 'element-plus';

defineOptions({
  name: 'EmrFindReplaceBar',
});

const props = defineProps<{
  editor?: Editor | null;
  /** 只读：可查找，不可替换 */
  readOnly?: boolean;
  /** 固定模板等场景禁止替换正文 */
  replaceDisabled?: boolean;
  visible?: boolean;
}>();

const emit = defineEmits<{
  'update:visible': [boolean];
}>();

const searchTerm = ref('');
const replaceTerm = ref('');
const caseSensitive = ref(false);

const findStorage = computed(() => props.editor?.storage.emrFindReplace);

const counterLabel = computed(() => {
  const s = findStorage.value;
  const term = (s?.searchTerm ?? searchTerm.value).trim();
  if (!term) return '';
  const total = s?.matchCount ?? 0;
  if (total === 0) return '无匹配';
  const cur = (s?.currentIndex ?? -1) + 1;
  return `${cur} / ${total}`;
});

const canReplace = computed(
  () => !props.readOnly && !props.replaceDisabled && Boolean(props.editor?.isEditable),
);

function closeBar() {
  props.editor?.commands.emrClearFind();
  emit('update:visible', false);
}

function runFind() {
  const ed = props.editor;
  if (!ed || ed.isDestroyed) return;
  ed.commands.emrFind(searchTerm.value, { caseSensitive: caseSensitive.value });
}

function runFindNext() {
  props.editor?.commands.emrFindNext();
}

function runFindPrevious() {
  props.editor?.commands.emrFindPrevious();
}

function runReplaceCurrent() {
  if (!canReplace.value) return;
  const ed = props.editor;
  if (!ed || ed.isDestroyed) return;
  ed.commands.emrReplaceCurrent(replaceTerm.value);
}

function runReplaceAll() {
  if (!canReplace.value) return;
  const ed = props.editor;
  if (!ed || ed.isDestroyed) return;
  ed.commands.emrReplaceAll(replaceTerm.value);
}

watch(
  () => props.visible,
  (open) => {
    if (!open) return;
    const stored = findStorage.value?.searchTerm;
    if (stored && !searchTerm.value) {
      searchTerm.value = stored;
    }
  },
);

watch(caseSensitive, () => {
  if (props.visible && searchTerm.value.trim()) {
    runFind();
  }
});

let findDebounceTimer: ReturnType<typeof setTimeout> | undefined;

watch(searchTerm, () => {
  if (!props.visible) return;
  if (findDebounceTimer) clearTimeout(findDebounceTimer);
  findDebounceTimer = setTimeout(() => {
    if (searchTerm.value.trim()) {
      runFind();
    } else {
      props.editor?.commands.emrClearFind();
    }
  }, 280);
});

function clearSearchInput() {
  searchTerm.value = '';
  props.editor?.commands.emrClearFind();
}
</script>

<template>
  <div v-show="visible" class="emr-find-replace-bar" role="search">
    <div class="emr-find-replace-bar__row">
      <span class="emr-find-replace-bar__label">查找</span>
      <ElInput
        v-model="searchTerm"
        class="emr-find-replace-bar__input"
        placeholder="输入查找内容"
        clearable
        @keyup.enter="runFindNext"
        @clear="clearSearchInput"
      />
      <ElButton size="small" @click="runFind">查找</ElButton>
      <ElButton size="small" :icon="ArrowUp" title="上一个 (Shift+F3)" @click="runFindPrevious" />
      <ElButton size="small" :icon="ArrowDown" title="下一个 (F3)" @click="runFindNext" />
      <span v-if="counterLabel" class="emr-find-replace-bar__counter">{{ counterLabel }}</span>
    </div>
    <div class="emr-find-replace-bar__row">
      <span class="emr-find-replace-bar__label">替换</span>
      <ElInput
        v-model="replaceTerm"
        class="emr-find-replace-bar__input"
        placeholder="替换为"
        clearable
        :disabled="!canReplace"
        @keyup.enter="runReplaceCurrent"
      />
      <ElButton size="small" :disabled="!canReplace" @click="runReplaceCurrent">替换</ElButton>
      <ElButton size="small" :disabled="!canReplace" @click="runReplaceAll">全部替换</ElButton>
      <ElCheckbox v-model="caseSensitive" label="区分大小写" />
      <span class="emr-find-replace-bar__spacer"></span>
      <ElButton class="emr-find-replace-bar__close" text size="small" @click="closeBar">
        <ElIcon><Close /></ElIcon>
      </ElButton>
    </div>
  </div>
</template>

<style scoped lang="scss">
.emr-find-replace-bar {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid #e5e7eb;
  background: #f1f5f9;
}

.emr-find-replace-bar__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.emr-find-replace-bar__label {
  flex-shrink: 0;
  width: 36px;
  font-size: 12px;
  color: #64748b;
}

.emr-find-replace-bar__input {
  width: 200px;
  max-width: min(280px, 40vw);
}

.emr-find-replace-bar__counter {
  font-size: 12px;
  color: #64748b;
  min-width: 48px;
}

.emr-find-replace-bar__spacer {
  flex: 1;
  min-width: 8px;
}
</style>
