import com.github.sh0nk.matplotlib4j.Plot;

import java.awt.Color;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import javax.security.auth.login.LoginException;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import org.json.*;

public class Main extends ListenerAdapter {
	public static long startTime = System.currentTimeMillis();
	public static Color embedColor = new Color(123, 212, 137);
	public static List<String> members = new ArrayList<String>();
	public static List<String> channels = new ArrayList<String>();
	public static List<String> messages = new ArrayList<String>();
	public static HashMap<String, Long> currencyCooldowns = new HashMap<String, Long>();
	public static String inviteLink = "";
	public static int cacheSize = 1024;
	public static JDA jda;

	public static void main(String[] arguments) throws LoginException {
		String botToken = System.getenv("TOKEN");
		if (botToken == null) {
			System.out.println("Unable to load the TOKEN environment variable");
			System.exit(1);
		}
		jda = JDABuilder.createDefault(botToken)
			.addEventListeners(new Main())
			.build();

		CommandListUpdateAction commands = jda.updateCommands();
		commands.addCommands(
			new CommandData("ping", "Display the bot's current latency")
		);
		commands.addCommands(
			new CommandData("bot", "View statistics for this bot")
				.addSubcommands(new SubcommandData("status", "View the bot's current status"))
				.addSubcommands(new SubcommandData("guilds", "View the amount of guilds the bot is in"))
				.addSubcommands(new SubcommandData("invite", "Invite the bot to another server"))
		);
		commands.addCommands(
			new CommandData("server", "View all kinds of information about this server")
				.addSubcommands(new SubcommandData("statistics", "View statistics for this server"))
		);
		commands.addCommands(
			new CommandData("fetch", "Fetch all kinds of different information")
				.addSubcommands(new SubcommandData("astronauts", "Fetch the amount of people who are currently in space"))
		);
		commands.addCommands(
			new CommandData("currency", "View information about different currencies")
				.addSubcommands(new SubcommandData("stocks", "Check the stock history of a specific currency")
					.addOptions(new OptionData(OptionType.STRING, "currency", "The currency you want to check the stocks for", true))
					.addOptions(new OptionData(OptionType.INTEGER, "days", "The amount of days you want to check the stocks for", false))
				)
		);
		commands.queue();
	}

	@Override
	public void onReady(ReadyEvent event) {
		CacheManager cacheManager = new CacheManager();
		cacheManager.start();
		StatusManager statusManager = new StatusManager();
		statusManager.start();
		String username = jda.getSelfUser().getName();
		String discriminator = jda.getSelfUser().getDiscriminator();
		inviteLink = String.format("https://discord.com/oauth2/authorize?client_id=%s&scope=applications.commands%%20bot", jda.getSelfUser().getId());
		double timeDifference = System.currentTimeMillis() - startTime;
		System.out.println(String.format("Successfully logged in as %s#%s in %.2f seconds", username, discriminator, timeDifference/1000));
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (event.getChannelType() == ChannelType.PRIVATE) {
			return;
		}

		String guildMemberId = event.getGuild().getId() + event.getAuthor().getId();
		if (!members.contains(guildMemberId)) {
			members.add(guildMemberId);
		}
		String guildChannelId = event.getGuild().getId() + event.getChannel().getId();
		if (!channels.contains(guildChannelId)) {
			channels.add(guildChannelId);
		}
		messages.add(String.format("%s.%d", event.getGuild().getId(), System.currentTimeMillis()));
	}

	@Override
	public void onButtonClick(ButtonClickEvent event) {
		if (event.getComponentId().equals("server-count")) {
			event.reply(getGuildCount()).setEphemeral(true).queue();
		}
	}

	@Override
	public void onSlashCommand(SlashCommandEvent event) {
		if (event.getGuild() == null) {
			return;
		}

		switch (event.getName()) {
			case "ping":
				pingCommand(event);
				return;
			case "bot":
				switch (event.getSubcommandName()) {
					case "status":
						botStatusCommand(event);
						return;
					case "guilds":
						botGuildsCommand(event);
						return;
					case "invite":
						botInviteCommand(event);
						return;
				}
			case "server":
				switch (event.getSubcommandName()) {
					case "statistics":
						serverStatisticsCommand(event);
						return;
				}
			case "fetch":
				switch (event.getSubcommandName()) {
					case "astronauts":
						fetchAstronautsCommand(event);
						return;
				}
			case "currency":
				switch (event.getSubcommandName()) {
					case "stocks":
						long days = 5;
						if (event.getOption("days") != null) {
							days = event.getOption("days").getAsLong();
						}
						currencyStocksCommand(event, event.getOption("currency").getAsString().toLowerCase(), days);
						return;
				}
			default:
				event.reply("The command you just used is no longer available!").setEphemeral(true).queue();
				return;
		}
	}

