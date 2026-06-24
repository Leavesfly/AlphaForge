package io.leavesfly.stock.application.service;

import io.leavesfly.stock.domain.model.entity.PortfolioTrade;
import io.leavesfly.stock.domain.model.entity.CashLedgerEntry;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * CSV导入解析服务
 * 支持多券商格式的交易记录导入
 */
@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private static final Map<String, BrokerParser> BROKER_PARSERS = Map.of(
        "eastmoney", new EastMoneyParser(),
        "tonghuashun", new TongHuaShunParser(),
        "futu", new FutuParser(),
        "tiger", new TigerParser(),
        "longbridge", new LongbridgeParser(),
        "huatai", new HuaTaiParser(),
        "citic", new GenericParser("中信"),
        "cmb", new GenericParser("招商")
    );

    /**
     * 解析CSV文件，返回预览数据
     */
    public Map<String, Object> parseCsv(String broker, MultipartFile file) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("broker", broker);
        result.put("file_name", file.getOriginalFilename());

        BrokerParser parser = BROKER_PARSERS.getOrDefault(broker, new GenericParser(broker));
        List<Map<String, Object>> preview = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int totalRows = 0;
        int validRows = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), detectCharset(file))) {
            CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim().parse(reader);
            for (CSVRecord record : csvParser) {
                totalRows++;
                try {
                    Map<String, Object> row = parser.parseRow(record);
                    if (row != null) {
                        validRows++;
                        if (preview.size() < 20) preview.add(row);
                    }
                } catch (Exception e) {
                    errors.add("第" + (totalRows + 1) + "行: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            errors.add("文件读取失败: " + e.getMessage());
            log.error("CSV解析失败: broker={}, file={}", broker, file.getOriginalFilename(), e);
        }

        result.put("total_rows", totalRows);
        result.put("valid_rows", validRows);
        result.put("preview", preview);
        result.put("errors", errors);
        return result;
    }

    /**
     * 提交CSV导入，创建交易和现金流水记录
     */
    public Map<String, Object> commitCsv(Long accountId, String broker, MultipartFile file, boolean dryRun,
                                          PortfolioExtService extService) {
        BrokerParser parser = BROKER_PARSERS.getOrDefault(broker, new GenericParser(broker));
        int importedTrades = 0;
        int importedCash = 0;
        int skipped = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), detectCharset(file))) {
            CSVParser csvParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim().parse(reader);
            for (CSVRecord record : csvParser) {
                try {
                    Map<String, Object> row = parser.parseRow(record);
                    if (row == null) { skipped++; continue; }

                    String type = (String) row.getOrDefault("type", "trade");
                    if ("trade".equals(type)) {
                        if (!dryRun) {
                            PortfolioTrade trade = new PortfolioTrade();
                            trade.setAccountId(accountId);
                            trade.setSymbol((String) row.get("symbol"));
                            trade.setTradeDate(LocalDate.parse((String) row.get("trade_date")));
                            trade.setSide((String) row.get("side"));
                            trade.setQuantity(((Number) row.get("quantity")).doubleValue());
                            trade.setPrice(((Number) row.get("price")).doubleValue());
                            trade.setFee(row.get("fee") != null ? ((Number) row.get("fee")).doubleValue() : 0);
                            trade.setTax(row.get("tax") != null ? ((Number) row.get("tax")).doubleValue() : 0);
                            trade.setCurrency((String) row.getOrDefault("currency", "CNY"));
                            extService.createTrade(trade);
                        }
                        importedTrades++;
                    } else if ("cash".equals(type)) {
                        if (!dryRun) {
                            CashLedgerEntry entry = new CashLedgerEntry();
                            entry.setAccountId(accountId);
                            entry.setEventDate(LocalDate.parse((String) row.get("event_date")));
                            entry.setDirection((String) row.get("direction"));
                            entry.setAmount(((Number) row.get("amount")).doubleValue());
                            entry.setCurrency((String) row.getOrDefault("currency", "CNY"));
                            entry.setNote((String) row.get("note"));
                            extService.createCashEntry(entry);
                        }
                        importedCash++;
                    }
                } catch (Exception e) {
                    skipped++;
                }
            }
        } catch (Exception e) {
            log.error("CSV导入执行失败: broker={}", broker, e);
        }

        return Map.of(
            "status", dryRun ? "dry_run" : "committed",
            "account_id", accountId,
            "broker", broker,
            "imported_trades", importedTrades,
            "imported_cash", importedCash,
            "skipped", skipped
        );
    }

    private Charset detectCharset(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] bom = new byte[3];
            int read = is.read(bom);
            if (read >= 3 && bom[0] == (byte)0xEF && bom[1] == (byte)0xBB && bom[2] == (byte)0xBF) {
                return StandardCharsets.UTF_8;
            }
            // 尝试检测GBK
            byte[] sample = new byte[1024];
            is.read(sample);
            for (byte b : sample) {
                if ((b & 0xFF) > 0x7F) return Charset.forName("GBK");
            }
        } catch (Exception ignored) {}
        return StandardCharsets.UTF_8;
    }

    // ========== 券商解析器 ==========
    interface BrokerParser {
        Map<String, Object> parseRow(CSVRecord record);
    }

    static class EastMoneyParser implements BrokerParser {
        public Map<String, Object> parseRow(CSVRecord record) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "trade");
            row.put("symbol", getField(record, "证券代码", "股票代码", "代码"));
            row.put("trade_date", parseDate(getField(record, "成交日期", "交易日期", "日期")));
            String direction = getField(record, "买卖方向", "交易方向", "方向");
            row.put("side", direction != null && direction.contains("买") ? "buy" : "sell");
            row.put("quantity", parseDouble(getField(record, "成交数量", "数量")));
            row.put("price", parseDouble(getField(record, "成交价格", "价格", "成交均价")));
            row.put("fee", parseDouble(getField(record, "手续费", "佣金")));
            row.put("tax", parseDouble(getField(record, "印花税", "税费")));
            row.put("currency", "CNY");
            return validate(row) ? row : null;
        }
    }

    static class TongHuaShunParser implements BrokerParser {
        public Map<String, Object> parseRow(CSVRecord record) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "trade");
            row.put("symbol", getField(record, "证券代码", "股票代码"));
            row.put("trade_date", parseDate(getField(record, "成交日期", "日期")));
            String direction = getField(record, "操作", "买卖", "交易类型");
            row.put("side", direction != null && (direction.contains("买") || direction.contains("Buy")) ? "buy" : "sell");
            row.put("quantity", parseDouble(getField(record, "成交数量", "数量")));
            row.put("price", parseDouble(getField(record, "成交价格", "均价")));
            row.put("fee", parseDouble(getField(record, "手续费", "佣金", "费用")));
            row.put("currency", "CNY");
            return validate(row) ? row : null;
        }
    }

    static class FutuParser implements BrokerParser {
        public Map<String, Object> parseRow(CSVRecord record) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "trade");
            row.put("symbol", getField(record, "Code", "股票代码", "代码"));
            row.put("trade_date", parseDate(getField(record, "Date", "日期", "成交日期")));
            String side = getField(record, "Side", "方向", "买卖");
            row.put("side", side != null && (side.equalsIgnoreCase("buy") || side.contains("买")) ? "buy" : "sell");
            row.put("quantity", parseDouble(getField(record, "Qty", "数量", "成交数量")));
            row.put("price", parseDouble(getField(record, "Price", "价格", "成交价格")));
            row.put("fee", parseDouble(getField(record, "Commission", "佣金", "手续费")));
            row.put("currency", getField(record, "Currency", "币种") != null ? getField(record, "Currency", "币种") : "HKD");
            return validate(row) ? row : null;
        }
    }

    static class TigerParser implements BrokerParser {
        public Map<String, Object> parseRow(CSVRecord record) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "trade");
            row.put("symbol", getField(record, "Symbol", "股票代码"));
            row.put("trade_date", parseDate(getField(record, "Trade Date", "Date", "日期")));
            String action = getField(record, "Action", "Side", "方向");
            row.put("side", action != null && action.toLowerCase().contains("buy") ? "buy" : "sell");
            row.put("quantity", parseDouble(getField(record, "Quantity", "Qty", "数量")));
            row.put("price", parseDouble(getField(record, "Price", "价格")));
            row.put("fee", parseDouble(getField(record, "Commission", "Fee")));
            row.put("currency", "USD");
            return validate(row) ? row : null;
        }
    }

    static class LongbridgeParser implements BrokerParser {
        public Map<String, Object> parseRow(CSVRecord record) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "trade");
            row.put("symbol", getField(record, "股票代码", "Symbol", "代码"));
            row.put("trade_date", parseDate(getField(record, "成交时间", "日期", "Date")));
            String side = getField(record, "方向", "Side", "买卖方向");
            row.put("side", side != null && (side.contains("买") || side.equalsIgnoreCase("buy")) ? "buy" : "sell");
            row.put("quantity", parseDouble(getField(record, "成交数量", "Qty", "数量")));
            row.put("price", parseDouble(getField(record, "成交价格", "Price", "价格")));
            row.put("fee", parseDouble(getField(record, "手续费", "Commission")));
            row.put("currency", "HKD");
            return validate(row) ? row : null;
        }
    }

    static class HuaTaiParser implements BrokerParser {
        public Map<String, Object> parseRow(CSVRecord record) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "trade");
            row.put("symbol", getField(record, "证券代码", "股票代码"));
            row.put("trade_date", parseDate(getField(record, "成交日期", "交易日期")));
            String direction = getField(record, "买卖标志", "买卖方向", "操作");
            row.put("side", direction != null && direction.contains("买") ? "buy" : "sell");
            row.put("quantity", parseDouble(getField(record, "成交数量", "数量")));
            row.put("price", parseDouble(getField(record, "成交价格", "成交均价")));
            row.put("fee", parseDouble(getField(record, "手续费", "佣金")));
            row.put("tax", parseDouble(getField(record, "印花税")));
            row.put("currency", "CNY");
            return validate(row) ? row : null;
        }
    }

    static class GenericParser implements BrokerParser {
        private final String brokerName;
        GenericParser(String name) { this.brokerName = name; }

        public Map<String, Object> parseRow(CSVRecord record) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "trade");
            row.put("symbol", getField(record, "证券代码", "股票代码", "代码", "Code", "Symbol"));
            row.put("trade_date", parseDate(getField(record, "成交日期", "交易日期", "日期", "Date", "Trade Date")));
            String side = getField(record, "买卖方向", "方向", "操作", "Side", "Action", "买卖标志");
            row.put("side", side != null && (side.contains("买") || side.toLowerCase().contains("buy")) ? "buy" : "sell");
            row.put("quantity", parseDouble(getField(record, "成交数量", "数量", "Qty", "Quantity")));
            row.put("price", parseDouble(getField(record, "成交价格", "价格", "Price", "成交均价")));
            row.put("fee", parseDouble(getField(record, "手续费", "佣金", "Fee", "Commission")));
            row.put("currency", "CNY");
            return validate(row) ? row : null;
        }
    }

    // ========== 工具方法 ==========
    private static String getField(CSVRecord record, String... possibleHeaders) {
        for (String header : possibleHeaders) {
            try {
                String val = record.get(header);
                if (val != null && !val.trim().isEmpty()) return val.trim();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Double parseDouble(String val) {
        if (val == null || val.isEmpty()) return 0.0;
        val = val.replaceAll("[^\\d.\\-]", "");
        try { return Double.parseDouble(val); } catch (Exception e) { return 0.0; }
    }

    private static String parseDate(String val) {
        if (val == null || val.isEmpty()) return LocalDate.now().toString();
        String cleaned = val.replaceAll("[/年月]", "-").replaceAll("[日号]", "").trim();
        if (cleaned.length() > 10) cleaned = cleaned.substring(0, 10);
        String[] formats = {"yyyy-MM-dd", "yyyyMMdd", "yyyy-M-d", "MM-dd-yyyy", "dd-MM-yyyy"};
        for (String fmt : formats) {
            try { return LocalDate.parse(cleaned, DateTimeFormatter.ofPattern(fmt)).toString(); } catch (DateTimeParseException ignored) {}
        }
        try { return LocalDate.parse(cleaned).toString(); } catch (Exception e) { return LocalDate.now().toString(); }
    }

    private static boolean validate(Map<String, Object> row) {
        return row.get("symbol") != null && row.get("quantity") != null
                && ((Number) row.get("quantity")).doubleValue() > 0;
    }
}
