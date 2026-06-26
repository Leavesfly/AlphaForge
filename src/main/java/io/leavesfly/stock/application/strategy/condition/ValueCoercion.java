package io.leavesfly.stock.application.strategy.condition;

import java.util.Map;

/** 类型转换工具，供策略条件引擎共享。 */
public final class ValueCoercion {

    private ValueCoercion() {
    }

    public static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static int intVal(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).replaceAll("[^0-9-]", ""));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static double doubleVal(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    public static int intParam(Map<String, Object> parameters, String key, int defaultValue) {
        return intVal(parameters.get(key), defaultValue);
    }

    public static double doubleParam(Map<String, Object> parameters, String key, double defaultValue) {
        return doubleVal(parameters.get(key), defaultValue);
    }

    public static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
