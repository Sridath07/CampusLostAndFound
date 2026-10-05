package edu.campus.lostfound;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final Scanner INPUT = new Scanner(System.in);
    private static final LostAndFoundService SERVICE = new LostAndFoundService();
    private static final ReportRepository REPOSITORY = new ReportRepository();

    public static void main(String[] args) {
        if (args.length > 0 && "--web".equalsIgnoreCase(args[0])) {
            startWebsite();
            return;
        }
        try {
            SERVICE.load(REPOSITORY.load(SERVICE));
            System.out.println("Loaded " + SERVICE.getAllReports().size() + " saved report(s).");
        } catch (IOException ex) {
            System.out.println("Could not load saved reports: " + ex.getMessage());
            return;
        }

        boolean running = true;
        System.out.println("\n=== CAMPUS LOST-AND-FOUND ===");
        while (running) {
            printMenu();
            switch (readInt("Choose an option: ")) {
                case 1 -> createReport(true);
                case 2 -> createReport(false);
                case 3 -> show(SERVICE.getAllReports());
                case 4 -> search();
                case 5 -> show(SERVICE.getOpenReports(ReportType.LOST));
                case 6 -> show(SERVICE.getOpenReports(ReportType.FOUND));
                case 7 -> showMatches();
                case 8 -> updateReport(true);
                case 9 -> updateReport(false);
                case 0 -> running = false;
                default -> System.out.println("Please choose one of the listed options.");
            }
        }
        try {
            REPOSITORY.save(SERVICE.getAllReports());
            System.out.println("Saved " + SERVICE.getAllReports().size() + " report(s). Goodbye!");
        } catch (IOException ex) {
            System.out.println("Could not save reports: " + ex.getMessage());
        }
    }

    private static void startWebsite() {
        try {
            SERVICE.load(REPOSITORY.load(SERVICE));
            new WebServer(SERVICE, REPOSITORY, 8080).start();
        } catch (IOException ex) {
            System.out.println("Could not start website or connect to Supabase: " + ex.getMessage());
        }
    }

    private static void printMenu() {
        System.out.println("\n1. Report lost item     2. Report found item\n3. View all reports     4. Search reports\n5. Open lost reports    6. Open found reports\n7. Find possible match  8. Claim report\n9. Mark item returned   0. Save and exit");
    }

    private static void createReport(boolean lost) {
        try {
            System.out.println(lost ? "\n-- LOST ITEM --" : "\n-- FOUND ITEM --");
            String title = readText("Item name: ");
            String description = readText("Description / identifying details: ");
            String category = readText("Category (electronics, clothing, etc.): ");
            String location = readText("Campus location (or Unknown): ");
            User user = new User(readText("Your name: "), readText("Your campus ID: "), readText("Contact details: "));
            ItemReport report = lost
                    ? SERVICE.reportLost(title, description, category, location, user)
                    : SERVICE.reportFound(title, description, category, location, user);
            saveNow();
            System.out.println("Report saved with ID " + report.getId() + ".");
            List<ItemReport> matches = SERVICE.getPotentialMatches(report.getId());
            if (!matches.isEmpty()) {
                System.out.println("Possible matching reports:");
                show(matches);
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("Could not save report: " + ex.getMessage());
        }
    }

    private static void search() {
        String query = readText("Search by item, details, category, location, or reporter: ");
        show(SERVICE.search(query));
    }

    private static void showMatches() {
        int id = readInt("Report ID to find matches for: ");
        if (SERVICE.findById(id).isEmpty()) {
            System.out.println("No report has that ID.");
            return;
        }
        List<ItemReport> matches = SERVICE.getPotentialMatches(id);
        if (matches.isEmpty()) System.out.println("No likely matches found yet.");
        else show(matches);
    }

    private static void updateReport(boolean claim) {
        int id = readInt("Report ID: ");
        boolean updated;
        if (claim) {
            String campusId = readText("Your campus ID: ");
            updated = SERVICE.claim(id, campusId);
        } else {
            updated = SERVICE.markReturned(id);
        }
        if (updated) {
            saveNow();
            System.out.println("Report status updated.");
        } else {
            System.out.println("No matching report in the required status.");
        }
    }

    private static void show(List<ItemReport> reports) {
        if (reports.isEmpty()) {
            System.out.println("No reports found.");
            return;
        }
        reports.forEach(System.out::println);
    }

    private static void saveNow() {
        try { REPOSITORY.save(SERVICE.getAllReports()); }
        catch (IOException ex) { System.out.println("Warning: could not save data: " + ex.getMessage()); }
    }

    private static String readText(String prompt) {
        System.out.print(prompt);
        return INPUT.nextLine().trim();
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = INPUT.nextLine().trim();
            try { return Integer.parseInt(value); }
            catch (NumberFormatException ex) { System.out.println("Enter a whole number."); }
        }
    }
}
