package org.sonar.sonarvalidator_backend.Repository;

import java.util.List;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 프로젝트 저장소입니다.
 *
 * <p>조회 키는 숫자 PK 가 아니라 {@code projectKey} 입니다. 프론트엔드가
 * URL 로 들고 다니는 값이 그쪽이기 때문입니다.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * 프로젝트 키로 조회합니다.
     *
     * @param projectKey 외부 노출 키
     * @return 프로젝트 (없으면 비어 있음)
     */
    Optional<Project> findByProjectKey(String projectKey);

    /**
     * 생성 시각 내림차순 전체 조회입니다. (목록 화면 기본 정렬)
     *
     * @return 프로젝트 목록
     */
    List<Project> findAllByOrderByCreatedAtDesc();

    /**
     * 프로젝트 키 존재 여부를 확인합니다.
     *
     * @param projectKey 외부 노출 키
     * @return 존재하면 {@code true}
     */
    boolean existsByProjectKey(String projectKey);
}
