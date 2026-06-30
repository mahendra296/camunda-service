package com.camunda.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "risk_config")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RiskConfig {

    @Id
    @Column(name = "config_key", length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, precision = 10, scale = 4)
    private BigDecimal configValue;

    @Column(name = "description", length = 255)
    private String description;
}
