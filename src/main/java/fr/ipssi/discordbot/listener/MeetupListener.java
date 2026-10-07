package fr.ipssi.discordbot.listener;

import fr.ipssi.discordbot.model.Meetup;
import fr.ipssi.discordbot.model.MeetupStatus;
import fr.ipssi.discordbot.model.RsvpStatus;
import fr.ipssi.discordbot.repository.MeetupRepository;
import fr.ipssi.discordbot.service.MeetupService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;

public final class MeetupListener extends ListenerAdapter {

    private final MeetupRepository meetupRepository;
    private final MeetupService meetupService;

    public MeetupListener(MeetupRepository meetupRepository, MeetupService meetupService) {
        this.meetupRepository = meetupRepository;
        this.meetupService = meetupService;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith(MeetupService.BUTTON_PREFIX)) {
            return;
        }

        String[] parts = componentId.substring(MeetupService.BUTTON_PREFIX.length()).split(":");
        long meetupId = Long.parseLong(parts[0]);

        Optional<Meetup> meetup = meetupRepository.find(meetupId)
                .filter(found -> found.status() == MeetupStatus.OPEN);
        if (meetup.isEmpty()) {
            event.reply("Cet événement n'accepte plus de réponses.").setEphemeral(true).queue();
            return;
        }

        long userId = event.getUser().getIdLong();
        if (parts[1].equals(MeetupService.REMIND_ACTION)) {
            toggleReminder(event, meetup.get(), userId);
        } else {
            toggleAnswer(event, meetup.get(), userId, RsvpStatus.valueOf(parts[1]));
        }
    }

    private void toggleAnswer(ButtonInteractionEvent event, Meetup meetup, long userId, RsvpStatus answer) {
        if (meetupRepository.findRsvp(meetup.id(), userId).filter(current -> current == answer).isPresent()) {
            meetupRepository.removeRsvp(meetup.id(), userId);
        } else {
            meetupRepository.saveRsvp(meetup.id(), userId, answer);
        }
        event.editMessageEmbeds(meetupService.render(meetup)).queue();
    }

    private void toggleReminder(ButtonInteractionEvent event, Meetup meetup, long userId) {
        boolean subscribed = meetupRepository.hasReminder(meetup.id(), userId);
        if (!subscribed && !Instant.now().isBefore(meetupService.reminderTime(meetup))) {
            event.reply("Trop tard pour activer un rappel : l'événement commence dans moins d'une heure.")
                    .setEphemeral(true).queue();
            return;
        }

        if (subscribed) {
            meetupRepository.removeReminder(meetup.id(), userId);
        } else {
            meetupRepository.addReminder(meetup.id(), userId);
        }
        String confirmation = subscribed
                ? "Rappel désactivé."
                : "C'est noté, je te préviens une heure avant le début.";
        event.editMessageEmbeds(meetupService.render(meetup))
                .queue(hook -> hook.sendMessage(confirmation).setEphemeral(true).queue());
    }
}
