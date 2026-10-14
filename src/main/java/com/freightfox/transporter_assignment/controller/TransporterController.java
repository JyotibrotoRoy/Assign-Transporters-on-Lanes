package com.freightfox.transporter_assignment.controller;

import com.freightfox.transporter_assignment.dto.request.AssignmentRequest;
import com.freightfox.transporter_assignment.dto.request.InputRequest;
import com.freightfox.transporter_assignment.dto.response.AssignmentResponse;
import com.freightfox.transporter_assignment.dto.response.InputResponse;
import com.freightfox.transporter_assignment.service.AssignmentService;
import com.freightfox.transporter_assignment.service.InputService;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transporters")
public class TransporterController {

    private final InputService inputService;
    private final AssignmentService assignmentService;

    public TransporterController(InputService inputService, AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
        this.inputService = inputService;
    }

    @PostMapping("/input")
    public ResponseEntity<InputResponse> submitInput(@RequestBody InputRequest request) {

        inputService.saveInput(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InputResponse.saved());
    }

    @PostMapping("/assignment")
    public ResponseEntity<AssignmentResponse> assignTransporters(@RequestBody AssignmentRequest request) {
        AssignmentResponse response = assignmentService.assign(request);
        return ResponseEntity.ok(response);
    }
}
