<template>
  <div class="page-container">
    <el-page-header class="page-card" @back="goBack">
      <template #content>
        <span class="page-title">学生名单与成绩录入</span>
      </template>
    </el-page-header>

    <el-row :gutter="16" class="stat-cards">
      <el-col v-for="item in statItems" :key="item.label" :xs="12" :sm="8" :md="3">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>成绩录入（总评 = 平时成绩 × 40% + 期末成绩 × 60%）</span>
          <div>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!roster.length"
              @click="handleSaveAll"
            >
              <el-icon><Check /></el-icon>批量保存
            </el-button>
            <el-button :loading="loading" @click="loadAll">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="roster"
        border
        stripe
        row-key="selectionId"
        empty-text="暂无学生选课"
      >
        <el-table-column type="index" label="#" width="56" align="center" />
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="studentName" label="姓名" width="110" />
        <el-table-column prop="gender" label="性别" width="80" align="center" />
        <el-table-column prop="clazzName" label="班级" min-width="130" />

        <el-table-column label="学业预警" width="140" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="row.warningLevel && row.warningLevel !== 'NORMAL'"
              effect="dark"
              size="small"
              :color="warningColor(row.warningLevel)"
              class="wl-tag"
            >
              {{ warningShortLabel(row.warningLevel) }}
            </el-tag>
            <span v-else class="wl-none">正常</span>
          </template>
        </el-table-column>

        <el-table-column label="平时成绩" width="150" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="row.usualScore"
              :min="0"
              :max="100"
              :precision="1"
              :step="1"
              controls-position="right"
              size="small"
              style="width: 110px"
              placeholder="0-100"
            />
          </template>
        </el-table-column>

        <el-table-column label="期末成绩" width="150" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="row.examScore"
              :min="0"
              :max="100"
              :precision="1"
              :step="1"
              controls-position="right"
              size="small"
              style="width: 110px"
              placeholder="0-100"
            />
          </template>
        </el-table-column>

        <el-table-column prop="totalScore" label="总评" width="100" align="center">
          <template #default="{ row }">
            {{ row.totalScore ?? '-' }}
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.scored ? 'success' : 'info'" size="small">
              {{ row.scored ? '已录入' : '未录入' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  fetchCourseStudents,
  fetchCourseStatistics,
  saveCourseScores
} from '@/api/teacher'
import { warningColor, warningShortLabel } from '@/utils/dict'

const route = useRoute()
const router = useRouter()

const courseId = computed(() => route.params.courseId)

const roster = ref([])
const statistics = ref({})
const loading = ref(false)
const saving = ref(false)

function fmt(value, digits = 2) {
  if (value === null || value === undefined || value === '') return '-'
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  return Number.isInteger(num) ? String(num) : num.toFixed(digits)
}

/** 本课程名单中处于学业预警状态的学生人数（教务处红线预警） */
const warnedCount = computed(
  () => roster.value.filter((r) => r.warningLevel && r.warningLevel !== 'NORMAL').length
)

const statItems = computed(() => {
  const s = statistics.value || {}
  return [
    { label: '学生总数', value: s.totalCount ?? '-', color: '#409eff' },
    { label: '平均分', value: fmt(s.avgScore), color: '#409eff' },
    { label: '最高分', value: s.maxScore ?? '-', color: '#67c23a' },
    { label: '最低分', value: s.minScore ?? '-', color: '#e6a23c' },
    { label: '及格人数', value: s.passCount ?? '-', color: '#67c23a' },
    { label: '不及格人数', value: s.failCount ?? '-', color: '#f56c6c' },
    { label: '预警学生', value: warnedCount.value, color: '#f56c6c' }
  ]
})

async function loadRoster() {
  try {
    const res = await fetchCourseStudents(courseId.value)
    roster.value = (res?.data || []).map((item) => ({
      ...item,
      usualScore: item.usualScore ?? null,
      examScore: item.examScore ?? null
    }))
  } catch (e) {
    roster.value = []
  }
}

async function loadStatistics() {
  try {
    const res = await fetchCourseStatistics(courseId.value)
    statistics.value = res?.data || {}
  } catch (e) {
    statistics.value = {}
  }
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadRoster(), loadStatistics()])
  } finally {
    loading.value = false
  }
}

/** 校验 0-100 且不能只填一半 */
function validateScores() {
  for (let i = 0; i < roster.value.length; i += 1) {
    const row = roster.value[i]
    const label = `${row.studentName || ''}（${row.studentNo || ''}）`
    const hasUsual = row.usualScore !== null && row.usualScore !== undefined && row.usualScore !== ''
    const hasExam = row.examScore !== null && row.examScore !== undefined && row.examScore !== ''

    if (!hasUsual && !hasExam) continue

    if (hasUsual && (Number(row.usualScore) < 0 || Number(row.usualScore) > 100)) {
      ElMessage.error(`${label} 的平时成绩必须在 0~100 之间`)
      return null
    }
    if (hasExam && (Number(row.examScore) < 0 || Number(row.examScore) > 100)) {
      ElMessage.error(`${label} 的期末成绩必须在 0~100 之间`)
      return null
    }
    if (hasUsual !== hasExam) {
      ElMessage.error(`${label} 的平时成绩与期末成绩需同时填写`)
      return null
    }
  }

  const items = roster.value
    .filter(
      (row) =>
        row.usualScore !== null &&
        row.usualScore !== undefined &&
        row.usualScore !== '' &&
        row.examScore !== null &&
        row.examScore !== undefined &&
        row.examScore !== ''
    )
    .map((row) => ({
      studentId: row.studentId,
      usualScore: Number(row.usualScore),
      examScore: Number(row.examScore)
    }))

  if (!items.length) {
    ElMessage.warning('没有需要保存的成绩')
    return null
  }
  return items
}

async function handleSaveAll() {
  const items = validateScores()
  if (!items) return

  saving.value = true
  try {
    await saveCourseScores(courseId.value, items)
    ElMessage.success('保存成功')
    await loadAll()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/teacher/courses')
}

onMounted(loadAll)
</script>

<style scoped>
.page-title {
  font-size: 16px;
  font-weight: 600;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.stat-card {
  margin-bottom: 16px;
  text-align: center;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
}

.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}

.wl-tag {
  border: none;
  color: #fff;
}

.wl-none {
  color: #909399;
  font-size: 13px;
}
</style>
