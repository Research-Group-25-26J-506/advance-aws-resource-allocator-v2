package app.platform.observability;

/** Canonical MDC field names — dashboards and Loki queries key off these exact strings. */
public final class MdcKeys {
    public static final String REQUEST_ID = "request_id";
    public static final String TEMPLATE_ID = "template_id";
    public static final String ENVIRONMENT = "environment";
    public static final String REGION = "region";
    public static final String ACTOR_ID = "actor_id";

    private MdcKeys() {}
}
