package com.hc.aiagenthyc.common.error;

/** 只携带可安全返回客户端的描述，不包含供应商响应、Key 或本地路径。 */
public final class ChatException extends IllegalArgumentException {
    private final ErrorCode code;
    public ChatException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
    public ErrorCode code() { return code; }
}
