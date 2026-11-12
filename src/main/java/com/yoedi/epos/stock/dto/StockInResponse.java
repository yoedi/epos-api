package com.yoedi.epos.stock.dto;

import java.util.List;

public record StockInResponse(List<StockMovementResponse> movements) {
}
