package com.yoedi.epos.stock;

import com.yoedi.epos.auth.AuthenticatedUser;
import com.yoedi.epos.common.dto.PageResponse;
import com.yoedi.epos.stock.dto.StockAdjustmentRequest;
import com.yoedi.epos.stock.dto.StockInRequest;
import com.yoedi.epos.stock.dto.StockInResponse;
import com.yoedi.epos.stock.dto.StockMovementResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** All stock endpoints are OWNER-only (docs/api-spec.md, "Access rights summary"). */
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class StockController {

    private final StockMovementService stockMovementService;

    // The acting user always comes from the verified JWT, never from the request.
    @PostMapping("/in")
    public ResponseEntity<StockInResponse> stockIn(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody StockInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockMovementService.stockIn(request, principal.id()));
    }

    @PostMapping("/adjustment")
    public ResponseEntity<StockMovementResponse> adjust(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockMovementService.adjust(request, principal.id()));
    }

    @GetMapping("/movements")
    public PageResponse<StockMovementResponse> findMovements(
            @RequestParam(name = "product_id", required = false) Long productId,
            @RequestParam(required = false) StockMovementType type,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            // @SortDefault, not @PageableDefault: the latter hard-codes size=10 and ignores the global default of 20
            @SortDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return stockMovementService.findMovements(productId, type, startDate, endDate, pageable);
    }
}
