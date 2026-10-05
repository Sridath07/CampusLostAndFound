package edu.campus.lostfound;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

public abstract class ItemReport {
    private final int id;
    private final String title;
    private final String description;
    private final String category;
    private final String location;
    private final LocalDate reportDate;
    private final User reporter;
    private ItemStatus status;
    private String claimedBy;

    protected ItemReport(int id, String title, String description, String category,
                         String location, User reporter) {
        if (id <= 0) throw new IllegalArgumentException("ID must be positive.");
        this.id = id;
        this.title = requireText(title, "Title");
        this.description = requireText(description, "Description");
        this.category = requireText(category, "Category");
        this.location = requireText(location, "Location");
        this.reporter = Objects.requireNonNull(reporter, "Reporter is required.");
        this.reportDate = LocalDate.now();
        this.status = ItemStatus.OPEN;
    }

    protected ItemReport(int id, String title, String description, String category,
                         String location, User reporter, LocalDate reportDate,
                         ItemStatus status, String claimedBy) {
        if (id <= 0) throw new IllegalArgumentException("ID must be positive.");
        this.id = id;
        this.title = requireText(title, "Title");
        this.description = requireText(description, "Description");
        this.category = requireText(category, "Category");
        this.location = requireText(location, "Location");
        this.reporter = Objects.requireNonNull(reporter, "Reporter is required.");
        this.reportDate = Objects.requireNonNull(reportDate, "Report date is required.");
        this.status = Objects.requireNonNull(status, "Status is required.");
        this.claimedBy = claimedBy;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getLocation() { return location; }
    public LocalDate getReportDate() { return reportDate; }
    public User getReporter() { return reporter; }
    public ItemStatus getStatus() { return status; }
    public String getClaimedBy() { return claimedBy; }
    public abstract ReportType getType();

    public void markClaimed(String claimant) {
        if (status != ItemStatus.OPEN) throw new IllegalStateException("Only open reports can be claimed.");
        claimedBy = requireText(claimant, "Claimant campus ID");
        status = ItemStatus.CLAIMED;
    }

    public void markReturned() {
        if (status != ItemStatus.CLAIMED) throw new IllegalStateException("Claim the report before marking it returned.");
        status = ItemStatus.RETURNED;
    }

    public boolean matches(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return title.toLowerCase(Locale.ROOT).contains(q)
                || description.toLowerCase(Locale.ROOT).contains(q)
                || category.toLowerCase(Locale.ROOT).contains(q)
                || location.toLowerCase(Locale.ROOT).contains(q)
                || reporter.getName().toLowerCase(Locale.ROOT).contains(q);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be empty.");
        return value.trim();
    }

    @Override
    public String toString() {
        return String.format("#%d [%s | %s] %s — %s; category: %s; location: %s; reported: %s; by: %s (%s)",
                id, getType(), status, title, description, category, location, reportDate,
                reporter.getName(), reporter.getCampusId());
    }
}
