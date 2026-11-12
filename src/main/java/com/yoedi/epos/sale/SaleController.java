package com.yoedi.epos.sale;

import com.yoedi.epos.sale.dto.CreateSaleRequest;
import com.yoedi.epos.sale.dto.SaleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    // TODO(stage 3): drop the X-Cashier-Id header and take the cashier from the authenticated
    //  JWT principal instead. The header exists only because there is no login yet, and must
    //  not survive once auth is in place (anyone could ring up sales as someone else).
    @PostMapping
    public ResponseEntity<SaleResponse> checkout(
            @RequestHeader("X-Cashier-Id") long cashierId,
            @Valid @RequestBody CreateSaleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(saleService.checkout(request, cashierId));
    }
}
