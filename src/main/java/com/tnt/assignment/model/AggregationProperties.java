package com.tnt.assignment.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties
@Getter
@Setter
public class AggregationProperties {
    private String pricingBaseUrl;
    private String pricingUri;
    private String trackBaseUrl;
    private String trackUri;
    private String shipmentsBaseUrl;
    private String shipmentsUri;
    private Duration timeout;
}
