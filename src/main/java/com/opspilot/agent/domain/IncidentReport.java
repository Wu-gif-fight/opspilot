package com.opspilot.agent.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "incident_reports")
/**
 * 后台故障报告。
 *
 * <p>报告记录的是后台故障分级和工具执行状态，不会作为普通用户展示内容。</p>
 */
public class IncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession session;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntentType intent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentCategory category;

    @Column(nullable = false)
    private double severityScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeverityLevel severity;

    @Column(nullable = false)
    private double confidence;

    @Column(length = 500)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ToolStatus excelStatus = ToolStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ToolStatus emailStatus = ToolStatus.SKIPPED;

    @Column(length = 500)
    private String toolError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount user) {
        this.user = user;
    }

    public ChatSession getSession() {
        return session;
    }

    public void setSession(ChatSession session) {
        this.session = session;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public IntentType getIntent() {
        return intent;
    }

    public void setIntent(IntentType intent) {
        this.intent = intent;
    }

    public IncidentCategory getCategory() {
        return category;
    }

    public void setCategory(IncidentCategory category) {
        this.category = category;
    }

    public double getSeverityScore() {
        return severityScore;
    }

    public void setSeverityScore(double severityScore) {
        this.severityScore = severityScore;
    }

    public SeverityLevel getSeverity() {
        return severity;
    }

    public void setSeverity(SeverityLevel severity) {
        this.severity = severity;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public ToolStatus getExcelStatus() {
        return excelStatus;
    }

    public void setExcelStatus(ToolStatus excelStatus) {
        this.excelStatus = excelStatus;
    }

    public ToolStatus getEmailStatus() {
        return emailStatus;
    }

    public void setEmailStatus(ToolStatus emailStatus) {
        this.emailStatus = emailStatus;
    }

    public String getToolError() {
        return toolError;
    }

    public void setToolError(String toolError) {
        this.toolError = toolError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
