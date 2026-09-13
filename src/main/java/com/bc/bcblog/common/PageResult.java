package com.bc.bcblog.common;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private long total;
    private List<T> list;

    public static <T> PageResult<T> of(long total, List<T> list) {
        PageResult<T> r = new PageResult<>();
        r.setTotal(total);
        r.setList(list);
        return r;
    }
}
