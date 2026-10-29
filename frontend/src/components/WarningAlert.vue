<template>
  <div v-if="data" class="warning-box" :class="levelClass">
    <div class="wb-head">
      <el-icon class="wb-icon"><WarningFilled /></el-icon>
      <span class="wb-title">学业预警</span>
      <el-tag
        effect="dark"
        size="small"
        :color="warningColor(data.level)"
        class="wb-level-tag"
      >
        {{ data.levelLabel }}
      </el-tag>
      <span v-if="showStudent" class="wb-who">
        {{ data.studentNo }} {{ data.studentName }}
        <template v-if="data.clazzName"> · {{ data.clazzName }}</template>
      </span>
    </div>

    <div class="wb-summary">{{ data.summary }}</div>

    <div v-if="data.triggered && data.advice" class="wb-advice">
      <el-icon><InfoFilled /></el-icon>
      <span>{{ data.advice }}</span>
    </div>

    <div v-if="showRules && data.rules && data.rules.length" class="wb-rules">
      <div
        v-for="rule in data.rules"
        :key="rule.code"
        class="wb-rule"
        :class="{ 'wb-rule-hit': rule.triggered }"
      >
        <span class="wb-rule-label">{{ rule.label }}</span>
        <span class="wb-rule-value" :style="{ color: warningColor(rule.level) }">{{ rule.value }}</span>
        <span class="wb-rule-threshold">阈值 {{ rule.threshold }}</span>
        <el-tag
          v-if="rule.triggered"
          size="small"
          effect="dark"
          :color="warningColor(rule.level)"
          class="wb-rule-tag"
        >
          {{ warningShortLabel(rule.level) }}
        </el-tag>
        <el-tag v-else size="small" type="info" effect="plain" class="wb-rule-tag">未触发</el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { warningColor, warningShortLabel } from '@/utils/dict'

const props = defineProps({
  /** WarningVO */
  data: { type: Object, default: null },
  /** 是否展示学生身份信息（管理端复用时可开启） */
  showStudent: { type: Boolean, default: false },
  /** 是否展示两条规则的逐条判定 */
  showRules: { type: Boolean, default: true }
})

const levelClass = computed(() => `lvl-${String(props.data?.level || 'NORMAL').toLowerCase()}`)
</script>

<style scoped>
.warning-box {
  border-radius: 6px;
  border: 1px solid #e6e8eb;
  border-left-width: 5px;
  background: #fff;
  padding: 14px 18px;
  margin-bottom: 16px;
}

.lvl-normal {
  border-left-color: #67c23a;
  background: #f6fdf3;
}

.lvl-yellow {
  border-left-color: #d9a404;
  background: #fffdf3;
}

.lvl-orange {
  border-left-color: #e6862c;
  background: #fff8f2;
}

.lvl-red {
  border-left-color: #f56c6c;
  background: #fef6f6;
}

.wb-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.wb-icon {
  font-size: 20px;
  color: #909399;
}

.lvl-yellow .wb-icon {
  color: #d9a404;
}

.lvl-orange .wb-icon {
  color: #e6862c;
}

.lvl-red .wb-icon {
  color: #f56c6c;
}

.wb-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.wb-level-tag {
  border: none;
  color: #fff;
}

.wb-who {
  color: #606266;
  font-size: 13px;
}

.wb-summary {
  margin-top: 8px;
  font-size: 14px;
  color: #303133;
}

.wb-advice {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.03);
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.wb-rules {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.wb-rule {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #909399;
}

.wb-rule-hit {
  color: #303133;
  font-weight: 500;
}

.wb-rule-label {
  min-width: 130px;
}

.wb-rule-value {
  min-width: 60px;
  font-weight: 600;
}

.wb-rule-threshold {
  color: #909399;
  font-size: 12px;
}

.wb-rule-tag {
  border: none;
  color: #fff;
}
</style>
