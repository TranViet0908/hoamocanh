package com.hoamocanh.repository;

import com.hoamocanh.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByPhone(String phone);

    // Admin: danh sách tài khoản theo tên vai trò (ADMIN, STAFF)
    List<User> findByRoleNameIn(Collection<String> roleNames);
}
