package io.leavesfly.alphaforge.presentation.api.dto;

import io.leavesfly.alphaforge.domain.model.entity.watchlist.WatchlistItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自选股响应 DTO：隔离 API 出参与领域实体。
 */
public record WatchlistItemDto(Long id, String stockCode, String stockName, String market, LocalDateTime addedAt) {

    public static WatchlistItemDto from(WatchlistItem item) {
        return new WatchlistItemDto(item.getId(), item.getStockCode(), item.getStockName(),
                item.getMarket(), item.getAddedAt());
    }

    public static List<WatchlistItemDto> from(List<WatchlistItem> list) {
        return list == null ? List.of() : list.stream().map(WatchlistItemDto::from).toList();
    }
}
