package app.platform.domain.model;

public enum Environment {
    DEV,
    QA,
    STG,
    PROD;

    /** PROD provisioning always goes through the approvals inbox (2.07). */
    public boolean requiresApproval() {
        return this == PROD;
    }
}
