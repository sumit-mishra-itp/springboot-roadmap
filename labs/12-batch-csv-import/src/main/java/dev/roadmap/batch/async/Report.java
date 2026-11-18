package dev.roadmap.batch.async;

import java.util.List;

public record Report(List<Section> sections) {

    public record Section(String name, String content, ExecutionInfo executedOn) {

        static Section unavailable(String name) {
            return new Section(name, "unavailable", null);
        }
    }
}
