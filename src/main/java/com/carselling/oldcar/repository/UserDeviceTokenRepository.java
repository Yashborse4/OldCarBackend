package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, Long> {

    Optional<UserDeviceToken> findByToken(String token);

    List<UserDeviceToken> findByUser(User user);

    void deleteByToken(String token);

    void deleteByUser(User user);
}
