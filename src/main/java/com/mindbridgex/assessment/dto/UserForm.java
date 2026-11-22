package com.mindbridgex.assessment.dto;

import com.mindbridgex.assessment.domain.RoleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.EnumSet;
import java.util.Set;

public class UserForm {
    private Long id;
    @NotBlank(message = "Username is required")
    @Pattern(regexp = "^[A-Za-z0-9._@-]{3,60}$", message = "Username must be 3-60 characters: letters, digits, dot, underscore, dash or @")
    private String username;
    @NotBlank(message = "Full name is required")
    @Size(max = 120)
    private String fullName;
    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 160)
    private String email;
    @Size(max = 40)
    private String rollNo;
    @Size(max = 30)
    private String phone;
    @NotEmpty(message = "Select at least one role")
    private Set<RoleType> roles = EnumSet.of(RoleType.STUDENT);
    /**
     * Optional on create (a random one is generated), ignored on edit.
     */
    @Size(max = 72)
    private String password;
    private boolean active = true;

    public Long getId() {
        return this.id;
    }

    public String getUsername() {
        return this.username;
    }

    public String getFullName() {
        return this.fullName;
    }

    public String getEmail() {
        return this.email;
    }

    public String getRollNo() {
        return this.rollNo;
    }

    public String getPhone() {
        return this.phone;
    }

    public Set<RoleType> getRoles() {
        return this.roles;
    }

    /**
     * Optional on create (a random one is generated), ignored on edit.
     */
    public String getPassword() {
        return this.password;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public void setFullName(final String fullName) {
        this.fullName = fullName;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public void setRollNo(final String rollNo) {
        this.rollNo = rollNo;
    }

    public void setPhone(final String phone) {
        this.phone = phone;
    }

    public void setRoles(final Set<RoleType> roles) {
        this.roles = roles;
    }

    /**
     * Optional on create (a random one is generated), ignored on edit.
     */
    public void setPassword(final String password) {
        this.password = password;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }
}
