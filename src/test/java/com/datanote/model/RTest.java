package com.datanote.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RTest {

    @Test
    void ok_noData_shouldReturnCodeZero() {
        R<Void> r = R.ok();
        assertEquals(0, r.getCode());
        assertEquals("success", r.getMsg());
        assertNull(r.getData());
    }

    @Test
    void ok_withData_shouldReturnCodeZeroAndData() {
        R<String> r = R.ok("hello");
        assertEquals(0, r.getCode());
        assertEquals("success", r.getMsg());
        assertEquals("hello", r.getData());
    }

    @Test
    void fail_withMessage_shouldReturnCodeMinusOne() {
        R<Void> r = R.fail("something wrong");
        assertEquals(-1, r.getCode());
        assertEquals("something wrong", r.getMsg());
        assertNull(r.getData());
    }

    @Test
    void fail_withCustomCode_shouldReturnThatCode() {
        R<Void> r = R.fail(-404, "not found");
        assertEquals(-404, r.getCode());
        assertEquals("not found", r.getMsg());
    }

    @Test
    void fail_withBadRequestCode() {
        R<Void> r = R.fail(R.CODE_BAD_REQUEST, "bad request");
        assertEquals(-400, r.getCode());
    }

    @Test
    void fail_withUnauthorizedCode() {
        R<Void> r = R.fail(R.CODE_UNAUTHORIZED, "unauthorized");
        assertEquals(-401, r.getCode());
    }
}
