package io.leavesfly.stock.presentation.api.dto;

/**
 * 通用 API 响应包装
 */
public class ApiResponse<T> {
    private String status;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.status = "success";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> accepted(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.status = "accepted";
        r.message = message;
        return r;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.status = "error";
        r.message = message;
        return r;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
