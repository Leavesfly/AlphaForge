package io.leavesfly.alphaforge.infrastructure.dataprovider;

/**
 * 数据源不可达异常 — 表示传输层故障，而非「查得到但没数据」。
 *
 * <p>两者必须区分：{@link FetcherFailoverExecutor} 只在捕获到异常时调用
 * {@code recordFailure} 并推进熔断器；若把连接失败、超时、5xx 一律压成空结果返回，
 * 熔断器永不打开，已宕机的主机每次请求都要再付一次完整的连接握手代价，
 * 且日志里看不出「源挂了」与「该股无数据」的差别。</p>
 *
 * <p>因此传输层故障以本异常向上抛出，交由故障切换器统计失败、熔断并切换数据源；
 * 而 JSON 解析失败、字段缺失等数据层问题仍按空结果处理。</p>
 */
public class DataSourceUnavailableException extends RuntimeException {

    public DataSourceUnavailableException(String message) {
        super(message);
    }

    public DataSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
