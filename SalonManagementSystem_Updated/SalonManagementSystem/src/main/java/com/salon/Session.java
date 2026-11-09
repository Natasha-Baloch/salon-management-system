package com.salon;

/** The currently logged-in user. All data operations check it, so UI tricks cannot bypass permissions. */
public final class Session {
    public record User(int id, String fullName, String username, Role role, boolean mustChangePassword) {}

    private static volatile User current;

    private Session() {}

    public static void start(User u) { current = u; }
    public static void end() { current = null; }
    public static User user() { return current; }

    public static User require(Role.Perm p) {
        User u = current;
        if (u == null) throw new SecurityException("You are not logged in.");
        if (!u.role().can(p)) throw new SecurityException("Access denied: your role (" + u.role().label + ") cannot do this.");
        return u;
    }
}
