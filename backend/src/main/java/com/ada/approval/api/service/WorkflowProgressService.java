package com.ada.approval.api.service;

import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.api.dto.ApiMapper;
import com.ada.approval.entity.HolidayApply;
import com.ada.approval.entity.HolidayApproval;
import com.ada.approval.repository.HolidayApprovalRepository;
import org.activiti.engine.*;
import org.activiti.engine.task.Task;
import org.activiti.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Projects current and future approval stages from Activiti runtime/history and application
 * decisions. Callers must authorize access first. Missing or inconsistent evidence produces
 * unavailable progress, not an invented task.
 */
@Service
public class WorkflowProgressService {
    private final TaskService tasks;
    private final RuntimeService runtime;
    private final HistoryService history;
    private final HolidayApprovalRepository approvals;

    public WorkflowProgressService(
            TaskService tasks,
            RuntimeService runtime,
            HistoryService history,
            HolidayApprovalRepository approvals) {
        this.tasks = tasks;
        this.runtime = runtime;
        this.history = history;
        this.approvals = approvals;
    }

    /**
     * Reconciles active tasks with persisted decisions and process history for an already-
     * authorized request. A terminal application status alone is insufficient: runtime and historic
     * evidence must agree.
     */
    public WorkflowProgress describe(HolidayApply request) {
        String process = request.getProcessInstanceId();
        List<Task> active = Collections.emptyList();
        List<HolidayApproval> decisions = Collections.emptyList();
        boolean running = false, ended = false, successfullyEnded = false;
        Number workflowDays = null;
        String applicantIdentity = null;
        if (process != null && !process.isBlank()) {
            running =
                    runtime.createProcessInstanceQuery().processInstanceId(process).singleResult()
                            != null;
            active = tasks.createTaskQuery().processInstanceId(process).active().list();
            decisions = approvals.findByProcessInstanceIdOrderByCreateTimeAscIdAsc(process);
            if (running) {
                Object identity =
                        runtime.getVariable(
                                process,
                                com.ada.approval.workflow.ApplicantWorkflow.IDENTITY_VARIABLE);
                applicantIdentity = identity instanceof String ? (String) identity : null;
                Object day = runtime.getVariable(process, "day");
                if (day instanceof Number) workflowDays = (Number) day;
            } else {
                HistoricProcessInstance historic =
                        history.createHistoricProcessInstanceQuery()
                                .processInstanceId(process)
                                .singleResult();
                org.activiti.engine.history.HistoricVariableInstanceQuery variableQuery =
                        history.createHistoricVariableInstanceQuery();
                if (variableQuery != null) {
                    org.activiti.engine.history.HistoricVariableInstance variable =
                            variableQuery
                                    .processInstanceId(process)
                                    .variableName(
                                            com.ada.approval.workflow.ApplicantWorkflow
                                                    .IDENTITY_VARIABLE)
                                    .singleResult();
                    if (variable != null && variable.getValue() instanceof String)
                        applicantIdentity = (String) variable.getValue();
                }
                ended = historic != null && historic.getEndTime() != null;
                successfullyEnded = ended && historic.getDeleteReason() == null;
            }
        }
        boolean generalManager =
                (workflowDays != null
                                        ? workflowDays.intValue()
                                        : Objects.requireNonNullElse(request.getDays(), 0))
                                > 3
                        || active.stream()
                                .anyMatch(t -> "boss_check".equals(t.getTaskDefinitionKey()))
                        || decisions.stream().anyMatch(d -> "boss_check".equals(d.getTaskDefKey()));
        List<WorkflowProgressStage> steps = new ArrayList<>();
        steps.add(new WorkflowProgressStage("SUBMITTED", "Submitted", "COMPLETED"));
        // Older process definitions have no identity variable and retain the employee route.
        com.ada.approval.workflow.ApplicantWorkflow.Identity identity =
                applicantIdentity == null
                        ? com.ada.approval.workflow.ApplicantWorkflow.Identity.EMPLOYEE
                        : com.ada.approval.workflow.ApplicantWorkflow.Identity.valueOf(
                                applicantIdentity);
        for (String stage :
                com.ada.approval.workflow.ApplicantWorkflow.stages(
                        identity, generalManager ? 4 : 3)) {
            String label =
                    switch (stage) {
                        case "DEPARTMENT_MANAGER" -> "Department Manager";
                        case "GENERAL_MANAGER" -> "General Manager";
                        default -> "HR";
                    };
            steps.add(new WorkflowProgressStage(stage, label, "UPCOMING"));
        }
        steps.add(new WorkflowProgressStage("COMPLETED", "Completed", "UPCOMING"));
        WorkflowProgress progress = new WorkflowProgress();
        progress.setStages(steps);
        if (Objects.equals(request.getStatus(), 5) && !running && active.isEmpty() && ended) {
            HolidayApproval withdrawal =
                    decisions.stream()
                            .filter(d -> Objects.equals(d.getResult(), 7))
                            .reduce((a, b) -> b)
                            .orElse(null);
            if (withdrawal != null
                    && index(steps, ApiMapper.stage(withdrawal.getTaskDefKey())) > 0) {
                for (int i = 1; i < steps.size() - 1; i++) {
                    String stage = steps.get(i).getStage();
                    boolean approved =
                            decisions.stream()
                                    .anyMatch(
                                            d ->
                                                    stage.equals(ApiMapper.stage(d.getTaskDefKey()))
                                                            && d.getResult() != null
                                                            && Set.of(1, 3, 5)
                                                                    .contains(d.getResult()));
                    steps.get(i).setStatus(approved ? "COMPLETED" : "NOT_REACHED");
                }
                steps.set(
                        steps.size() - 1,
                        new WorkflowProgressStage("WITHDRAWN", "Withdrawn", "WITHDRAWN"));
                progress.setCurrentStage("WITHDRAWN");
                progress.setMessage("Request withdrawn by applicant");
                return progress;
            }
        }
        if (Objects.equals(request.getStatus(), 4)
                && !running
                && active.isEmpty()
                && successfullyEnded) {
            steps.forEach(s -> s.setStatus("COMPLETED"));
            progress.setCurrentStage("COMPLETED");
            progress.setMessage("Approval workflow completed");
            return progress;
        }
        if (Objects.equals(request.getStatus(), 3) && !running && active.isEmpty() && ended) {
            String rejected = null;
            for (HolidayApproval decision : decisions)
                if (decision.getResult() != null && Set.of(2, 4, 6).contains(decision.getResult()))
                    rejected = ApiMapper.stage(decision.getTaskDefKey());
            int rejectedIndex = index(steps, rejected);
            for (int i = 1; i < steps.size(); i++)
                steps.get(i)
                        .setStatus(
                                rejectedIndex < 0
                                        ? "NOT_REACHED"
                                        : i < rejectedIndex
                                                ? "COMPLETED"
                                                : i == rejectedIndex ? "REJECTED" : "NOT_REACHED");
            progress.setCurrentStage("REJECTED");
            progress.setMessage(
                    rejectedIndex < 0
                            ? "Request rejected"
                            : "Request rejected by " + steps.get(rejectedIndex).getLabel());
            return progress;
        }
        if (running
                && active.size() == 1
                && !Objects.equals(request.getStatus(), 3)
                && !Objects.equals(request.getStatus(), 4)
                && !Objects.equals(request.getStatus(), 5)) {
            String current = ApiMapper.stage(active.get(0).getTaskDefinitionKey());
            int currentIndex = index(steps, current);
            if (currentIndex > 0 && currentIndex < steps.size() - 1) {
                for (int i = 1; i < currentIndex; i++) steps.get(i).setStatus("COMPLETED");
                steps.get(currentIndex).setStatus("CURRENT");
                progress.setCurrentStage(current);
                progress.setMessage("Awaiting " + steps.get(currentIndex).getLabel() + " approval");
                return progress;
            }
        }
        // Do not guess an actionable stage from status or duration when engine evidence is missing.
        for (int i = 1; i < steps.size(); i++) steps.get(i).setStatus("UNKNOWN");
        progress.setCurrentStage("UNKNOWN");
        progress.setMessage("Workflow progress is unavailable");
        return progress;
    }

    private int index(List<WorkflowProgressStage> stages, String key) {
        for (int i = 0; i < stages.size(); i++) if (stages.get(i).getStage().equals(key)) return i;
        return -1;
    }
}
