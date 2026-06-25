package io.leavesfly.stock.domain.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Signal 信号枚举测试")
class SignalTest {

    @Nested
    @DisplayName("fromCode - 根据code获取枚举")
    class FromCodeTests {

        @Test
        @DisplayName("strong_buy代码返回STRONG_BUY")
        void strongBuyCode() {
            assertEquals(Signal.STRONG_BUY, Signal.fromCode("strong_buy"));
        }

        @Test
        @DisplayName("buy代码返回BUY")
        void buyCode() {
            assertEquals(Signal.BUY, Signal.fromCode("buy"));
        }

        @Test
        @DisplayName("neutral代码返回NEUTRAL")
        void neutralCode() {
            assertEquals(Signal.NEUTRAL, Signal.fromCode("neutral"));
        }

        @Test
        @DisplayName("sell代码返回SELL")
        void sellCode() {
            assertEquals(Signal.SELL, Signal.fromCode("sell"));
        }

        @Test
        @DisplayName("strong_sell代码返回STRONG_SELL")
        void strongSellCode() {
            assertEquals(Signal.STRONG_SELL, Signal.fromCode("strong_sell"));
        }

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive() {
            assertEquals(Signal.BUY, Signal.fromCode("BUY"));
            assertEquals(Signal.BUY, Signal.fromCode("Buy"));
        }

        @Test
        @DisplayName("未知code返回NEUTRAL")
        void unknownCodeReturnsNeutral() {
            assertEquals(Signal.NEUTRAL, Signal.fromCode("unknown_code"));
        }
    }

    @Nested
    @DisplayName("fromScore - 根据评分获取枚举")
    class FromScoreTests {

        @Test
        @DisplayName("score>=4.5返回STRONG_BUY")
        void scoreAbove45ReturnsStrongBuy() {
            assertEquals(Signal.STRONG_BUY, Signal.fromScore(4.5));
            assertEquals(Signal.STRONG_BUY, Signal.fromScore(5.0));
        }

        @Test
        @DisplayName("3.5<=score<4.5返回BUY")
        void score35To45ReturnsBuy() {
            assertEquals(Signal.BUY, Signal.fromScore(3.5));
            assertEquals(Signal.BUY, Signal.fromScore(4.0));
        }

        @Test
        @DisplayName("2.5<=score<3.5返回WEAK_BUY")
        void score25To35ReturnsWeakBuy() {
            assertEquals(Signal.WEAK_BUY, Signal.fromScore(2.5));
            assertEquals(Signal.WEAK_BUY, Signal.fromScore(3.0));
        }

        @Test
        @DisplayName("1.5<=score<2.5返回NEUTRAL")
        void score15To25ReturnsNeutral() {
            assertEquals(Signal.NEUTRAL, Signal.fromScore(1.5));
            assertEquals(Signal.NEUTRAL, Signal.fromScore(2.0));
        }

        @Test
        @DisplayName("0.5<=score<1.5返回WEAK_SELL")
        void score05To15ReturnsWeakSell() {
            assertEquals(Signal.WEAK_SELL, Signal.fromScore(0.5));
            assertEquals(Signal.WEAK_SELL, Signal.fromScore(1.0));
        }

        @Test
        @DisplayName("-0.5<=score<0.5返回SELL")
        void scoreNeg05To05ReturnsSell() {
            assertEquals(Signal.SELL, Signal.fromScore(0.0));
            assertEquals(Signal.SELL, Signal.fromScore(-0.4));
        }

        @Test
        @DisplayName("score<-0.5返回STRONG_SELL")
        void scoreBelowNeg05ReturnsStrongSell() {
            assertEquals(Signal.STRONG_SELL, Signal.fromScore(-1.0));
            assertEquals(Signal.STRONG_SELL, Signal.fromScore(-0.6));
        }
    }

    @Nested
    @DisplayName("枚举属性验证")
    class EnumPropertyTests {

        @Test
        @DisplayName("STRONG_BUY分数为5")
        void strongBuyScore() {
            assertEquals(5, Signal.STRONG_BUY.getScore());
        }

        @Test
        @DisplayName("STRONG_SELL分数为-1")
        void strongSellScore() {
            assertEquals(-1, Signal.STRONG_SELL.getScore());
        }

        @Test
        @DisplayName("getCode返回正确值")
        void getCodeValues() {
            assertEquals("strong_buy", Signal.STRONG_BUY.getCode());
            assertEquals("neutral", Signal.NEUTRAL.getCode());
            assertEquals("strong_sell", Signal.STRONG_SELL.getCode());
        }

        @Test
        @DisplayName("getLabel返回中文标签")
        void getLabelValues() {
            assertEquals("强烈买入", Signal.STRONG_BUY.getLabel());
            assertEquals("中性", Signal.NEUTRAL.getLabel());
            assertEquals("强烈卖出", Signal.STRONG_SELL.getLabel());
        }
    }
}
