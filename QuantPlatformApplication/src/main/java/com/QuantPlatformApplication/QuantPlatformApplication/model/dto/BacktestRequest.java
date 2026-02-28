package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BacktestRequest {

    @NotNull(message = "Strategy ID is required")
    private Long strategyId;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private Double initialCapital = 100_000.0;
}
