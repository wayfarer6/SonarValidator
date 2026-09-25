package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 로그인 사용자 저장소입니다.
 *
 * <p>파생 쿼리만 쓰므로 구현 코드가 없습니다. 조회 키는 항상
 * {@code username} 입니다. (숫자 PK 는 외부에 노출하지 않습니다.)
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 아이디로 사용자를 찾습니다. (인증 시 사용)
     *
     * @param username 로그인 아이디
     * @return 사용자 (없으면 비어 있음)
     */
    Optional<User> findByUsername(String username);

    /**
     * 아이디 존재 여부를 확인합니다. (중복 확인)
     *
     * @param username 로그인 아이디
     * @return 존재하면 {@code true}
     */
    boolean existsByUsername(String username);

    /**
     * 전체 사용자를 생성순으로 조회합니다.
     *
     * @return 사용자 목록
     */
    List<User> findAllByOrderByIdAsc();
}
