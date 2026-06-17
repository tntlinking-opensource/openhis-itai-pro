<script setup lang="ts">
import type { UploadProps } from 'element-plus';
import type { LayoutDocument } from '#/types/report-layout';

import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElUpload,
} from 'element-plus';

import { ACCEPT_MIME, uploadGuideImageApi } from '#/api/report/report-guide-image';
import { hasUserGuideImageUrl, resolveGuideImageUrl } from '#/utils/report/report-preview';
import { isCustomPageSize, pageSizeMm } from '#/utils/report/overlay-layout';

defineOptions({ name: 'LayoutPagePanel' });

const props = defineProps<{
  layout: LayoutDocument;
  editable?: boolean;
  pipeline?: string;
}>();

const isRollPipeline = computed(
  () => (props.pipeline ?? props.layout.pipeline ?? 'page') === 'roll',
);

const emit = defineEmits<{
  patchPage: [patch: Partial<NonNullable<LayoutDocument['page']>>];
  patchOverlay: [patch: Partial<NonNullable<LayoutDocument['page']>['overlay']>];
}>();

const page = computed(() => props.layout.page ?? {});
const isOverlay = computed(() => page.value.layoutMode === 'overlay');

const margin = computed(() => {
  const m = page.value.marginMm ?? [12, 10, 12, 10];
  return {
    top: m[0] ?? 12,
    right: m[1] ?? 10,
    bottom: m[2] ?? 12,
    left: m[3] ?? 10,
  };
});

function setMargin(side: 'top' | 'right' | 'bottom' | 'left', value: number | undefined) {
  const m = [...(page.value.marginMm ?? [12, 10, 12, 10])];
  const idx = { top: 0, right: 1, bottom: 2, left: 3 }[side];
  m[idx] = value ?? 0;
  emit('patchPage', { marginMm: m });
}

function resetOverlayMargins() {
  emit('patchPage', { marginMm: [0, 0, 0, 0] });
}

function resetPrintOffset() {
  emit('patchOverlay', { printOffsetMm: [0, 0] });
}

const ROLL_WIDTH_PRESETS = [
  { label: '58mm 小票纸', value: 58 },
  { label: '80mm 小票纸（常用）', value: 80 },
  { label: '110mm 宽幅热敏', value: 110 },
] as const;

const rollWidthMm = computed(() => page.value.widthMm ?? 80);

const rollWidthCustomMode = ref(false);

const rollWidthPreset = computed(() => {
  if (rollWidthCustomMode.value) {
    return 'custom';
  }
  const current = rollWidthMm.value;
  return ROLL_WIDTH_PRESETS.some((item) => item.value === current) ? current : 'custom';
});

watch(rollWidthMm, (width) => {
  if (ROLL_WIDTH_PRESETS.some((item) => item.value === width)) {
    rollWidthCustomMode.value = false;
  }
});

function onRollWidthPresetChange(value: number | 'custom') {
  if (value === 'custom') {
    rollWidthCustomMode.value = true;
    return;
  }
  rollWidthCustomMode.value = false;
  emit('patchPage', { widthMm: value });
}

function onRollWidthCustomChange(value: number | undefined) {
  emit('patchPage', { widthMm: value && value > 0 ? value : 80 });
}

const pageSizePreset = computed(() => {
  if (isCustomPageSize(page.value)) {
    return 'custom';
  }
  return page.value.size ?? 'A4';
});

const customPageWidthMm = computed(() => page.value.widthMm ?? pageSizeMm(page.value).widthMm);
const customPageHeightMm = computed(() => page.value.heightMm ?? pageSizeMm(page.value).heightMm);

function onPageSizePresetChange(value: string) {
  if (value === 'custom') {
    const current = pageSizeMm(page.value);
    emit('patchPage', {
      size: 'custom',
      widthMm: current.widthMm,
      heightMm: current.heightMm,
      orientation: page.value.orientation ?? 'portrait',
    });
    return;
  }
  emit('patchPage', {
    size: value,
    widthMm: undefined,
    heightMm: undefined,
  });
}

function onCustomPageWidthChange(value: number | undefined) {
  emit('patchPage', {
    size: 'custom',
    widthMm: value && value > 0 ? value : customPageWidthMm.value,
    heightMm: customPageHeightMm.value,
  });
}

function onCustomPageHeightChange(value: number | undefined) {
  emit('patchPage', {
    size: 'custom',
    widthMm: customPageWidthMm.value,
    heightMm: value && value > 0 ? value : customPageHeightMm.value,
  });
}

