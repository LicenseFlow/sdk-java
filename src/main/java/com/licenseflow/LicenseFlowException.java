package com.licenseflow;

public class LicenseFlowException extends RuntimeException {
    private String code;
    private int status;

    public LicenseFlowException(String message, String code, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public int getStatus() { return status; }
}
