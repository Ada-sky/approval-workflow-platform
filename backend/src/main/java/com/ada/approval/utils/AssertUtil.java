package com.ada.approval.utils;

import com.ada.approval.exception.ParamException;

/** Validation helper */
public class AssertUtil {
    /**
     * Throw a validation exception with the supplied message when the condition is true
     *
     * @param flag
     * @param msg
     */
    public static void isTrue(Boolean flag, String msg) {
        if (flag) { // When the validation condition is true
            throw new ParamException(msg);
        }
    }
}
