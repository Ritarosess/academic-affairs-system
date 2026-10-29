<template>
  <div class="page-container">
    <el-row :gutter="16" class="stat-cards">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ selections.length }}</div>
          <div class="stat-label">已选课程数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ totalCredit }}</div>
          <div class="stat-label">已选学分</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="card-header">
          <span>我的课表</span>
          <el-button type="primary" link :loading="loading" @click="loadList">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="selections" border stripe empty-text="尚未选课">
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
        <el-table-column label="上课时间" width="150">
          <template #default="{ row }">
            {{ row.weekdayLabel || formatWeekday(row.weekday) }}
            第{{ row.startSection }}-{{ row.endSection }}节
          </template>
        </el-table-column>
        <el-table-column prop="location" label="上课地点" width="120" />
        <el-table-column prop="semesterName" label="学期" min-width="170" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.scored ? 'success' : 'primary'" size="small">
              {{ row.statusLabel || (row.scored ? '已出成绩' : '已选') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right" align="center">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.courseType === '必修'"
              content="必修课不可退课"
              placement="top"
            >
              <span>
                <el-button type="danger" link disabled>退课</el-button>
              </span>
            </el-tooltip>
            <el-tooltip
              v-else-if="row.scored === true"
              content="该课程已录入成绩，不可退课"
              placement="top"
            >
              <span>
                <el-button type="danger" link disabled>退课</el-button>
              </span>
            </el-tooltip>
            <el-button v-else type="danger" link @click="handleDrop(row)">
              退课
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span>周课表（周一 ~ 周日）</span>
      </template>

      <div v-loading="loading" class="timetable-scroll">
        <div class="timetable">
          <div class="tt-cell tt-head">节次</div>
          <div
            v-for="day in WEEKDAY_OPTIONS"
            :key="`h-${day.value}`"
            class="tt-cell tt-head"
          >
            {{ day.label }}
          </div>

          <template v-for="row in grid" :key="`r-${row.section}`">
            <div class="tt-cell tt-section">{{ row.section }}</div>
            <template v-for="cell in row.cells" :key="`c-${row.section}-${cell.weekday}`">
              <div
                v-if="cell.type === 'block'"
                class="tt-cell tt-block"
                :style="{ gridRow: `span ${cell.span}` }"
                :title="`${cell.course.courseName} ${cell.course.teacherName || ''} ${cell.course.location || ''}`"
              >
                <div class="tt-block-name">{{ cell.course.courseName }}</div>
                <div class="tt-block-meta">{{ cell.course.location || '-' }}</div>
                <div class="tt-block-meta">{{ cell.course.teacherName || '-' }}</div>
              </div>
              <div v-else-if="cell.type === 'skip'" class="tt-cell tt-skip" />
              <div v-else class="tt-cell" />
            </template>
          </template>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dropCourse, fetchMySelections } from '@/api/student'
import { WEEKDAY_OPTIONS, courseTypeTag, formatWeekday } from '@/utils/dict'

const selections = ref([])
const loading = ref(false)

const MAX_SECTION = computed(() => {
  let max = 12
  selections.value.forEach((item) => {
    const end = Number(item.endSection)
    if (!Number.isNaN(end) && end > max) max = end
  })
  return max
})

const totalCredit = computed(() =>
  selections.value
    .reduce((sum, item) => sum + (Number(item.credit) || 0), 0)
    .toFixed(1)
)

/** 构建周课表网格 */
const grid = computed(() => {
  const rows = []
  for (let section = 1; section <= MAX_SECTION.value; section += 1) {
    const cells = []
    for (let weekday = 1; weekday <= 7; weekday += 1) {
      const covering = selections.value.find((item) => {
        const start = Number(item.startSection)
        const end = Number(item.endSection)
        return (
          Number(item.weekday) === weekday &&
          !Number.isNaN(start) &&
          !Number.isNaN(end) &&
          section > start &&
          section <= end
        )
      })
      if (covering) {
        cells.push({ type: 'skip', weekday })
        continue
      }

      const starting = selections.value.find((item) => {
        const start = Number(item.startSection)
        const end = Number(item.endSection)
        return (
          Number(item.weekday) === weekday &&
          !Number.isNaN(start) &&
          !Number.isNaN(end) &&
          start === section
        )
      })

      if (starting) {
        const span = Math.max(1, Number(starting.endSection) - Number(starting.startSection) + 1)
        cells.push({
          type: 'block',
          weekday,
          span,
          course: {
            courseName: starting.courseName || starting.name || '-',
            teacherName: starting.teacherName,
            location: starting.location
          }
        })
      } else {
        cells.push({ type: 'empty', weekday })
      }
    }
    rows.push({ section, cells })
  }
  return rows
})

async function loadList() {
  loading.value = true
  try {
    const res = await fetchMySelections()
    selections.value = res?.data || []
  } catch (e) {
    selections.value = []
  } finally {
    loading.value = false
  }
}

async function handleDrop(row) {
  try {
    await ElMessageBox.confirm(
      `确定要退选「${row.courseName}」吗？`,
      '退课确认',
      { confirmButtonText: '确定退课', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }

  try {
    const res = await dropCourse(row.courseId)
    ElMessage.success(res?.message || '退课成功')
    loadList()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(loadList)
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
  color: #409eff;
}

.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}

.timetable-scroll {
  overflow-x: auto;
}

.timetable {
  display: grid;
  grid-template-columns: 64px repeat(7, minmax(120px, 1fr));
  border-top: 1px solid #ebeef5;
  border-left: 1px solid #ebeef5;
  min-width: 900px;
}

.tt-cell {
  border-right: 1px solid #ebeef5;
  border-bottom: 1px solid #ebeef5;
  min-height: 58px;
  padding: 4px 6px;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.tt-head {
  background: #f5f7fa;
  font-weight: 600;
  text-align: center;
  align-items: center;
  min-height: 38px;
}

.tt-section {
  background: #fafafa;
  text-align: center;
  align-items: center;
  font-weight: 600;
  min-height: 58px;
}

.tt-block {
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
  color: #1f4e9c;
  margin: 2px;
  overflow: hidden;
}

.tt-block-name {
  font-weight: 600;
  line-height: 1.3;
}

.tt-block-meta {
  color: #5a7fb5;
  line-height: 1.4;
}

.tt-skip {
  background: #f7f9fc;
}
</style>
