package dev.roadmap.aop.selfinvocation;

import java.util.List;

import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import dev.roadmap.aop.CallLog;
import dev.roadmap.aop.logging.Loggable;

/** Demonstrates the self-invocation trap and two fixes. Restructuring (the preferred fix) is shown in the note. */
@Service
public class ReportService {

    private final CallLog callLog;
    private final ObjectProvider<ReportService> self; // resolved lazily, so no circular-reference failure at startup

    public ReportService(CallLog callLog, ObjectProvider<ReportService> self) {
        this.callLog = callLog;
        this.self = self;
    }

    @Loggable
    public String render(String name) {
        return "report:" + name;
    }

    /** TRAP: this.render() runs on the raw target, never through the proxy, so LoggingAspect never sees it. */
    public List<String> renderAllDirect(List<String> names) {
        return names.stream().map(this::render).toList();
    }

    /** FIX 1: call through the proxy, fetched lazily from the container. */
    public List<String> renderAllViaSelf(List<String> names) {
        ReportService proxy = self.getObject();
        return names.stream().map(proxy::render).toList();
    }

    /** FIX 2 (last resort): needs exposeProxy = true, and couples the class to Spring AOP. */
    public List<String> renderAllViaCurrentProxy(List<String> names) {
        ReportService proxy = (ReportService) AopContext.currentProxy();
        return names.stream().map(proxy::render).toList();
    }

    /**
     * final: CGLIB can't override it, so the call runs on the PROXY instance itself. Objenesis created that instance
     * without running the constructor, so its fields are null, and no advice fires.
     */
    @Loggable
    public final boolean hasCallLog() {
        return callLog != null;
    }
}
