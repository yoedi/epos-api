package com.yoedi.epos.sale;

import com.yoedi.epos.auth.AuthenticatedUser;
import com.yoedi.epos.sale.dto.CreateSaleRequest;
import com.yoedi.epos.sale.dto.SaleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
