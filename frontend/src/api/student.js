import request from './request'

/** 选课大厅：当前学期可选课程（含 selected 标记） */
export function fetchSelectableCourses(params) {
  return request.get('/student/courses', { params })
}

/** 选课 */
export function selectCourse(courseId) {
  return request.post('/student/selections', { courseId })
}

/** 退课 */
export function dropCourse(courseId) {
  return request.delete(`/student/selections/${courseId}`)
}

/** 我的课表 */
export function fetchMySelections() {
  return request.get('/student/selections')
}

/** 我的成绩 */
export function fetchMyScores() {
  return request.get('/student/scores')
}

/** 我的学籍信息 */
export function fetchStudentProfile() {
  return request.get('/student/profile')
}
