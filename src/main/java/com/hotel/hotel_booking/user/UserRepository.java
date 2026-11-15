package com.hotel.hotel_booking.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    long countByRole(Role role);
    org.springframework.data.domain.Page<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name,String email,org.springframework.data.domain.Pageable page);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.email = :email")
    Optional<User> findLockedByEmail(@org.springframework.data.repository.query.Param("email") String email);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
