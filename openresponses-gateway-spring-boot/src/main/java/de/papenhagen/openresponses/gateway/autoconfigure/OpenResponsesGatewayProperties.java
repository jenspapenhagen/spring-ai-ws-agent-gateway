package de.papenhagen.openresponses.gateway.autoconfigure;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openresponses.gateway")
public class OpenResponsesGatewayProperties {
    private boolean enabled = true;
    private String path = "/ws/responses";
    private String defaultModel = "gpt-4.1-mini";
    @Min(1)
    private int maxHistoryItems = 100;
    @Min(1)
    private int maxStoredResponses = 100;
    @Positive
    private Duration providerTimeout = Duration.ofSeconds(90);
    private Duration sessionTtl = Duration.ofMinutes(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean gatewayEnabled) {
        this.enabled = gatewayEnabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String gatewayPath) {
        this.path = gatewayPath;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(final String configuredDefaultModel) {
        this.defaultModel = configuredDefaultModel;
    }

    public int getMaxHistoryItems() {
        return maxHistoryItems;
    }

    public void setMaxHistoryItems(final int configuredMaxHistoryItems) {
        this.maxHistoryItems = configuredMaxHistoryItems;
    }

    public int getMaxStoredResponses() {
        return maxStoredResponses;
    }

    public void setMaxStoredResponses(final int configuredMaxStoredResponses) {
        this.maxStoredResponses = configuredMaxStoredResponses;
    }

    public Duration getProviderTimeout() {
        return providerTimeout;
    }

    public void setProviderTimeout(final Duration configuredProviderTimeout) {
        this.providerTimeout = configuredProviderTimeout;
    }

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(final Duration ttl) {
        this.sessionTtl = ttl;
    }
}
