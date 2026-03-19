package com.camunda.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderItemDto {

    @NotBlank
    private String productId;

    @NotBlank
    private String productName;

    @Min(1)
    private int quantity;

    private BigDecimal unitPrice;
}
