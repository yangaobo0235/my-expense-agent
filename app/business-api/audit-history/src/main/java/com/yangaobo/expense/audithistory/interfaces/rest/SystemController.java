package com.yangaobo.expense.audithistory.interfaces.rest;

import com.yangaobo.expense.common.api.ServiceInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final String serviceName;

    public SystemController(@Value("${spring.application.name}") String serviceName) {
        this.serviceName = serviceName;
    }

    @GetMapping
    public ServiceInfoResponse info() {
        return ServiceInfoResponse.of(serviceName, SystemController.class);
    }
}
