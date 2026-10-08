package com.ada.approval;

import com.ada.approval.api.service.AdminApiService;
import com.ada.approval.api.dto.ApiDtos.*;
import com.ada.approval.api.error.ApiException;
import com.ada.approval.entity.HolidayType;
import com.ada.approval.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LeaveTypeAdminTest {
    AdminApiService service;
    HolidayTypeRepository types;
    HolidayApplyRepository requests;

    @BeforeEach
    void setup() {
        service = new AdminApiService();
        types = mock(HolidayTypeRepository.class);
        requests = mock(HolidayApplyRepository.class);
        ReflectionTestUtils.setField(service, "leaveTypes", types);
        ReflectionTestUtils.setField(service, "leaveRequests", requests);
    }

    HolidayType type() {
        HolidayType t = new HolidayType();
        t.setId(3);
        t.setHolidayType("Annual Leave");
        return t;
    }

    LeaveTypeRequest name(String name) {
        LeaveTypeRequest dto = new LeaveTypeRequest();
        dto.setName(name);
        return dto;
    }

    @Test
    void createsTrimmedName() {
        when(types.save(any()))
                .thenAnswer(
                        i -> {
                            HolidayType t = i.getArgument(0);
                            t.setId(3);
                            return t;
                        });
        var dto = service.createLeaveType(name("  Annual Leave  "));
        assertEquals(3, dto.getId());
        assertEquals("Annual Leave", dto.getName());
        verify(types).existsByHolidayTypeIgnoreCase("Annual Leave");
    }

    @Test
    void duplicateCreateFailsWithoutSaving() {
        when(types.existsByHolidayTypeIgnoreCase("annual leave")).thenReturn(true);
        assertThrows(ApiException.class, () -> service.createLeaveType(name("annual leave")));
        verify(types, never()).save(any());
    }

    @Test
    void updateExcludesItsOwnId() {
        when(types.findById(3)).thenReturn(Optional.of(type()));
        assertEquals("Sick Leave", service.updateLeaveType(3, name(" Sick Leave ")).getName());
        verify(types).existsByHolidayTypeIgnoreCaseAndIdNot("Sick Leave", 3);
        verify(types).save(any());
    }

    @Test
    void duplicateUpdateDoesNotMutateName() {
        HolidayType original = type();
        when(types.findById(3)).thenReturn(Optional.of(original));
        when(types.existsByHolidayTypeIgnoreCaseAndIdNot("Sick Leave", 3)).thenReturn(true);
        assertThrows(ApiException.class, () -> service.updateLeaveType(3, name("Sick Leave")));
        assertEquals("Annual Leave", original.getHolidayType());
        verify(types, never()).save(any());
    }

    @Test
    void deletesUnusedType() {
        HolidayType t = type();
        when(types.findById(3)).thenReturn(Optional.of(t));
        service.deleteLeaveType(3);
        verify(requests).existsByHolidayType(3);
        verify(types).delete(t);
        verifyNoMoreInteractions(requests);
    }

    @Test
    void referencedTypeNeverDeletes() {
        when(types.findById(3)).thenReturn(Optional.of(type()));
        when(requests.existsByHolidayType(3)).thenReturn(true);
        assertThrows(ApiException.class, () -> service.deleteLeaveType(3));
        verify(types, never()).delete(any());
    }

    @Test
    void missingTypeCannotUpdateOrDelete() {
        assertThrows(ApiException.class, () -> service.getLeaveType(99));
        assertThrows(ApiException.class, () -> service.updateLeaveType(99, name("Sick Leave")));
        assertThrows(ApiException.class, () -> service.deleteLeaveType(99));
        verifyNoInteractions(requests);
        verify(types, never()).save(any());
        verify(types, never()).delete(any());
    }

    @Test
    void searchPreservesPagination() {
        when(types.findByHolidayTypeContainingIgnoreCase(eq("Annual"), any()))
                .thenReturn(
                        new PageImpl<>(
                                Collections.singletonList(type()), PageRequest.of(1, 20), 21));
        var page = service.searchLeaveTypes(1, 20, " Annual ");
        assertEquals(1, page.getPage());
        assertEquals(21, page.getTotalElements());
        assertEquals("Annual Leave", page.getContent().get(0).getName());
    }
}
