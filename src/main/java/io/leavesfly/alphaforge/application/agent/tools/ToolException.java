package io.leavesfly.alphaforge.application.agent.tools;

/**
 * 工具执行异常
 *
 * 统一工具执行过程中的错误处理，包括：
 * - 参数校验失败
 * - 数据获取失败
 * - 分析计算失败
 * - 回测执行失败
 */
public class ToolException extends RuntimeException {

    private final String errorCode;

    public ToolException(String message) {
        super(message);
        this.errorCode = "TOOL_ERROR";
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "TOOL_ERROR";
    }

    public ToolException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ToolException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
