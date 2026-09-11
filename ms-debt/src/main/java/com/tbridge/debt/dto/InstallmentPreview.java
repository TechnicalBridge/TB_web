package com.tbridge.debt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentPreview(int number, LocalDate dueDate, BigDecimal amount) {
}
