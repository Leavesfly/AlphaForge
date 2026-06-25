package io.leavesfly.stock.domain.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DataProviderType 数据源枚举测试")
class DataProviderTypeTest {

    @Nested
    @DisplayName("fromCode - 根据code获取枚举")
    class FromCodeTests {

        @Test
        @DisplayName("efinance返回EFINANCE")
        void efinanceCode() {
            assertEquals(DataProviderType.EFINANCE, DataProviderType.fromCode("efinance"));
        }

        @Test
        @DisplayName("akshare返回AKSHARE")
        void akshareCode() {
            assertEquals(DataProviderType.AKSHARE, DataProviderType.fromCode("akshare"));
        }

        @Test
        @DisplayName("tushare返回TUSHARE")
        void tushareCode() {
            assertEquals(DataProviderType.TUSHARE, DataProviderType.fromCode("tushare"));
        }

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive() {
            assertEquals(DataProviderType.AKSHARE, DataProviderType.fromCode("AKSHARE"));
            assertEquals(DataProviderType.TUSHARE, DataProviderType.fromCode("Tushare"));
        }

        @Test
        @DisplayName("未知code默认返回AKSHARE")
        void unknownCodeReturnsAkshare() {
            assertEquals(DataProviderType.AKSHARE, DataProviderType.fromCode("unknown_provider"));
        }
    }

    @Nested
    @DisplayName("枚举属性验证")
    class EnumPropertyTests {

        @Test
        @DisplayName("getCode返回正确值")
        void getCodeValues() {
            assertEquals("efinance", DataProviderType.EFINANCE.getCode());
            assertEquals("akshare", DataProviderType.AKSHARE.getCode());
            assertEquals("tencent", DataProviderType.TENCENT.getCode());
        }

        @Test
        @DisplayName("getName返回名称")
        void getNameValues() {
            assertEquals("efinance数据源", DataProviderType.EFINANCE.getName());
            assertEquals("AKShare数据源", DataProviderType.AKSHARE.getName());
            assertEquals("腾讯数据源", DataProviderType.TENCENT.getName());
        }

        @Test
        @DisplayName("所有数据源均支持实时行情")
        void allSupportRealtime() {
            for (DataProviderType type : DataProviderType.values()) {
                assertTrue(type.isSupportsRealtime(), type.name() + " should support realtime");
            }
        }
    }
}
