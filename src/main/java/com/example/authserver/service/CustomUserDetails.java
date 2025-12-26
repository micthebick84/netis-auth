package com.example.authserver.service;

import com.example.authserver.entity.User;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomUserDetails implements UserDetails {

    private long id;
    private String username;
    private String password;
    private String email;
    private boolean enabled;
    private boolean accountNonLocked;
    private Set<String> authorities;

    @JsonIgnore
    private transient User user;

    // Jackson 역직렬화용 기본 생성자
    public CustomUserDetails() {
    }

    // Jackson 역직렬화용 생성자
    @JsonCreator
    public CustomUserDetails(@JsonProperty("id") long id,
                             @JsonProperty("username") String username,
                             @JsonProperty("password") String password,
                             @JsonProperty("email") String email,
                             @JsonProperty("enabled") boolean enabled,
                             @JsonProperty("accountNonLocked") boolean accountNonLocked,
                             @JsonProperty("authorities") Set<String> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.authorities = authorities != null ? authorities : new HashSet<>();
    }

    // User 엔티티로부터 생성하는 생성자
    public CustomUserDetails(User user) {
        this.user = user;
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.email = user.getEmail();
        this.enabled = user.isEnabled();
        this.accountNonLocked = user.isAccountNonLocked();
        this.authorities = user.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .collect(Collectors.toSet());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    @JsonProperty("authorities")
    public Set<String> getAuthorityStrings() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @JsonIgnore
    public User getUser() {
        return user;
    }

    public long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
}
