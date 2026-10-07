package fr.ipssi.discordbot.service;

import fr.ipssi.discordbot.model.ScheduleEvent;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public record ScheduleDiff(List<ScheduleEvent> added, List<ScheduleEvent> removed, List<Change> changed) {

    public record Change(ScheduleEvent before, ScheduleEvent after) {
    }

    private static final Comparator<ScheduleEvent> BY_START = Comparator.comparing(ScheduleEvent::startsAt);

    public static ScheduleDiff compute(List<ScheduleEvent> before, List<ScheduleEvent> after, Instant now) {
        if (before.isEmpty()) {
            return new ScheduleDiff(List.of(), List.of(), List.of());
        }

        Map<String, ScheduleEvent> previous = new HashMap<>();
        before.forEach(event -> previous.put(event.uid(), event));
        Map<String, ScheduleEvent> current = new HashMap<>();
        after.forEach(event -> current.put(event.uid(), event));

        List<ScheduleEvent> added = after.stream()
                .filter(event -> !previous.containsKey(event.uid()))
                .filter(event -> event.startsAt().isAfter(now))
                .sorted(BY_START)
                .toList();
        List<ScheduleEvent> removed = before.stream()
                .filter(event -> !current.containsKey(event.uid()))
                .filter(event -> event.startsAt().isAfter(now))
                .sorted(BY_START)
                .toList();
        List<Change> changed = after.stream()
                .filter(event -> previous.containsKey(event.uid()))
                .map(event -> new Change(previous.get(event.uid()), event))
                .filter(change -> isDifferent(change.before(), change.after()))
                .filter(change -> change.before().startsAt().isAfter(now) || change.after().startsAt().isAfter(now))
                .sorted(Comparator.comparing(change -> change.after().startsAt()))
                .toList();

        return new ScheduleDiff(added, removed, changed);
    }

    public ScheduleDiff filter(Predicate<ScheduleEvent> relevant) {
        return new ScheduleDiff(
                added.stream().filter(relevant).toList(),
                removed.stream().filter(relevant).toList(),
                changed.stream().filter(change -> relevant.test(change.before()) || relevant.test(change.after())).toList());
    }

    public int size() {
        return added.size() + removed.size() + changed.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    private static boolean isDifferent(ScheduleEvent before, ScheduleEvent after) {
        return !before.title().equals(after.title())
                || !before.teacher().equals(after.teacher())
                || !before.location().equals(after.location())
                || !before.startsAt().equals(after.startsAt())
                || !before.endsAt().equals(after.endsAt())
                || !before.promos().equals(after.promos());
    }
}
