package dev.roadmap.shop.auth;

/** One role per user. Stored as its name (EnumType.STRING), and sent in the JWT "roles" claim without the ROLE_ prefix. */
public enum Role {
    ADMIN,
    CUSTOMER;

    public String authority() {
        return "ROLE_" + name();
    }
}
