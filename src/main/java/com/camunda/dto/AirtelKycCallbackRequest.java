package com.camunda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AirtelKycCallbackRequest {

    /** MSISDN used as correlation key to route the callback to the waiting process instance. */
    @NotBlank(message = "msisdn is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "msisdn must be 10-15 digits")
    private String msisdn;

    /** Customer full name from Airtel KYC. */
    @NotBlank(message = "fullName is required")
    private String fullName;

    /** National ID number from Airtel KYC. */
    @NotBlank(message = "nationalId is required")
    private String nationalId;

    /** Date of birth (ISO-8601, e.g. 1990-01-15). */
    private String dob;

    /** Email address on record with Airtel. */
    private String email;
}
