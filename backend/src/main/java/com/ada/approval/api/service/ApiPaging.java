package com.ada.approval.api.service;

import com.ada.approval.api.error.ApiException;
import org.springframework.data.domain.*;
import com.ada.approval.api.dto.ApiDtos.PageResponse;

import java.util.function.Function;
import java.util.stream.Collectors;

/** Normalizes REST pagination and required-resource checks independently of authorization. */
public final class ApiPaging {
    private ApiPaging() {}

    public static Pageable request(int page, int size) {
        if (page < 0 || page == Integer.MAX_VALUE || size < 1 || size > 100)
            throw new ApiException(
                    400, "Page must be non-negative and size must be between 1 and 100");
        return PageRequest.of(page, size, Sort.by("id").ascending());
    }

    public static <T, R> PageResponse<R> map(Page<T> page, Function<T, R> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).collect(Collectors.toList()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    public static <T> T required(T value) {
        if (value == null) throw new ApiException(404, "Resource not found");
        return value;
    }
}
