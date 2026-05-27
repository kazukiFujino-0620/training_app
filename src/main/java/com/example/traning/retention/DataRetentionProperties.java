package com.example.traning.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.retention")
public class DataRetentionProperties {

    /** データ保護期間（年）。デフォルト 7 年。 */
    private int retentionYears = 7;

    public int getRetentionYears() {
        return retentionYears;
    }

    public void setRetentionYears(int retentionYears) {
        this.retentionYears = retentionYears;
    }
}
