package com.school.edu.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 总记录数 */
    private long total;
    /** 当前页码，从 1 开始 */
    private long page;
    /** 每页条数 */
    private long size;
    /** 当前页数据 */
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public static <S, T> PageResult<T> of(IPage<S> page, Function<S, T> mapper) {
        List<T> list = page.getRecords().stream().map(mapper).toList();
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), list);
    }

    public static <T> PageResult<T> of(long total, long page, long size, List<T> records) {
        return new PageResult<>(total, page, size, records);
    }
}
