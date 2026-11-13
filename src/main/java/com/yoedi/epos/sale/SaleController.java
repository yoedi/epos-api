package com.yoedi.epos.sale;

import com.yoedi.epos.auth.AuthenticatedUser;
import com.yoedi.epos.sale.dto.CreateSaleRequest;
import com.yoedi.epos.sale.dto.SaleDetailResponse;
import com.yoedi.epos.sale.dto.SalePageResponse;
import com.yoedi.epos.sale.dto.SaleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    // The cashier always comes from the verified JWT, never from the request.
    @PostMapping
    public ResponseEntity<SaleResponse> checkout(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateSaleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(saleService.checkout(request, principal.id()));
    }

    // Both roles may call this; the service limits a CASHIER to their own sales of today.
    @GetMapping
    public SalePageResponse findHistory(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "cashier_id", required = false) Long cashierId,
            @RequestParam(name = "invoice_no", required = false) String invoiceNo,
            // @SortDefault, not @PageableDefault: the latter hard-codes size=10 and ignores the global default of 20
            @SortDefault(sort = {"created_at", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return saleService.findHistory(startDate, endDate, cashierId, invoiceNo,
                principal.id(), principal.role(), pageable);
    }

    // Both roles may call this; a CASHIER gets 404 for any sale that is not their own of today.
    @GetMapping("/{id}")
    public SaleDetailResponse findDetail(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @PathVariable long id) {
        return saleService.findDetail(id, principal.id(), principal.role());
    }
}
