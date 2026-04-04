package com.datanote.model;

import lombok.Data;

/**
 * 统一返回结果
 */
@Data
public class R<T> {

    /** 错误码：0=成功，负数=失败 */
    private int code;
    private String msg;
    private T data;

    /** 常用错误码 */
    public static final int CODE_SUCCESS = 0;
    public static final int CODE_FAIL = -1;
    public static final int CODE_NOT_FOUND = -404;
    public static final int CODE_UNAUTHORIZED = -401;
    public static final int CODE_BAD_REQUEST = -400;

    /**
     * 构建成功响应（带数据）
     *
     * @param data 响应数据
     * @return 成功响应
     */
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(CODE_SUCCESS);
        r.setMsg("success");
        r.setData(data);
        return r;
    }

    /**
     * 构建成功响应（无数据）
     *
     * @return 成功响应
     */
    public static <T> R<T> ok() {
        return ok(null);
    }

    /**
     * 构建失败响应（默认错误码）
     *
     * @param msg 错误信息
     * @return 失败响应
     */
    public static <T> R<T> fail(String msg) {
        R<T> r = new R<>();
        r.setCode(CODE_FAIL);
        r.setMsg(msg);
        return r;
    }

    /**
     * 构建失败响应（自定义错误码）
     *
     * @param code 错误码
     * @param msg  错误信息
     * @return 失败响应
     */
    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }
}
