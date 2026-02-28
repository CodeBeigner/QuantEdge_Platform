package com.QuantPlatformApplication.QuantPlatformApplication.security;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.User;
import com.QuantPlatformApplication.QuantPlatformApplication.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Custom UserDetailsService — loads a user from OUR database for Spring Security.
 * Spring Security calls loadUserByUsername() whenever it needs to verify a user
 * (during login or JWT filter authentication). We look up the User entity by
 * email and convert it into Spring Security's UserDetails object.
 * The "username" here is actually the user's email address.
 */

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));

        // Convert our User entity → Spring Security's UserDetails
        // Role is stored as "USER" or "ADMIN", we prefix with "ROLE_" for Spring
        // convention
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
    }
}
