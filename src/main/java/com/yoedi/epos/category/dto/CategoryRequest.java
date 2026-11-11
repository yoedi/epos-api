package com.yoedi.epos.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload shared by create and update ("name" is the only editable field). */
public record CategoryRequest(
        @NotBlank(message = "Nama kategori wajib diisi")
        @Size(max = 100, message = "Nama kategori maksimal 100 karakter")
        String name
) {
}
