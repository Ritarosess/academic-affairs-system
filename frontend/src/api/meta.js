import request from './request'

/** 教师下拉列表 */
export function fetchTeacherOptions() {
  return request.get('/meta/teachers')
}

/** 学期下拉列表 */
export function fetchSemesterOptions() {
  return request.get('/meta/semesters')
}

/** 年级下拉列表 */
export function fetchGradeOptions() {
  return request.get('/meta/grades')
}

/** 专业下拉列表 */
export function fetchMajorOptions() {
  return request.get('/meta/majors')
}

/** 班级下拉列表 */
export function fetchClazzOptions() {
  return request.get('/meta/clazzs')
}

/** 选课周期状态 */
export function fetchSelectionWindow() {
  return request.get('/meta/selection-window')
}
