<template>
  <div class="page-container">
    <!-- 学业红线预警提示（教务处） -->
    <WarningAlert :data="warning" />

    <el-row :gutter="16" class="stat-cards">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #409eff">{{ scores.length }}</div>
          <div class="stat-label">课程门数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #67c23a">{{ earnedCredit }}</div>
          <div class="stat-label">已获学分</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #f56c6c">{{ avgTotalScore }}</div>
          <div class="stat-label">平均总评</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #e6a23c">{{ avgGradePoint }}</div>
          <div class="stat-label">平均绩点</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>我的成绩</span>
          <el-button type="primary" link :loading="loading" @click="loadList(); loadWarning()">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="scores" border stripe empty-text="暂无成绩记录">
        <el-table-column prop="courseNo" label="课程号" width="110" />
        <el-table-column prop="courseName" label="课程名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="credit" label="学分" width="80" align="center" />
        <el-table-column label="性质" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="courseTypeTag(row.courseType)" size="small">
              {{ row.courseType || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" label="任课教师" width="110" />
        <el-table-column prop="semesterName" label="学期" min-width="170" show-overflow-tooltip />
        <el-table-column prop="usualScore" label="平时" width="90" align="center">
          <template #default="{ row }">{{ row.usualScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="examScore" label="期末" width="90" align="center">
          <template #default="{ row }">{{ row.examScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="总评" width="100" align="center">
          <template #default="{ row }">
            <span :class="{ 'score-fail': isFail(row.totalScore) }">
              {{ row.totalScore ?? '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="是否获得学分" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.totalScore === null || row.totalScore === undefined" type="info" size="small">未出成绩</el-tag>
            <el-tag v-else-if="isFail(row.totalScore)" type="danger" size="small">未获得</el-tag>
            <el-tag v-else type="success" size="small">已获得</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="绩点" width="90" align="center">
          <template #default="{ row }">{{ row.gradePoint ?? '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchMyScores } from '@/api/student'
import { fetchMyWarning } from '@/api/warning'
import { courseTypeTag } from '@/utils/dict'
import WarningAlert from '@/components/WarningAlert.vue'

const scores = ref([])
const loading = ref(false)
const warning = ref(null)

function isFail(value) {
  return value !== null && value !== undefined && Number(value) < 60
}

/** 已获学分：总评 >= 60 的课程学分之和 */
const earnedCredit = computed(() => {
  const total = scores.value.reduce((sum, item) => {
    const score = Number(item.totalScore)
    if (Number.isNaN(score) || score < 60) return sum
    return sum + (Number(item.credit) || 0)
  }, 0)
  return Number.isInteger(total) ? String(total) : total.toFixed(1)
})

/** 平均总评 */
const avgTotalScore = computed(() => {
  const list = scores.value
    .filter((item) => item.totalScore !== null && item.totalScore !== undefined)
    .map((item) => Number(item.totalScore))
    .filter((num) => !Number.isNaN(num))
  if (!list.length) return '-'
  return (list.reduce((a, b) => a + b, 0) / list.length).toFixed(2)
})

/** 平均绩点：仅统计已换算绩点的课程（未出成绩的课程不参与） */
const avgGradePoint = computed(() => {
  const list = scores.value
    .filter((item) => item.gradePoint !== null && item.gradePoint !== undefined)
    .map((item) => Number(item.gradePoint))
    .filter((num) => !Number.isNaN(num))
  if (!list.length) return '-'
  return (list.reduce((a, b) => a + b, 0) / list.length).toFixed(2)
})

async function loadList() {
  loading.value = true
  try {
    const res = await fetchMyScores()
    scores.value = res?.data || []
  } catch (e) {
    scores.value = []
  } finally {
    loading.value = false
  }
}

async function loadWarning() {
  try {
    const res = await fetchMyWarning()
    warning.value = res?.data || null
  } catch (e) {
    warning.value = null
  }
}

onMounted(() => {
  loadList()
  loadWarning()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-card {
  margin-bottom: 16px;
  text-align: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
}

.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}

.score-fail {
  color: #f56c6c;
  font-weight: 600;
}
</style>
