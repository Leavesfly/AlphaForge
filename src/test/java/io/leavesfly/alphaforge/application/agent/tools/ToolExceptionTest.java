package io.leavesfly.alphaforge.application.agent.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolException 工具异常测试")
class ToolExceptionTest {

    @Nested
    @DisplayName("构造器测试")
    class ConstructorTests {

        @Test
        @DisplayName("仅message构造器默认errorCode为TOOL_ERROR")
        void messageOnlyConstructor() {
            ToolException ex = new ToolException("something went wrong");
            assertEquals("something went wrong", ex.getMessage());
            assertEquals("TOOL_ERROR", ex.getErrorCode());
        }

        @Test
        @DisplayName("message+cause构造器默认errorCode为TOOL_ERROR")
        void messageAndCauseConstructor() {
            Throwable cause = new RuntimeException("root cause");
            ToolException ex = new ToolException("wrapper error", cause);
            assertEquals("wrapper error", ex.getMessage());
            assertEquals("TOOL_ERROR", ex.getErrorCode());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("message+errorCode构造器")
        void messageAndErrorCodeConstructor() {
            ToolException ex = new ToolException("not found", "TOOL_NOT_FOUND");
            assertEquals("not found", ex.getMessage());
            assertEquals("TOOL_NOT_FOUND", ex.getErrorCode());
        }

        @Test
        @DisplayName("message+cause+errorCode构造器")
        void messageCauseAndErrorCodeConstructor() {
            Throwable cause = new RuntimeException("root");
            ToolException ex = new ToolException("wrapper", cause, "EXEC_ERROR");
            assertEquals("wrapper", ex.getMessage());
            assertEquals("EXEC_ERROR", ex.getErrorCode());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("RuntimeException行为验证")
    class RuntimeExceptionBehaviorTests {

        @Test
        @DisplayName("ToolException是RuntimeException子类")
        void isRuntimeException() {
            ToolException ex = new ToolException("test");
            assertInstanceOf(RuntimeException.class, ex);
        }

        @Test
        @DisplayName("可以被catch(RuntimeException)捕获")
        void canBeCaughtAsRuntimeException() {
            try {
                throw new ToolException("test error", "CUSTOM_CODE");
            } catch (RuntimeException e) {
                assertTrue(e instanceof ToolException);
                assertEquals("CUSTOM_CODE", ((ToolException) e).getErrorCode());
            }
        }
    }
}
