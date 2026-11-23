package com.opspilot.agent.repository;

import com.opspilot.agent.domain.IncidentReport;
import com.opspilot.agent.domain.ToolStatus;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 故障报告的数据访问接口。
 */
public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {

    /** 当前用户自己的报告查询，主要保留给接口扩展。 */
    @EntityGraph(attributePaths = {"user", "session"})
    List<IncidentReport> findTop50ByUser_IdOrderByCreatedAtDesc(Long userId);

    /** 管理员后台报告列表。 */
    @EntityGraph(attributePaths = {"user", "session"})
    List<IncidentReport> findTop100ByOrderByCreatedAtDesc();

    /** Excel 写入记录页面只需要写入成功的报告。 */
    @EntityGraph(attributePaths = {"user", "session"})
    List<IncidentReport> findTop100ByExcelStatusOrderByCreatedAtDesc(ToolStatus excelStatus);
}
