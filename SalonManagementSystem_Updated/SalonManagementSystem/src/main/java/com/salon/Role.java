package com.salon;

import java.util.EnumSet;
import java.util.Set;

/** The three kinds of users: the salon owner (admin), salon staff, and clients (who sign in with just an email). */
public enum Role {
    ADMIN("Administrator (Owner)", EnumSet.complementOf(EnumSet.of(Perm.CREATE_BOOKING, Perm.EDIT_BOOKING, Perm.PROCESS_PAYMENT, Perm.HANDLE_REQUESTS, Perm.CLIENT_BROWSE, Perm.CLIENT_BOOK,
            Perm.CLIENT_PAY, Perm.CLIENT_FEEDBACK))),
    STAFF("Salon Staff", EnumSet.of(Perm.UPDATE_OWN_PROGRESS, Perm.VIEW_OWN_WORK, Perm.HANDLE_REQUESTS, Perm.VIEW_REVIEWS)),
    CLIENT("Client", EnumSet.of(Perm.CLIENT_BROWSE, Perm.CLIENT_BOOK, Perm.CLIENT_PAY, Perm.CLIENT_FEEDBACK, Perm.VIEW_REVIEWS));

    public enum Perm {
        MANAGE_USERS, MANAGE_SERVICES, VIEW_REPORTS, VIEW_CLIENTS, REGISTER_CLIENT, CREATE_BOOKING, EDIT_BOOKING,
        VIEW_BOOKINGS, PROCESS_PAYMENT, VIEW_PAYMENTS, VIEW_SERVICES, UPDATE_OWN_PROGRESS, VIEW_OWN_WORK,
        HANDLE_REQUESTS, VIEW_REVIEWS, CLIENT_BROWSE, CLIENT_BOOK, CLIENT_PAY, CLIENT_FEEDBACK
    }

    public final String label;
    private final Set<Perm> perms;

    Role(String label, Set<Perm> perms) { this.label = label; this.perms = perms; }

    public boolean can(Perm p) { return perms.contains(p); }

    /** Roles that live in the "users" table (clients are in the "clients" table). */
    public static Role[] accountRoles() { return new Role[]{ADMIN, STAFF}; }
}
