<script setup lang="ts">
import type { Editor } from '@tiptap/core';

import { computed, nextTick, ref, watch } from 'vue';

import { ArrowDown, ArrowUp, Close } from '@element-plus/icons-vue';
import { ElButton, ElCheckbox, ElIcon, ElInput, ElMessage } from 'element-plus';

defineOptions({
  name: 'EmrFindReplaceBar',
});

const props = defineProps<{
  editor?: Editor | null;
  /** 固定模板：替换仅作用于可填域（由命令层过滤） */
  layoutLocked?: boolean;
  /** 只读：可查找，不可替换 */
  readOnly?: boolean;
  visible?: boolean;
}>();

const emit = defineEmits<{
  'update:visible': [boolean];
}>();

const searchTerm = ref('');
const replaceTerm = ref('');
const caseSensitive = ref(false);
const findInputRef = ref<{ focus?: () => void } | null>(null);

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

const canReplace = computed(() => !props.readOnly && Boolean(props.editor?.isEditable));

const replaceHint = computed(() =>
  props.layoutLocked ? '固定模板下仅替换可填内容，结构文字不会被改' : '',
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
  const ok = ed.commands.emrReplaceCurrent(replaceTerm.value);
  if (!ok && props.layoutLocked) {
    ElMessage.info('当前匹配在结构文字上，固定模板下不可替换；请查找可填内容');
  }
}

function runReplaceAll() {
  if (!canReplace.value) return;
  const ed = props.editor;
  if (!ed || ed.isDestroyed) return;
  ed.commands.emrReplaceAll(replaceTerm.value);
}

watch(
  () => props.visible,
  async (open) => {
    if (!open) return;
    const stored = findStorage.value?.searchTerm;
    if (stored && !searchTerm.value) {
      searchTerm.value = stored;
    }
    await nextTick();
    findInputRef.value?.focus?.();
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
        ref="findInputRef"
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
        :title="replaceHint"
        @keyup.enter="runReplaceCurrent"
      />
      <ElButton
        size="small"
        :disabled="!canReplace"
        :title="replaceHint"
        @click="runReplaceCurrent"
      >
        替换
      </ElButton>
      <ElButton size="small" :disabled="!canReplace" :title="replaceHint" @click="runReplaceAll">
        全部替换
      </ElButton>
      <ElCheckbox v-model="caseSensitive" label="区分大小写" />
      <span v-if="layoutLocked && canReplace" class="emr-find-replace-bar__hint">
        仅可填域可替换
      </span>
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

.emr-find-replace-bar__hint {
  font-size: 11px;
  color: #94a3b8;
}

.emr-find-replace-bar__spacer {
  flex: 1;
  min-width: 8px;
}
</style>
