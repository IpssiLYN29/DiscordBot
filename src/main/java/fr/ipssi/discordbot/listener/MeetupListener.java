package fr.ipssi.discordbot.listener;

import fr.ipssi.discordbot.model.Meetup;
import fr.ipssi.discordbot.model.MeetupStatus;
import fr.ipssi.discordbot.model.RsvpStatus;
import fr.ipssi.discordbot.repository.MeetupRepository;
import fr.ipssi.discordbot.service.MeetupService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

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
        RsvpStatus answer = RsvpStatus.valueOf(parts[1]);

        Optional<Meetup> meetup = meetupRepository.find(meetupId)
                .filter(found -> found.status() == MeetupStatus.OPEN);
        if (meetup.isEmpty()) {
            event.reply("Cet événement n'accepte plus de réponses.").setEphemeral(true).queue();
            return;
        }

        long userId = event.getUser().getIdLong();
        if (meetupRepository.findRsvp(meetupId, userId).filter(current -> current == answer).isPresent()) {
            meetupRepository.removeRsvp(meetupId, userId);
        } else {
            meetupRepository.saveRsvp(meetupId, userId, answer);
        }
        event.editMessageEmbeds(meetupService.render(meetup.get())).queue();
    }
}