const guideImageUrl = computed(() => page.value.overlay?.guideImageUrl ?? '');
const guideConfigured = computed(() => hasUserGuideImageUrl(guideImageUrl.value));
const guidePreviewSrc = computed(() =>
  guideConfigured.value ? resolveGuideImageUrl(guideImageUrl.value) : '',
);
const guidePreviewFailed = ref(false);
const guideUploading = ref(false);

watch(guideImageUrl, () => {
  guidePreviewFailed.value = false;
});

function onGuidePreviewError() {
  guidePreviewFailed.value = true;
}

const showGuidePreviewImage = computed(
  () => guideConfigured.value && !!guidePreviewSrc.value && !guidePreviewFailed.value,
);

const hasGuideUrlToClear = computed(() => guideConfigured.value || !!guideImageUrl.value.trim());

const beforeGuideUpload: UploadProps['beforeUpload'] = async (rawFile) => {
  if (!rawFile.type.startsWith('image/')) {
    ElMessage.warning('请上传 PNG、JPG、SVG 等图片格式');
    return false;
  }
  guideUploading.value = true;
  try {
    const url = await uploadGuideImageApi(rawFile);
    if (!url) {
      ElMessage.error('上传失败，未返回图片地址');
      return false;
    }
    emit('patchOverlay', { guideImageUrl: url });
    ElMessage.success('定位纸已上传');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传定位纸失败');
  } finally {
    guideUploading.value = false;
  }
  return false;
};

function clearGuideImage() {
  emit('patchOverlay', { guideImageUrl: undefined });
}
</script>