	public static String getGuildCount() {
		String displayName = "guilds";
		if (jda.getGuilds().size() == 1) {
			displayName = "guild";
		}
		return String.format("I am currently in **%s %s**", jda.getGuilds().size(), displayName);
	}

	public static double getProcessCpuLoad() throws Exception {
		MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		ObjectName name = ObjectName.getInstance("java.lang:type=OperatingSystem");
		AttributeList list = server.getAttributes(name, new String[]{ "ProcessCpuLoad" });

		if (list.isEmpty()) {
			return Double.NaN;
		}
		Attribute attribute = (Attribute) list.get(0);
		Double value = (Double) attribute.getValue();
		if (value == -1.0) {
			return Double.NaN;
		}
		return ((int) (value * 1000) / 10.0);
	}

	public void pingCommand(SlashCommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setTitle("Pong :ping_pong:");
		embed.setDescription(String.format("Latency: **%d ms**", jda.getGatewayPing()));
		embed.setColor(embedColor);
		event.replyEmbeds(embed.build()).queue();
	}

	public void botInviteCommand(SlashCommandEvent event) {
		event.reply("Here is the link to add me to your server")
			.addActionRow(
				Button.link(inviteLink, "Invite Link"),
				Button.secondary("server-count", "Server Count")
			)
			.queue();
	}

	public void botGuildsCommand(SlashCommandEvent event) {
		event.reply(getGuildCount()).queue();
	}

	public void botStatusCommand(SlashCommandEvent event) {
		try {
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(embedColor);
			float timeDifference = (System.currentTimeMillis() - startTime) / 1000;
			float minutesTime = timeDifference / 60;
			float hoursTime = minutesTime / 60;
			minutesTime = minutesTime % 60;
			MemoryUsage heapMemoryUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

			embed.addField("API Latency", String.format("```%d ms```", jda.getGatewayPing()), true);
			embed.addField("CPU Usage", String.format("```%s%%```", getProcessCpuLoad()), true);
			embed.addField("RAM Usage", String.format("```%s MB```", heapMemoryUsage.getUsed()/1000000), true);
			embed.addField("Threads", String.format("```%s```", ManagementFactory.getThreadMXBean().getThreadCount()), true);
			embed.addField("Guilds", String.format("```%s```", jda.getGuilds().size()), true);
			embed.addField("Members", String.format("```%s```", jda.getUsers().size()), true);
			embed.addField("Channels", String.format("```%s```", jda.getTextChannels().size()), true);
			embed.addField("Roles", String.format("```%s```", jda.getRoles().size()), true);
			embed.addField("Uptime", String.format("```%.0fh %.0fm```", Math.floor(hoursTime), Math.floor(minutesTime)), true);
			event.replyEmbeds(embed.build()).queue();
		} catch (Exception error) {
			String errorMessage = error.getMessage();
			event.reply(String.format("Unable to get bot status: `%s`", errorMessage)).setEphemeral(true).queue();
		}
	}

	public void currencyStocksCommand(SlashCommandEvent event, String currency, long days) {
		if (days > 30) {
			event.reply("You can only fetch up to **30 days** of stock history!").setEphemeral(true).queue();
			return;
		} else if (days < 1) {
			event.reply("You need to specify a **positive number** of days!").setEphemeral(true).queue();
			return;
		}
		if (currencyCooldowns.containsKey(event.getMember().getId())) {
			long endTime = currencyCooldowns.get(event.getMember().getId());
			long difference = endTime - (System.currentTimeMillis()/1000);
			if (difference > 0) {
				String displayName = "seconds";
				if (difference == 1) {
					displayName = "second";
				}
				event.reply(String.format("Please wait **%d %s** before using this command again", difference, displayName)).setEphemeral(true).queue();
				return;
			} else {
				currencyCooldowns.remove(event.getMember().getId());
			}
		}
		currencyCooldowns.put(event.getMember().getId(), (System.currentTimeMillis()/1000)+days);
		event.deferReply().queue();
		InteractionHook hook = event.getHook();
		try {
			URL url = new URL(String.format("https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/latest/currencies/%s/usd.json", currency));
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String inputLine;
			StringBuffer content = new StringBuffer();
			while ((inputLine = input.readLine()) != null) {
				content.append(inputLine);
			}
			input.close();
			connection.disconnect();

			List<Double> stocks = new ArrayList<Double>();
			JSONObject object = new JSONObject(content.toString());
			stocks.add(object.getDouble("usd"));
			String currencyDate = object.getString("date");
			SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd");
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(parser.parse(currencyDate));
			for (int i = 0; i < days; i++) {
			    calendar.add(Calendar.DATE, -1);
				String date = parser.format(calendar.getTime());
				url = new URL(String.format("https://cdn.jsdelivr.net/gh/fawazahmed0/currency-api@1/%s/currencies/%s/usd.json", date, currency));
				connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");
				input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				content = new StringBuffer();
				while ((inputLine = input.readLine()) != null) {
					content.append(inputLine);
				}
				input.close();
				connection.disconnect();
				object = new JSONObject(content.toString());
				stocks.add(object.getDouble("usd"));
			}

			Plot plot = Plot.create();
			plot.plot().add(stocks).linewidth(2);
			plot.xlim(days-1, 0);
			plot.title(String.format("%s Stocks", currency.toUpperCase()));
			plot.ylabel("USD");
			plot.xlabel("Days ago");
			plot.savefig("plot.png").dpi(200);
			plot.executeSilently();

			File plotFile = new File("plot.png");
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(embedColor);
			embed.setImage("attachment://plot.png");
			MessageBuilder newMessage = new MessageBuilder();
			newMessage.setEmbed(embed.build());
			hook.sendMessage(newMessage.build()).addFile(plotFile, "plot.png").queue();
		} catch (Exception error) {
			String errorMessage = error.getMessage();
			if (errorMessage.contains("Server returned HTTP response code: 403")) {
				errorMessage = "currency not found";
			}
			hook.sendMessage(String.format("Unable to fetch currency information: `%s`", errorMessage)).queue();
		}
	}

