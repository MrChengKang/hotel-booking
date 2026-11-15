package com.hotel.hotel_booking.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PasswordResetRepository extends JpaRepository<PasswordResetToken, Long> {
    @Override
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findById(Long id);
    @Query("select t.userId from PasswordResetToken t where t.tokenHash = :hash")
    Optional<Long> findUserIdByHash(@Param("hash") String hash);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String hash);
}
