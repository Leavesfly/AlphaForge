package io.leavesfly.stock.presentation.api.dto;

/**
 * 统一 API 响应包装。
 */
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;
    private String error;
    private int code;

    public ApiResponse() {
    }

    public ApiResponse(boolean success, T data, String message, String error, int code) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.error = error;
        this.code = code;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, 200);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null, 200);
    }

    public static <T> ApiResponse<T> error(int code, String error) {
        return new ApiResponse<>(false, null, null, error, code);
    }

    public static <T> ApiResponse<T> error(String error) {
        return error(400, error);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
}
