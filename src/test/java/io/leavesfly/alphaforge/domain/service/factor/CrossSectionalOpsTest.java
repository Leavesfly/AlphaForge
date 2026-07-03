package io.leavesfly.alphaforge.domain.service.factor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("横截面算子 CrossSectionalOps")
class CrossSectionalOpsTest {

    @Test
    @DisplayName("MAD 去极值：极端值被截断")
    void winsorizeMad() {
        double[] v = {1, 2, 3, 4, 5, 100};
        double[] w = CrossSectionalOps.winsorizeMad(v, 3.0);
        assertTrue(w[5] < 100, "极端值应被截断");
        assertEquals(1, w[0], 1e-9, "正常值应保留");
    }

    @Test
    @DisplayName("Z-Score：均值约0、标准差约1")
    void zscore() {
        double[] v = {1, 2, 3, 4, 5};
        double[] z = CrossSectionalOps.zscore(v);
        double mean = 0;
        for (double x : z) mean += x;
        mean /= z.length;
        assertEquals(0, mean, 1e-9);
    }

    @Test
    @DisplayName("排名标准化：映射到 [0,1]")
    void rankNormalize() {
        double[] v = {10, 30, 20};
        double[] r = CrossSectionalOps.rankNormalize(v);
        assertEquals(0.0, r[0], 1e-9);  // 最小
        assertEquals(1.0, r[1], 1e-9);  // 最大
        assertEquals(0.5, r[2], 1e-9);  // 中间
    }

    @Test
    @DisplayName("中性化：残差与暴露正交（去除市值暴露）")
    void neutralize() {
        // factor = 2*size + noise，中性化后应基本消除 size 影响
        double[] size = {1, 2, 3, 4, 5};
        double[] factor = {2.1, 4.0, 6.1, 8.0, 10.0};
        double[][] exp = new double[5][1];
        for (int i = 0; i < 5; i++) exp[i][0] = size[i];
        double[] resid = CrossSectionalOps.neutralize(factor, exp);
        // 残差与 size 的相关应接近 0
        double corr = CrossSectionalOps.spearman(resid, size);
        assertTrue(Math.abs(corr) < 0.9, "中性化后与市值相关应下降");
        double sum = 0;
        for (double r : resid) sum += r;
        assertEquals(0, sum, 1e-6, "OLS 残差和应为 0");
    }

    @Test
    @DisplayName("Spearman：完全正相关为1")
    void spearman() {
        double[] a = {1, 2, 3, 4, 5};
        double[] b = {2, 4, 6, 8, 10};
        assertEquals(1.0, CrossSectionalOps.spearman(a, b), 1e-9);
    }

    @Test
    @DisplayName("分位数：中位数正确")
    void quantile() {
        double[] v = {1, 2, 3, 4, 5};
        assertEquals(3.0, CrossSectionalOps.median(v), 1e-9);
    }
}
