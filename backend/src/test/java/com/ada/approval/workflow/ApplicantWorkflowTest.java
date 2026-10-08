package com.ada.approval.workflow;

import com.ada.approval.entity.*;
import com.ada.approval.repository.*;
import com.ada.approval.api.error.ApiException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicantWorkflowTest {
    final EmployeeRepository employees = mock(EmployeeRepository.class);
    final DeptRepository departments = mock(DeptRepository.class);
    final TitleCategoryRepository titles = mock(TitleCategoryRepository.class);
    final AccountRepository accounts = mock(AccountRepository.class);
    final com.ada.approval.config.security.BackendAuthorization authorization =
            mock(com.ada.approval.config.security.BackendAuthorization.class);
    final ApplicantWorkflow service =
            new ApplicantWorkflow(employees, departments, titles, accounts, authorization);
    Account applicant;
    Employee employee;

    @BeforeEach
    void setup() {
        when(authorization.canApprove(any())).thenReturn(true);
        applicant = new Account();
        applicant.setId(11);
        applicant.setEmpId(1);
        applicant.setUserName("employee");
        applicant.setStatus(1);
        employee = new Employee();
        employee.setId(1);
        employee.setStatus(1);
        employee.setTitleCategoryId(1);
        when(employees.findByIdAndStatus(1, 1)).thenReturn(employee);
        title("Employee");
        Employee manager = approver(3, "manager"),
                boss = approver(4, "boss"),
                hr = approver(5, "hr");
        when(employees.findDepartmentManager(1)).thenReturn(manager);
        when(employees.findAllByJobTitles(
                        com.ada.approval.workflow.WorkflowTitleNames.generalManagerNames()))
                .thenReturn(List.of(boss));
        when(employees.findAllByJobTitles(com.ada.approval.workflow.WorkflowTitleNames.hrNames()))
                .thenReturn(List.of(hr));
    }

    void title(String name) {
        TitleCategory t = new TitleCategory();
        t.setTitleName(name);
        when(titles.findById(1)).thenReturn(Optional.of(t));
    }

    Employee approver(int id, String name) {
        Employee e = new Employee();
        e.setId(id);
        e.setStatus(1);
        Account a = new Account();
        a.setId(id + 10);
        a.setEmpId(id);
        a.setStatus(1);
        a.setUserName(name);
        when(accounts.findByEmpId(id)).thenReturn(a);
        return e;
    }

    @ParameterizedTest
    @CsvSource({
        "EMPLOYEE,2,manager_check:hr_check",
        "EMPLOYEE,3,manager_check:hr_check",
        "EMPLOYEE,4,manager_check:boss_check:hr_check",
        "DEPARTMENT_MANAGER,2,boss_check:hr_check",
        "DEPARTMENT_MANAGER,4,boss_check:hr_check",
        "GENERAL_MANAGER,2,hr_check",
        "GENERAL_MANAGER,4,hr_check",
        "HR,2,boss_check",
        "HR,4,boss_check"
    })
    void actualBpmnRouteMatchesProgressAndBusinessRules(String identity, int days, String expected)
            throws Exception {
        var vars = new HashMap<String, Object>();
        vars.put("applicantWorkflowIdentity", identity);
        vars.put("day", days);
        List<String> taskKeys = new ArrayList<>();
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        org.w3c.dom.Document doc;
        try (var input = getClass().getResourceAsStream("/bpmn/hr_employee_holiday.bpmn")) {
            doc = factory.newDocumentBuilder().parse(input);
        }
        var nodes =
                doc.getElementsByTagNameNS(
                        "http://www.omg.org/spec/BPMN/20100524/MODEL", "sequenceFlow");
        String current = "Activity_158mas7";
        var context =
                new org.springframework.expression.spel.support.StandardEvaluationContext(vars);
        context.addPropertyAccessor(new org.springframework.context.expression.MapAccessor());
        var parser = new org.springframework.expression.spel.standard.SpelExpressionParser();
        for (int guard = 0; !current.equals("Event_1judlho") && guard < 20; guard++) {
            List<String> targets = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                var flow = (org.w3c.dom.Element) nodes.item(i);
                if (!current.equals(flow.getAttribute("sourceRef"))) continue;
                String condition = flow.getTextContent().trim();
                if (condition.isEmpty()
                        || Boolean.TRUE.equals(
                                parser.parseExpression(
                                                condition.substring(2, condition.length() - 1))
                                        .getValue(context, Boolean.class)))
                    targets.add(flow.getAttribute("targetRef"));
            }
            assertEquals(1, targets.size(), "Exactly one outgoing route at " + current);
            current = targets.get(0);
            if (current.endsWith("_check")) taskKeys.add(current);
        }
        assertEquals("Event_1judlho", current);
        assertEquals(List.of(expected.split(":")), taskKeys);
        assertEquals(
                taskKeys.stream().map(com.ada.approval.api.dto.ApiMapper::stage).toList(),
                ApplicantWorkflow.stages(ApplicantWorkflow.Identity.valueOf(identity), days));
    }

    @Test
    void employeeResolvesOnlyRequiredApproversUsingAccountUsernames() {
        var vars = service.variables(applicant, 3);
        assertEquals("manager", vars.get("manager"));
        assertEquals("hr", vars.get("hrs"));
        assertFalse(vars.containsKey("boss"));
        assertEquals("EMPLOYEE", vars.get("applicantWorkflowIdentity"));
    }

    @Test
    void managerIdentityUsesDepartmentLink() {
        when(departments.existsByManagerIdAndStatus(1, 1)).thenReturn(true);
        assertEquals(ApplicantWorkflow.Identity.DEPARTMENT_MANAGER, service.identity(applicant));
        assertFalse(service.variables(applicant, 2).containsKey("manager"));
    }

    @Test
    void generalManagerTitleTakesPrecedenceOverDepartmentLink() {
        title("\u603b\u7ecf\u7406");
        when(departments.existsByManagerIdAndStatus(1, 1)).thenReturn(true);
        assertEquals(ApplicantWorkflow.Identity.GENERAL_MANAGER, service.identity(applicant));
        assertEquals(
                Set.of("user", "day", "applicantWorkflowIdentity", "hrs"),
                service.variables(applicant, 5).keySet());
    }

    @Test
    void hrApplicantRequiresOnlyGeneralManager() {
        title("\u4eba\u4e8b");
        assertEquals(
                Set.of("user", "day", "applicantWorkflowIdentity", "boss"),
                service.variables(applicant, 2).keySet());
    }

    @Test
    void systemAdminCannotApplyRegardlessOfPermissions() {
        title("Administrator");
        assertFalse(service.canApply(applicant));
        assertThrows(ApiException.class, () -> service.variables(applicant, 2));
    }

    @Test
    void missingEmployeeCannotApply() {
        when(employees.findByIdAndStatus(1, 1)).thenReturn(null);
        assertFalse(service.canApply(applicant));
    }

    @Test
    void selfAssignedManagerFailsBeforeProcessStart() {
        when(accounts.findByEmpId(3)).thenReturn(applicant);
        assertThrows(ApiException.class, () -> service.variables(applicant, 2));
    }

    @Test
    void disabledApproverFails() {
        Account a = new Account();
        a.setStatus(0);
        when(accounts.findByEmpId(5)).thenReturn(a);
        assertThrows(ApiException.class, () -> service.variables(applicant, 2));
    }

    @Test
    void revokedApprovalGrantFailsBeforeSubmission() {
        when(authorization.canApprove(any())).thenReturn(false);
        assertThrows(ApiException.class, () -> service.variables(applicant, 2));
    }

    @Test
    void ambiguousGeneralManagerFails() {
        var bosses = List.of(approver(4, "boss"), approver(7, "boss2"));
        when(employees.findAllByJobTitles(
                        com.ada.approval.workflow.WorkflowTitleNames.generalManagerNames()))
                .thenReturn(bosses);
        assertThrows(ApiException.class, () -> service.variables(applicant, 4));
    }

    @Test
    void englishGeneralManagerRetainsTheSameRoute() {
        title("General Manager");
        assertEquals(ApplicantWorkflow.Identity.GENERAL_MANAGER, service.identity(applicant));
        assertEquals(
                Set.of("user", "day", "applicantWorkflowIdentity", "hrs"),
                service.variables(applicant, 5).keySet());
    }

    @Test
    void englishHrRetainsTheSameRoute() {
        title("HR");
        assertEquals(ApplicantWorkflow.Identity.HR, service.identity(applicant));
        assertEquals(
                Set.of("user", "day", "applicantWorkflowIdentity", "boss"),
                service.variables(applicant, 2).keySet());
    }

    @Test
    void approverQueriesIncludeEnglishAndHistoricalNamesTogether() {
        service.variables(applicant, 4);
        verify(employees).findAllByJobTitles(WorkflowTitleNames.generalManagerNames());
        verify(employees).findAllByJobTitles(WorkflowTitleNames.hrNames());
        assertEquals(2, WorkflowTitleNames.generalManagerNames().size());
        assertEquals(2, WorkflowTitleNames.hrNames().size());
    }

    @Test
    void titleNormalizationDoesNotTranslateUnrelatedUserData() {
        assertEquals(
                "General Manager",
                WorkflowTitleNames.display(WorkflowTitleNames.generalManagerNames().get(1)));
        assertEquals("HR", WorkflowTitleNames.display(WorkflowTitleNames.hrNames().get(1)));
        assertEquals("Engineer", WorkflowTitleNames.display("Engineer"));
        assertNull(WorkflowTitleNames.display(null));
        assertTrue(WorkflowTitleNames.equivalent("HR", WorkflowTitleNames.hrNames().get(1)));
        assertFalse(WorkflowTitleNames.equivalent("HR", "Employee"));
    }

    @Test
    void englishGeneralManagerDtoDoesNotRewriteTheEntity() {
        TitleCategory stored = new TitleCategory();
        stored.setId(42);
        stored.setTitleName(WorkflowTitleNames.generalManagerNames().get(1));
        var dto = com.ada.approval.api.dto.ApiMapper.title(stored);
        assertEquals("General Manager", dto.getName());
        assertEquals("General Manager", dto.getDisplayName());
        assertEquals(42, dto.getId());
        assertEquals(WorkflowTitleNames.generalManagerNames().get(1), stored.getTitleName());
    }

    @Test
    void activitiParsesAndValidatesProductionBpmnWithoutStartingEngine() throws Exception {
        try (var input = getClass().getResourceAsStream("/bpmn/hr_employee_holiday.bpmn")) {
            var reader = javax.xml.stream.XMLInputFactory.newFactory().createXMLStreamReader(input);
            try {
                var model =
                        new org.activiti.bpmn.converter.BpmnXMLConverter()
                                .convertToBpmnModel(reader);
                assertEquals("hr_employee_holiday", model.getMainProcess().getId());
                assertTrue(
                        new org.activiti.validation.ProcessValidatorFactory()
                                .createDefaultProcessValidator()
                                .validate(model)
                                .isEmpty());
            } finally {
                reader.close();
            }
        }
    }
}
