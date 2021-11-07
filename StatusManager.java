import java.util.Random;

import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;

public class StatusManager implements Runnable {
	private Thread thread;

	public String parseActivity(String text) {
		int userCount = 0;
		int guildCount = 0;
		for (Guild guild: Main.jda.getGuilds()) {
			guildCount++;
			userCount += guild.getMemberCount();
		}
		text = text.replace("[users]", String.valueOf(userCount));
		text = text.replace("[guilds]", String.valueOf(guildCount));
		return text;
	}

	public void run() {
		Random random = new Random(System.currentTimeMillis());
		while (true) {
			try {
				String[] activities = {"playing", "watching"};
				String[] playingActivities = {"in [guilds] servers", "with [users] users"};
				String[] watchingActivities = {"over [guilds] servers", "over the internet"};

				String activityType = activities[random.nextInt(activities.length)];
				if (activityType == "playing") {
					String activityName = playingActivities[random.nextInt(playingActivities.length)];
					Main.jda.getPresence().setActivity(Activity.playing(parseActivity(activityName)));
				} else if (activityType == "watching") {
					String activityName = watchingActivities[random.nextInt(watchingActivities.length)];
					Main.jda.getPresence().setActivity(Activity.watching(parseActivity(activityName)));
				}
				Thread.sleep(60000);
			} catch (InterruptedException error) {
				break;
			}
		}
	}

	public void start() {
		if (thread == null) {
			thread = new Thread(this);
			thread.start();
		}
	}
}
