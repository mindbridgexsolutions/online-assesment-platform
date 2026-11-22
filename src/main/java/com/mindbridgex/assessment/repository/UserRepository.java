package com.mindbridgex.assessment.repository;

import com.mindbridgex.assessment.domain.RoleType;
import com.mindbridgex.assessment.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query("select u from User u where :role member of u.roles and u.active = true order by u.fullName")
    List<User> findActiveByRole(@Param("role") RoleType role);

    @Query("select count(u) from User u where :role member of u.roles and u.active = true")
    long countActiveByRole(@Param("role") RoleType role);

    List<User> findByIdIn(Collection<Long> ids);

    @Query("select u from User u where u.id in :ids and :role member of u.roles order by u.fullName")
    List<User> findByIdsAndRole(@Param("ids") Collection<Long> ids, @Param("role") RoleType role);
}
