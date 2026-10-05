package edu.campus.lostfound;

import java.time.LocalDate;

public class FoundItemReport extends ItemReport {
    public FoundItemReport(int id, String title, String description, String category, String location, User reporter) {
        super(id, title, description, category, location, reporter);
    }

    public FoundItemReport(int id, String title, String description, String category, String location,
                           User reporter, LocalDate reportDate) {
        super(id, title, description, category, location, reporter, reportDate, ItemStatus.OPEN, null);
    }

    FoundItemReport(int id, String title, String description, String category, String location,
                    User reporter, LocalDate date, ItemStatus status, String claimedBy) {
        super(id, title, description, category, location, reporter, date, status, claimedBy);
    }
    @Override public ReportType getType() { return ReportType.FOUND; }
}
