package edu.campus.lostfound;

import java.time.LocalDate;

public class LostItemReport extends ItemReport {
    public LostItemReport(int id, String title, String description, String category, String location, User reporter) {
        super(id, title, description, category, location, reporter);
    }

    public LostItemReport(int id, String title, String description, String category, String location,
                          User reporter, LocalDate reportDate) {
        super(id, title, description, category, location, reporter, reportDate, ItemStatus.OPEN, null);
    }

    LostItemReport(int id, String title, String description, String category, String location,
                   User reporter, LocalDate date, ItemStatus status, String claimedBy) {
        super(id, title, description, category, location, reporter, date, status, claimedBy);
    }
    @Override public ReportType getType() { return ReportType.LOST; }
}
