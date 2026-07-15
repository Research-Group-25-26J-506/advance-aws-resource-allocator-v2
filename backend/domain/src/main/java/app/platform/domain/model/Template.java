package app.platform.domain.model;

public record Template(
        String id, // e.g. "s3-bucket" — ^[a-z][a-z0-9-]{1,63}$
        String displayName,
        String description,
        String category,
        String maturity, // stable | beta | deprecated
        String application,
        String latestVersion) {}
