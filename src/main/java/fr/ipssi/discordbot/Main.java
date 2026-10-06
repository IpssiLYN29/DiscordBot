package fr.ipssi.discordbot;

import fr.ipssi.discordbot.command.AnnounceCommand;
import fr.ipssi.discordbot.command.ClearCommand;
import fr.ipssi.discordbot.command.CommandManager;
import fr.ipssi.discordbot.command.ConfigCommand;
import fr.ipssi.discordbot.command.DeadlineCommand;
import fr.ipssi.discordbot.command.DeadlinesCommand;
import fr.ipssi.discordbot.command.MeetupCommand;
import fr.ipssi.discordbot.command.MuteCommand;
import fr.ipssi.discordbot.command.PingCommand;
import fr.ipssi.discordbot.command.PlanningCommand;
import fr.ipssi.discordbot.command.PollCommand;
import fr.ipssi.discordbot.command.ResourceCommand;
import fr.ipssi.discordbot.command.RolesPanelCommand;
import fr.ipssi.discordbot.command.RulesPanelCommand;
import fr.ipssi.discordbot.command.UnmuteCommand;
import fr.ipssi.discordbot.command.WarnCommand;
import fr.ipssi.discordbot.command.WarnsCommand;
import fr.ipssi.discordbot.listener.MeetupListener;
import fr.ipssi.discordbot.listener.MemberListener;
import fr.ipssi.discordbot.listener.PollListener;
import fr.ipssi.discordbot.listener.RoleSelectionListener;
import fr.ipssi.discordbot.listener.RulesListener;
import fr.ipssi.discordbot.repository.DeadlineRepository;
import fr.ipssi.discordbot.repository.MeetupRepository;
import fr.ipssi.discordbot.repository.PollRepository;
import fr.ipssi.discordbot.repository.ResourceRepository;
import fr.ipssi.discordbot.repository.ScheduleRepository;
import fr.ipssi.discordbot.repository.SettingsRepository;
import fr.ipssi.discordbot.repository.WarnRepository;
import fr.ipssi.discordbot.repository.sqlite.SqliteDatabase;
import fr.ipssi.discordbot.repository.sqlite.SqliteDeadlineRepository;
import fr.ipssi.discordbot.repository.sqlite.SqliteMeetupRepository;
import fr.ipssi.discordbot.repository.sqlite.SqlitePollRepository;
import fr.ipssi.discordbot.repository.sqlite.SqliteResourceRepository;
import fr.ipssi.discordbot.repository.sqlite.SqliteScheduleRepository;
import fr.ipssi.discordbot.repository.sqlite.SqliteSettingsRepository;
import fr.ipssi.discordbot.repository.sqlite.SqliteWarnRepository;
import fr.ipssi.discordbot.service.LogService;
import fr.ipssi.discordbot.service.DeadlineReminderService;
import fr.ipssi.discordbot.service.MeetupService;
import fr.ipssi.discordbot.service.PlanningRecapService;
import fr.ipssi.discordbot.service.PollService;
import fr.ipssi.discordbot.service.ScheduleReminderService;
import fr.ipssi.discordbot.service.SettingsService;
import fr.ipssi.discordbot.service.TaskScheduler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public final class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final Duration REMINDER_CHECK_PERIOD = Duration.ofMinutes(1);
    private static final Duration DEV_DEMO_DELAY = Duration.ofSeconds(10);

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        Config config = Config.load();

        SqliteDatabase database = new SqliteDatabase(config.databaseFile());
        WarnRepository warnRepository = new SqliteWarnRepository(database);
        SettingsRepository settingsRepository = new SqliteSettingsRepository(database);
        DeadlineRepository deadlineRepository = new SqliteDeadlineRepository(database);
        ScheduleRepository scheduleRepository = new SqliteScheduleRepository(database);
        PollRepository pollRepository = new SqlitePollRepository(database);
        MeetupRepository meetupRepository = new SqliteMeetupRepository(database);
        ResourceRepository resourceRepository = new SqliteResourceRepository(database);

        SettingsService settings = new SettingsService(settingsRepository);
        LogService logService = new LogService(settings);
        PlanningRecapService recapService = new PlanningRecapService(scheduleRepository, settings);
        PollService pollService = new PollService(pollRepository);
        MeetupService meetupService = new MeetupService(meetupRepository);

        CommandManager commandManager = new CommandManager();
        commandManager.register(new PingCommand());
        commandManager.register(new ConfigCommand(settings));
        commandManager.register(new ClearCommand(logService));
        commandManager.register(new WarnCommand(warnRepository, logService));
        commandManager.register(new WarnsCommand(warnRepository));
        commandManager.register(new MuteCommand(logService));
        commandManager.register(new UnmuteCommand(logService));
        commandManager.register(new DeadlineCommand(deadlineRepository));
        commandManager.register(new DeadlinesCommand(deadlineRepository));
        commandManager.register(new PlanningCommand(scheduleRepository, recapService));
        commandManager.register(new PollCommand(pollRepository, pollService));
        commandManager.register(new MeetupCommand(meetupRepository, meetupService));
        commandManager.register(new ResourceCommand(resourceRepository));
        commandManager.register(new AnnounceCommand());
        commandManager.register(new RulesPanelCommand());
        commandManager.register(new RolesPanelCommand(settings));

        JDA jda = JDABuilder.createLight(config.token(), GatewayIntent.GUILD_MEMBERS)
                .setActivity(Activity.watching("IPSSI Lyon"))
                .addEventListeners(
                        commandManager,
                        new MemberListener(settings, logService),
                        new RulesListener(settings),
                        new RoleSelectionListener(settings),
                        new PollListener(pollRepository, pollService),
                        new MeetupListener(meetupRepository, meetupService))
                .build()
                .awaitReady();

        commandManager.publish(jda);

        TaskScheduler taskScheduler = new TaskScheduler();
        taskScheduler.schedule(new DeadlineReminderService(jda, deadlineRepository, settings), REMINDER_CHECK_PERIOD);
        ScheduleReminderService scheduleReminders = new ScheduleReminderService(jda, scheduleRepository, settings);
        taskScheduler.schedule(scheduleReminders, REMINDER_CHECK_PERIOD);
        taskScheduler.schedule(() -> recapService.refreshOutdated(jda), REMINDER_CHECK_PERIOD);
        taskScheduler.schedule(() -> pollService.closeDue(jda), REMINDER_CHECK_PERIOD);
        taskScheduler.schedule(() -> meetupService.remindUpcoming(jda), REMINDER_CHECK_PERIOD);
        taskScheduler.schedule(() -> meetupService.endStarted(jda), REMINDER_CHECK_PERIOD);
        recapService.resetAll(jda);

        if (config.devMode()) {
            LOGGER.info("Dev mode enabled: announcing the next class in {}s", DEV_DEMO_DELAY.toSeconds());
            taskScheduler.scheduleOnce(() -> jda.getGuilds().forEach(scheduleReminders::announceNext), DEV_DEMO_DELAY);
        }
        LOGGER.info("Bot started as {}", jda.getSelfUser().getName());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            taskScheduler.stop();
            jda.shutdown();
        }));


    }
    public static Logger getLogger() {
        return LOGGER;
    }
}
