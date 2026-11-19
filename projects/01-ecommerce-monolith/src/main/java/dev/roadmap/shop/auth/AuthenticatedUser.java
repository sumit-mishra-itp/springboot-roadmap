package dev.roadmap.shop.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * What DaoAuthenticationProvider gets from the UserDetailsService, and what it puts back as the principal of a
 * successful login. Spring's own User class has no id, and the JWT "sub" claim needs one, so this record carries it.
 * The account-status methods (isEnabled, ...) are UserDetails defaults that return true.
 */
record AuthenticatedUser(long id, String username, String passwordHash, Role role) implements UserDetails {

    static AuthenticatedUser from(UserAccount account) {
        return new AuthenticatedUser(account.getId(), account.getUsername(), account.getPasswordHash(), account.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /** The principal can end up in a log line or a debugger. The hash must not. */
    @Override
    public String toString() {
        return "AuthenticatedUser[id=" + id + ", username=" + username + ", role=" + role + "]";
    }
}
