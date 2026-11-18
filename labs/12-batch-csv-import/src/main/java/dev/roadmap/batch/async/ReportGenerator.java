package dev.roadmap.batch.async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

/**
 * Fan-out / fan-in with CompletableFuture: start every section at once, give each a timeout and a fallback,
 * then wait for all of them. Total latency is the slowest section, not the sum.
 */
@Service
public class ReportGenerator {

    private final ReportService reportService;

    public ReportGenerator(ReportService reportService) {
        this.reportService = reportService;
    }

    public Report generate(List<String> sectionNames) throws InterruptedException {
        List<CompletableFuture<Report.Section>> sections = new ArrayList<>();
        for (String name : sectionNames) {
            sections.add(reportService.fetchSection(name)
                    .orTimeout(2, TimeUnit.SECONDS)                     // never wait forever on one downstream
                    .exceptionally(failure -> Report.Section.unavailable(name))); // degrade, don't fail the report
        }
        return CompletableFuture.allOf(sections.toArray(CompletableFuture[]::new))
                .thenApply(done -> new Report(sections.stream().map(CompletableFuture::join).toList()))
                .join();
    }
}
