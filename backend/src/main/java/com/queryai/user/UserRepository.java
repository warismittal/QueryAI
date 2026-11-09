package com.queryai.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    @Query("""
            select u from User u
            where (:q is null or lower(u.email) like lower(concat('%', cast(:q as string), '%'))
                   or lower(u.fullName) like lower(concat('%', cast(:q as string), '%')))
            order by u.createdAt desc
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);

    long countByEnabledTrue();
}