<template>
  <ElForm label-position="top" size="small" :disabled="!editable">
    <template v-if="isRollPipeline">
      <ElFormItem label="纸型">
        <ElSelect
          :model-value="rollWidthPreset"
          class="layout-page-panel__full-width"
          @update:model-value="onRollWidthPresetChange"
        >
          <ElOption
            v-for="item in ROLL_WIDTH_PRESETS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
          <ElOption label="自定义宽度" value="custom" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem v-if="rollWidthPreset === 'custom'" label="自定义 mm">
        <ElInputNumber
          :model-value="rollWidthMm"
          :min="40"
          :max="200"
          :step="0.5"
          :precision="1"
          controls-position="right"
          class="layout-page-panel__full-width"
          @update:model-value="onRollWidthCustomChange"
        />
      </ElFormItem>
    </template>
    <template v-else>
      <ElFormItem label="纸型">
        <ElSelect
          :model-value="pageSizePreset"
          class="layout-page-panel__full-width"
          @update:model-value="onPageSizePresetChange"
        >
          <ElOption label="A4" value="A4" />
          <ElOption label="A5" value="A5" />
          <ElOption label="自定义" value="custom" />
        </ElSelect>
      </ElFormItem>
      <template v-if="pageSizePreset === 'custom'">
        <ElFormItem label="宽度 mm">
          <ElInputNumber
            :model-value="customPageWidthMm"
            :min="40"
            :max="600"
            :step="0.5"
            :precision="1"
            controls-position="right"
            class="layout-page-panel__full-width"
            @update:model-value="onCustomPageWidthChange"
          />
        </ElFormItem>
        <ElFormItem label="高度 mm">
          <ElInputNumber
            :model-value="customPageHeightMm"
            :min="40"
            :max="1000"
            :step="0.5"
            :precision="1"
            controls-position="right"
            class="layout-page-panel__full-width"
            @update:model-value="onCustomPageHeightChange"
          />
        </ElFormItem>
        <p class="layout-page-panel__hint">
          自定义纸型按宽高 mm 精确渲染；套打落格坐标与标尺均以此尺寸为准。
        </p>
      </template>
      <ElFormItem v-else label="方向">
        <ElSelect
          :model-value="page.orientation ?? 'portrait'"
          @update:model-value="(v: string) => emit('patchPage', { orientation: v })"
        >
          <ElOption label="纵向" value="portrait" />
          <ElOption label="横向" value="landscape" />
        </ElSelect>
      </ElFormItem>
    </template>
    <ElFormItem label="排版模式">
      <span>{{ page.layoutMode ?? 'flow' }}</span>
    </ElFormItem>
    <template v-if="!isOverlay">
      <ElFormItem label="上边距 mm">
        <ElInputNumber
          :model-value="margin.top"
          :step="0.5"
          controls-position="right"
          @update:model-value="(v: number | undefined) => setMargin('top', v)"
        />
      </ElFormItem>
      <ElFormItem label="右边距 mm">
        <ElInputNumber
          :model-value="margin.right"
          :step="0.5"
          controls-position="right"
          @update:model-value="(v: number | undefined) => setMargin('right', v)"
        />
      </ElFormItem>
      <ElFormItem label="下边距 mm">
        <ElInputNumber
          :model-value="margin.bottom"
          :step="0.5"
          controls-position="right"
          @update:model-value="(v: number | undefined) => setMargin('bottom', v)"
        />
      </ElFormItem>
      <ElFormItem label="左边距 mm">
        <ElInputNumber
          :model-value="margin.left"
          :step="0.5"
          controls-position="right"
          @update:model-value="(v: number | undefined) => setMargin('left', v)"
        />
      </ElFormItem>
    </template>
    <template v-else>
      <ElFormItem label="页边距">
        <span class="layout-page-panel__hint">
          套打通常为 0；当前 [{{ margin.top }}, {{ margin.right }}, {{ margin.bottom }},
          {{ margin.left }}]
        </span>
        <ElButton v-if="editable" link type="primary" @click="resetOverlayMargins">
          设为 0
        </ElButton>
      </ElFormItem>
      <ElFormItem label="定位纸图">
        <p v-if="!guideConfigured" class="layout-page-panel__hint layout-page-panel__guide-empty">
          未上传定位纸。上传预印纸扫描图后可在设计区半透明叠图对齐落格（不进正式 PDF）。
        </p>
        <div v-else-if="showGuidePreviewImage" class="layout-page-panel__guide-preview">
          <img :src="guidePreviewSrc" alt="" @error="onGuidePreviewError" />
        </div>
        <p
          v-else-if="guideConfigured && guidePreviewFailed"
          class="layout-page-panel__hint layout-page-panel__guide-empty"
        >
          定位纸预览加载失败，请检查路径或重新上传。
        </p>
        <div v-if="editable" class="layout-page-panel__guide-actions">
          <ElUpload
            :before-upload="beforeGuideUpload"
            :show-file-list="false"
            :accept="ACCEPT_MIME"
            :disabled="guideUploading"
          >
            <ElButton type="primary" plain :loading="guideUploading">上传定位纸</ElButton>
          </ElUpload>
          <ElButton v-if="hasGuideUrlToClear" link type="danger" @click="clearGuideImage">
            清除
          </ElButton>
        </div>
        <ElInput
          v-if="guideConfigured"
          :model-value="guideImageUrl"
          placeholder="上传后自动填入路径"
          @update:model-value="
            (v: string) => emit('patchOverlay', { guideImageUrl: v || undefined })
          "
        />
      </ElFormItem>
      <p class="layout-page-panel__hint">
        支持 PNG/JPG/SVG/WebP，建议与纸型同比例扫描；正式 PDF 不含底图，仅设计预览叠图。
      </p>
      <ElFormItem label="全局偏移 X">
        <ElInputNumber
          :model-value="page.overlay?.printOffsetMm?.[0] ?? 0"
          :step="0.1"
          :precision="1"
          controls-position="right"
          @update:model-value="
            (v: number | undefined) =>
              emit('patchOverlay', {
                printOffsetMm: [v ?? 0, page.overlay?.printOffsetMm?.[1] ?? 0],
              })
          "
        />
      </ElFormItem>
      <ElFormItem label="全局偏移 Y">
        <ElInputNumber
          :model-value="page.overlay?.printOffsetMm?.[1] ?? 0"
          :step="0.1"
          :precision="1"
          controls-position="right"
          @update:model-value="
            (v: number | undefined) =>
              emit('patchOverlay', {
                printOffsetMm: [page.overlay?.printOffsetMm?.[0] ?? 0, v ?? 0],
              })
          "
        />
      </ElFormItem>
      <ElFormItem v-if="editable">
        <ElButton size="small" @click="resetPrintOffset">清零全局偏移</ElButton>
      </ElFormItem>
    </template>
  </ElForm>
</template>

<style scoped>
.layout-page-panel__hint {
  margin: 0 0 4px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--el-text-color-secondary);
}

.layout-page-panel__full-width {
  width: 100%;
}

.layout-page-panel__guide-empty {
  margin-bottom: 8px;
  line-height: 1.5;
}

.layout-page-panel__guide-preview {
  margin-bottom: 8px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.layout-page-panel__guide-preview img {
  display: block;
  width: 100%;
  max-height: 160px;
  object-fit: contain;
}

.layout-page-panel__guide-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
</style>
