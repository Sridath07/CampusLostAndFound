package edu.campus.lostfound;

public class User {
    private final String name;
    private final String campusId;
    private final String contact;

    public User(String name, String campusId, String contact) {
        this.name = requireText(name, "Name");
        this.campusId = requireText(campusId, "Campus ID");
        this.contact = requireText(contact, "Contact");
    }

    public String getName() { return name; }
    public String getCampusId() { return campusId; }
    public String getContact() { return contact; }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty.");
        }
        return value.trim();
    }
}
