package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.NotNull;

public record VisibilityReq(@NotNull(message = "Thiếu trạng thái ẩn/hiện.") Boolean hidden) {
}
