<script lang="ts" setup>
import type { PaperPreset } from '../core/types';
import type { AlignType } from './AlignToolbar.vue';

import { PAPER_PRESETS } from '../composables/use-hiprint-designer';
import AlignToolbar from './AlignToolbar.vue';

defineOptions({ name: 'HiprintDesignerToolbar' });

withDefaults(defineProps<Props>(), {
  scaleValue: 1,
  currentPaperLabel: 'A4',
  saving: false,
  hasSelection: false,
  multiSelection: false,
  subtitle: '',
  saveLabel: '保存',
  canPublish: false,
});

const emit = defineEmits<{
  align: [type: AlignType];
  clear: [];
  editPrintData: [];
  editTemplateJson: [];
  exportJson: [];
  importJson: [];
  paperChange: [preset: PaperPreset];
  preview: [];
  redo: [];
  saveAndPublish: [];
  saveLocal: [];
  undo: [];
  zoomIn: [];
  zoomOut: [];
}>();

interface Props {
  scaleValue?: number;
  currentPaperLabel?: string;
  saving?: boolean;
  hasSelection?: boolean;
  multiSelection?: boolean;
  /** 当前编辑的模板 busNo / 名称 */
  subtitle?: string;
  /** 顶部保存按钮文案 */
  saveLabel?: string;
  /** 是否展示「保存并发布」（后端模板编辑态） */
  canPublish?: boolean;
}

/** 纸张下拉变更 */
function onPaperCommand(label: string): void {
  const preset = PAPER_PRESETS.find((p) => p.label === label);
  if (preset) {
    emit('paperChange', preset);
  }
}
</script>

<template>
  <header class="sv-toolbar-wrap">
    <!-- 顶部紫色导航栏 -->
    <div class="sv-top-bar">
      <div class="sv-brand">
        <i class="svicon sv-print sv-brand-icon"></i>
        <span class="sv-brand-text">ZXPlat 打印设计器</span>
        <span v-if="subtitle" class="sv-brand-subtitle">{{ subtitle }}</span>
      </div>
      <div class="sv-top-actions">
        <button class="sv-top-btn" type="button" @click="emit('importJson')">
          <i class="svicon sv-import"></i>
          本地加载
        </button>
        <button class="sv-top-btn" type="button" @click="emit('exportJson')">
          <i class="svicon sv-export"></i>
          导出
        </button>
        <button class="sv-top-btn" type="button" @click="emit('editPrintData')">
          <i class="svicon sv-edit-data"></i>
          编辑数据
        </button>
        <button class="sv-top-btn" type="button" @click="emit('editTemplateJson')">
          <i class="svicon sv-dev-code"></i>
          模板JSON
        </button>
        <button class="sv-top-btn" type="button" @click="emit('preview')">
          <i class="svicon sv-preview"></i>
          预览
        </button>
        <button
          class="sv-top-btn sv-top-btn--primary"
          :disabled="saving"
          type="button"
          @click="emit('saveLocal')"
        >
          <i class="svicon sv-save"></i>
          {{ saving ? '保存中…' : saveLabel }}
        </button>
        <button
          v-if="canPublish"
          class="sv-top-btn sv-top-btn--publish"
          :disabled="saving"
          type="button"
          @click="emit('saveAndPublish')"
        >
          <i class="svicon sv-save"></i>
          {{ saving ? '处理中…' : '保存并发布' }}
        </button>
      </div>
    </div>

    <!-- sv-print 次级工具栏：对齐 / 撤销 / 缩放 / 纸张 -->
    <div class="sv-toolbar">
      <div class="tool-row">
        <button class="tool" title="撤销" type="button" @click="emit('undo')">
          <i class="svicon sv-undo"></i>
        </button>
        <button class="tool" title="重做" type="button" @click="emit('redo')">
          <i class="svicon sv-redo"></i>
        </button>

        <span class="tool-gap"></span>

        <AlignToolbar
          :has-selection="hasSelection"
          :multi-selection="multiSelection"
          @align="emit('align', $event)"
        />

        <span class="tool-gap"></span>

        <ElDropdown trigger="click" @command="onPaperCommand">
          <button class="tool" title="纸张" type="button">
            <i class="svicon sv-panels"></i>
            <span class="tool-name">{{ currentPaperLabel }}</span>
          </button>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem v-for="p in PAPER_PRESETS" :key="p.label" :command="p.label">
                {{ p.label }} ({{ p.width }}×{{ p.height }}mm)
              </ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>

        <span class="tool-gap"></span>

        <button class="tool" title="缩小" type="button" @click="emit('zoomOut')">
          <i class="svicon sv-zoom-out"></i>
        </button>
        <span class="tool-scale">{{ Math.round(scaleValue * 100) }}%</span>
        <button class="tool" title="放大" type="button" @click="emit('zoomIn')">
          <i class="svicon sv-zoom-in"></i>
        </button>

        <span class="tool-gap"></span>

        <button class="tool" title="清空画布" type="button" @click="emit('clear')">
          <i class="svicon sv-clear"></i>
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.sv-toolbar-wrap {
  flex-shrink: 0;
}

/* ── 顶部主题色导航 ── */
.sv-top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--sv-header-h, 44px);
  padding: 0 16px;
  background: var(--sv-primary);
}

.sv-brand {
  display: flex;
  gap: 8px;
  align-items: center;
  color: #fff;
}

.sv-brand-icon {
  font-size: 22px;
}

.sv-brand-text {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.sv-brand-subtitle {
  padding-left: 12px;
  margin-left: 12px;
  font-size: 14px;
  font-weight: 600;
  color: rgb(255 255 255 / 85%);
  border-left: 1px solid rgb(255 255 255 / 25%);
}

.sv-top-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

.sv-top-btn {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: rgb(255 255 255 / 90%);
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 4px;
  transition: background 0.15s;
}

.sv-top-btn .svicon {
  font-size: 16px;
}

.sv-top-btn:hover {
  color: #fff;
  background: rgb(255 255 255 / 15%);
}

.sv-top-btn--primary {
  font-weight: 600;
  color: var(--sv-primary);
  background: var(--el-color-white);
}

.sv-top-btn--primary:hover {
  color: var(--sv-primary-dark);
  background: var(--el-color-primary-light-9);
}

.sv-top-btn--primary:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.sv-top-btn--publish {
  font-weight: 600;
  color: #fff;
  border: 1px solid rgb(255 255 255 / 55%);
}

.sv-top-btn--publish:hover {
  background: rgb(255 255 255 / 12%);
}

.sv-top-btn--publish:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}
</style>
