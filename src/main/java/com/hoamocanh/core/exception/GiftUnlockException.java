package com.hoamocanh.core.exception;

/** Lỗi khi mở khóa quà (sai mã, đang bị khóa...). Không làm hoàn tác transaction để vẫn lưu số lần nhập sai. */
public class GiftUnlockException extends RuntimeException {
    public GiftUnlockException(String message) {
        super(message);
    }
}