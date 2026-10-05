package edu.campus.lostfound;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class LostAndFoundService {
    private final List<ItemReport> reports = new ArrayList<>();
    private int nextId = 1;

    public synchronized ItemReport reportLost(String title, String description, String category, String location, User reporter) {
        ItemReport report = new LostItemReport(nextId++, title, description, category, location, reporter);
        reports.add(report);
        return report;
    }

    public synchronized ItemReport reportLost(String title, String description, String category, String location,
                                              User reporter, LocalDate reportDate) {
        ItemReport report = new LostItemReport(nextId++, title, description, category, location, reporter, reportDate);
        reports.add(report);
        return report;
    }

    public synchronized ItemReport reportFound(String title, String description, String category, String location, User reporter) {
        ItemReport report = new FoundItemReport(nextId++, title, description, category, location, reporter);
        reports.add(report);
        return report;
    }

    public synchronized ItemReport reportFound(String title, String description, String category, String location,
                                                User reporter, LocalDate reportDate) {
        ItemReport report = new FoundItemReport(nextId++, title, description, category, location, reporter, reportDate);
        reports.add(report);
        return report;
    }

    public synchronized List<ItemReport> getAllReports() {
        return reports.stream().sorted(Comparator.comparingInt(ItemReport::getId)).collect(Collectors.toList());
    }

    public synchronized List<ItemReport> search(String query) {
        if (query == null || query.trim().isEmpty()) return getAllReports();
        return reports.stream().filter(report -> report.matches(query.trim()))
                .sorted(Comparator.comparingInt(ItemReport::getId)).collect(Collectors.toList());
    }

    public synchronized List<ItemReport> getOpenReports(ReportType type) {
        return reports.stream().filter(r -> r.getType() == type && r.getStatus() == ItemStatus.OPEN)
                .sorted(Comparator.comparingInt(ItemReport::getId)).collect(Collectors.toList());
    }

    public synchronized List<ItemReport> getPotentialMatches(int id) {
        ItemReport selected = findById(id).orElse(null);
        if (selected == null) return List.of();
        ReportType opposite = selected.getType() == ReportType.LOST ? ReportType.FOUND : ReportType.LOST;
        String[] terms = (selected.getTitle() + " " + selected.getCategory() + " " + selected.getDescription())
                .toLowerCase(Locale.ROOT).split("\\W+");
        return reports.stream()
                .filter(candidate -> candidate.getType() == opposite && candidate.getStatus() == ItemStatus.OPEN)
                .map(candidate -> new Match(candidate, score(candidate, terms)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(Match::score).reversed()
                        .thenComparingInt(match -> match.report().getId()))
                .limit(5).map(Match::report).collect(Collectors.toList());
    }

    private int score(ItemReport candidate, String[] terms) {
        String searchable = (candidate.getTitle() + " " + candidate.getCategory() + " " + candidate.getDescription())
                .toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (term.length() > 2 && searchable.contains(term)) score++;
        }
        if (candidate.getLocation().equalsIgnoreCase("unknown")) return score;
        return score + 1;
    }

    private record Match(ItemReport report, int score) { }

    public synchronized Optional<ItemReport> findById(int id) {
        return reports.stream().filter(r -> r.getId() == id).findFirst();
    }

    public synchronized boolean claim(int id, String claimantCampusId) {
        Optional<ItemReport> report = findById(id);
        if (report.isEmpty() || report.get().getStatus() != ItemStatus.OPEN) return false;
        report.get().markClaimed(claimantCampusId);
        return true;
    }

    public synchronized boolean markReturned(int id) {
        Optional<ItemReport> report = findById(id);
        if (report.isEmpty() || report.get().getStatus() != ItemStatus.CLAIMED) return false;
        report.get().markReturned();
        return true;
    }

    synchronized void load(List<ItemReport> savedReports) {
        reports.clear();
        reports.addAll(savedReports);
        nextId = reports.stream().mapToInt(ItemReport::getId).max().orElse(0) + 1;
    }

    synchronized ItemReport restore(int id, ReportType type, String title, String description,
                       String category, String location, User reporter, LocalDate date,
                       ItemStatus status, String claimedBy) {
        ItemReport item = type == ReportType.LOST
                ? new LostItemReport(id, title, description, category, location, reporter, date, status, claimedBy)
                : new FoundItemReport(id, title, description, category, location, reporter, date, status, claimedBy);
        reports.add(item);
        nextId = Math.max(nextId, id + 1);
        return item;
    }
}
