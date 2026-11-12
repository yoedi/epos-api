package com.yoedi.epos.product.dto;

import jakarta.validation.constraints.NotNull;

/** Toggle product active state (docs/api-spec.md §3 PATCH /products/{id}/status). */
public record UpdateStatusRequest(
        @NotNull(message = "Status aktif wajib diisi")
        Boolean isActive
) {
}
