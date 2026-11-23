package com.opspilot.agent.service.agent;

import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.service.IncidentAssessmentService;
import com.opspilot.agent.service.IncidentAssessment;
import org.springframework.stereotype.Component;

/**
 * 故障守护 Agent。
 *
 * <p>负责后台故障分级和生产事故硬兜底；安全判断不能完全交给自由生成模型。</p>
 */
@Component
public class IncidentGuardianAgent implements OpsPilotAgent {

    private final IncidentAssessmentService assessmentService;

    public IncidentGuardianAgent(IncidentAssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @Override
    public AgentName name() {
        return AgentName.INCIDENT_GUARDIAN_AGENT;
    }

    @Override
    public boolean supports(AgentContext context) {
        return context.knowledgeHandled()
                && !context.incidentAssessed()
                && context.intent() != IntentType.CHAT;
    }

    @Override
    public AgentDecision act(AgentContext context) {
        IncidentAssessment assessment = assessmentService.assess(context.modelInput(), context.modelHistory());
        if (context.intent() == IntentType.INCIDENT && assessment.severity() != SeverityLevel.HIGH) {
            assessment = new IncidentAssessment(
                    assessment.category(),
                    Math.max(assessment.severityScore(), 4.0),
                    SeverityLevel.HIGH,
                    assessment.confidence(),
                    assessment.summary());
        }
        context.setAssessment(assessment);
        context.setSeverity(assessment.severity());
        context.markIncidentAssessed();
        return AgentDecision.continueWith(
                AgentAction.ASSESS_INCIDENT,
                "severity=%s, category=%s".formatted(assessment.severity(), assessment.category()));
    }
}
