package com.yoedi.epos.report;

import com.yoedi.epos.report.dto.DailyReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Reports are OWNER-only (docs/api-spec.md, "Access rights summary"). */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/daily")
    public DailyReportResponse daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.daily(date);
    }
}