	public void serverStatisticsCommand(SlashCommandEvent event) {
		int guildMembers = 0;
		for (int i = 0; i < members.size(); i++) {
			if (members.get(i).startsWith(event.getGuild().getId())) {
				guildMembers++;
			}
		}
		int guildChannels = 0;
		for (int i = 0; i < channels.size(); i++) {
			if (channels.get(i).startsWith(event.getGuild().getId())) {
				guildChannels++;
			}
		}
		List<Integer> differences = new ArrayList<Integer>();
		int counter = 0;
		double currentTime = 0;
		for (int i = 0; i < messages.size(); i++) {
			if (messages.get(i).startsWith(event.getGuild().getId())) {
				double sentTime = Double.parseDouble(messages.get(i).split("\\.")[1])/1000;
				if (sentTime - currentTime > 1) {
					differences.add(counter);
					counter = 0;
					currentTime = sentTime;
				} else {
					counter++;
				}
			}
		}
		int sum = 0;
		for (int difference: differences) {
			sum += difference;
		}
		double messageRate = 0;
		if (differences.size() > 0) {
			messageRate = (double) sum / differences.size();
		}

		EmbedBuilder embed = new EmbedBuilder();
		embed.setThumbnail(event.getGuild().getIconUrl());
		embed.addField("Total Members", String.valueOf(event.getGuild().getMemberCount()), true);
		embed.addField("Total Channels", String.valueOf(event.getGuild().getChannels().size()), true);
		embed.addField("Total Roles", String.valueOf(event.getGuild().getRoles().size()), true);
		embed.addField("Message Rate", String.format("%.2f/s", messageRate), true);
		embed.addField("Active Members", String.valueOf(guildMembers), true);
		embed.addField("Active Channels", String.valueOf(guildChannels), true);
		embed.setColor(embedColor);
		event.replyEmbeds(embed.build()).queue();
	}

	public void fetchAstronautsCommand(SlashCommandEvent event) {
		event.deferReply().queue();
		InteractionHook hook = event.getHook();
		try {
			URL url = new URL("http://api.open-notify.org/astros.json");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String inputLine;
			String content = "";
			while ((inputLine = reader.readLine()) != null) {
				content += inputLine;
			}
			reader.close();
			connection.disconnect();

			JSONObject object = new JSONObject(content);
			JSONArray people = object.getJSONArray("people");
			List<String> stations = new ArrayList<String>();
			for (int i = 0; i < people.length(); i++) {
				JSONObject peopleObject = new JSONObject(people.get(i).toString());
				String stationName = peopleObject.getString("craft");
				if (!stations.contains(stationName)) {
					stations.add(stationName);
				}
			}
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(embedColor);
			int astronauts = object.getInt("number");
			if (astronauts == 1) {
				embed.setFooter("There is currently 1 person in space");
			} else {
				embed.setFooter(String.format("There are currently %s people in space", astronauts));
			}
			for (String station: stations) {
				String value = "";
				for (int i = 0; i < people.length(); i++) {
					JSONObject peopleObject = new JSONObject(people.get(i).toString());
					if (peopleObject.getString("craft").equals(station)) {
						value += "\n" + peopleObject.getString("name");
					}
				}
				embed.addField(station, value, true);
			}
			hook.sendMessageEmbeds(embed.build()).queue();
		} catch (Exception error) {
			String errorMessage = error.getMessage();
			hook.sendMessage(String.format("Unable to fetch astronauts: `%s`", errorMessage)).queue();
		}
	}
}
