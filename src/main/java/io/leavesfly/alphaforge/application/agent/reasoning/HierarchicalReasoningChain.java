package io.leavesfly.alphaforge.application.agent.reasoning;

import java.util.List;

/**
 * 分层推理链 — 从扁平 6 步升级为 4 层 × 3 步的分层推理框架
 *
 * 对应论文 Trading-R1 和 RETuning 的核心思想：
 * 通过深度推理（inference-time scaling）提升 LLM 量化分析的准确性和可解释性。
 *
 * 四层推理架构：
 * Layer 1: 宏观环境 — 市场大势、流动性、风险偏好
 * Layer 2: 行业逻辑 — 板块周期、竞争格局、催化剂
 * Layer 3: 个股因子 — 技术面、基本面、估值、量价
 * Layer 4: 综合判断 — 风险收益比、择时、仓位建议
 *
 * 每层包含 3 个微步骤：
 * 1. 观察（Observe）— 客观数据
 * 2. 推断（Infer）— 基于数据的判断
 * 3. 验证（Verify）— 交叉验证 + 风险检查
 *
 * 设计理念：
 * - 上层结论影响下层分析权重（如熊市时风控权重提升）
 * - 不允许跳层推理（必须先宏观后个股）
 * - 每层结论必须有数据支撑
 */
public class HierarchicalReasoningChain {

    /** 推理层列表（按层级顺序） */
    private final List<ReasoningLayer> layers;

    public HierarchicalReasoningChain(List<ReasoningLayer> layers) {
        this.layers = layers;
    }

    public List<ReasoningLayer> getLayers() { return layers; }

    /**
     * 创建标准分层推理链
     */
    public static HierarchicalReasoningChain standard() {
        return new HierarchicalReasoningChain(List.of(
                // Layer 1: 宏观环境
                new ReasoningLayer("L1", "宏观环境", """
                        分析当前宏观经济和市场环境，确定大方向：
                        • 市场处于什么阶段？（牛市/熊市/震荡）
                        • 流动性环境如何？（宽松/收紧/中性）
                        • 市场风险偏好高低？（追涨/避险/观望）
                        • 当前环境下哪类策略更有效？

                        推理要求：
                        - 客观陈述宏观数据（指数走势、成交量、北向资金等）
                        - 基于数据推断市场阶段
                        - 确定当前环境下各分析维度的权重
                        """),

                // Layer 2: 行业逻辑
                new ReasoningLayer("L2", "行业逻辑", """
                        基于宏观环境，分析标的所在行业的逻辑：
                        • 行业处于什么周期位置？（成长期/成熟期/衰退期）
                        • 行业是否有政策催化或利空？
                        • 板块相对强弱如何？（跑赢/跑输大盘）
                        • 行业内竞争格局如何？（集中度/差异化）

                        推理要求：
                        - 引用行业数据（板块涨跌、PE 分位等）
                        - 判断行业是否有结构性机会
                        - 如无行业数据，可标注"数据不足，基于宏观推断"
                        """),

                // Layer 3: 个股因子
                new ReasoningLayer("L3", "个股因子", """
                        基于行业判断，深入分析个股的多维度因子：
                        • 技术面：趋势方向、关键位置、量价配合
                        • 基本面：估值水平、盈利质量、成长性
                        • 资金面：主力动向、机构持仓变化
                        • 催化剂：近期是否有事件驱动（财报、重组等）

                        推理要求：
                        - 每个因子维度需引用至少 1 个具体数据点
                        - 技术面需明确趋势方向和关键支撑/阻力位
                        - 如因子间信号矛盾，需指出矛盾点
                        - 形成个股层面的初步假设
                        """),

                // Layer 4: 综合判断
                new ReasoningLayer("L4", "综合判断", """
                        综合前三层分析，形成最终投资决策：
                        • 各层结论是否一致？（宏观/行业/个股共振 or 分歧）
                        • 风险收益比如何评估？
                        • 择时判断：当前是否是好的入场时机？
                        • 仓位建议：基于置信度和风险，建议多大仓位？

                        推理要求：
                        - 明确说明采纳和否决的分析观点
                        - 风控 Agent 的反对意见需被特别重视
                        - 给出明确的信号、评分、置信度
                        - 提供入场价、止损价、目标价
                        """)
        ));
    }

    /**
     * 推理层 — 一个分析层级
     */
    public static class ReasoningLayer {
        private final String layerId;
        private final String layerName;
        private final String instruction;

        public ReasoningLayer(String layerId, String layerName, String instruction) {
            this.layerId = layerId;
            this.layerName = layerName;
            this.instruction = instruction;
        }

        public String getLayerId() { return layerId; }
        public String getLayerName() { return layerName; }
        public String getInstruction() { return instruction; }
    }

    /**
     * 构建分层推理链的 JSON 输出格式
     */
    public static String buildOutputFormat() {
        return """
               ```json
               {
                 "reasoning": {
                   "macro": {
                     "observation": "宏观数据描述",
                     "inference": "市场阶段判断",
                     "weight_adjustment": "各维度权重调整（如熊市风控权重↑）"
                   },
                   "industry": {
                     "observation": "行业数据描述",
                     "inference": "行业逻辑判断",
                     "catalyst": "行业催化剂/利空"
                   },
                   "stock": {
                     "technical": "技术面分析（趋势/位置/量价）",
                     "fundamental": "基本面分析（估值/盈利/成长）",
                     "hypothesis": "个股假设",
                     "evidence": ["证据1", "证据2"],
                     "contradiction": "因子间矛盾点（如有）"
                   },
                   "synthesis": {
                     "consistency": "各层一致性分析",
                     "risk_reward": "风险收益比评估",
                     "timing": "择时判断",
                     "position_sizing": "仓位建议"
                   }
                 },
                 "signal": "strong_buy/buy/neutral/sell/strong_sell",
                 "score": 0-100,
                 "confidence": "高/中等/低",
                 "entry_price": "建议入场价",
                 "stop_loss": "止损价",
                 "target_price": "目标价",
                 "key_findings": ["关键发现1", "关键发现2"]
               }
               ```
               """;
    }
}
