package com.hcimroute;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "HCIM Macroquest Route",
	description = "Volg de HCIM Macroquest Guide 2026 stap voor stap, met voortgang per account",
	tags = {"hcim", "ironman", "guide", "route", "quest", "macroquest"}
)
public class HcimRoutePlugin extends Plugin
{
	static final String CONFIG_GROUP = "hcimroute";
	static final String KEY_DONE = "doneSteps";

	private static final Logger log = LoggerFactory.getLogger(HcimRoutePlugin.class);
	/** Elke ~15 seconden de quests nakijken (25 ticks van 0,6 s). */
	private static final int QUEST_CHECK_INTERVAL_TICKS = 25;
	private static final int UPCOMING_LIMIT = 5;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private HcimRouteConfig config;

	@Inject
	private Gson gson;

	@Inject
	private RouteOverlay overlay;

	private RouteGuide guide;
	private final ProgressTracker progress = new ProgressTracker();
	private final Map<String, Quest> questsByName = new HashMap<>();

	private RoutePanel panel;
	private NavigationButton navButton;

	/** True zodra de voortgang van het ingelogde account geladen is. */
	private volatile boolean profileLoaded;
	private volatile boolean questCheckPending;
	private int tickCounter;
	private volatile List<Route.Step> upcoming = Collections.emptyList();
	private volatile Route.Step nextParallel;

	private final HotkeyListener completeHotkey = new HotkeyListener(() -> config.completeHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			completeCurrentStep();
		}
	};

	@Override
	protected void startUp() throws Exception
	{
		guide = RouteGuide.load(gson, HcimRoutePlugin.class.getResourceAsStream("route.json"));
		questsByName.clear();
		for (Quest quest : Quest.values())
		{
			questsByName.put(quest.getName(), quest);
		}

		panel = new RoutePanel(this, guide, config);
		BufferedImage icon = ImageUtil.loadImageResource(HcimRoutePlugin.class, "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("HCIM Macroquest Route")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		keyManager.registerKeyListener(completeHotkey);

		loadProgress();
		log.debug("HCIM route geladen: {} stappen", guide.getAllTrackableSteps().size());
	}

	@Override
	protected void shutDown() throws Exception
	{
		keyManager.unregisterKeyListener(completeHotkey);
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		guide = null;
		profileLoaded = false;
		progress.clear();
		upcoming = Collections.emptyList();
		nextParallel = null;
	}

	@Provides
	HcimRouteConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HcimRouteConfig.class);
	}

	// ------------------------------------------------------------------ events

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadProgress();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			questCheckPending = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!profileLoaded || !config.autoCompleteQuests())
		{
			return;
		}
		if (questCheckPending || ++tickCounter >= QUEST_CHECK_INTERVAL_TICKS)
		{
			questCheckPending = false;
			tickCounter = 0;
			checkQuests();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (profileLoaded && config.autoCompleteTargets())
		{
			checkTargets();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()) || KEY_DONE.equals(event.getKey()))
		{
			return;
		}
		if ("hideCompleted".equals(event.getKey()))
		{
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.rebuild();
				}
			});
		}
		if ("autoTargets".equals(event.getKey()) && config.autoCompleteTargets())
		{
			clientThread.invokeLater(this::checkTargets);
		}
	}

	// ------------------------------------------------------------------ voortgang

	private void loadProgress()
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			profileLoaded = false;
			progress.clear();
		}
		else
		{
			progress.load(configManager.getRSProfileConfiguration(CONFIG_GROUP, KEY_DONE));
			profileLoaded = true;
			questCheckPending = true;
		}
		onProgressChanged(true);
	}

	private void saveProgress()
	{
		if (profileLoaded)
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY_DONE, progress.serialize());
		}
	}

	private void onProgressChanged(boolean jumpToCurrent)
	{
		RouteGuide g = guide;
		if (g == null)
		{
			return;
		}
		upcoming = g.upcoming(progress::isDone, UPCOMING_LIMIT);
		nextParallel = g.nextParallel(progress::isDone);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.onProgressChanged(jumpToCurrent);
			}
		});
	}

	public boolean isDone(Route.Step step)
	{
		return progress.isDone(step.getId());
	}

	public boolean isStepDone(String stepId)
	{
		return progress.isDone(stepId);
	}

	public boolean isProfileLoaded()
	{
		return profileLoaded;
	}

	public void setDone(Route.Step step, boolean done)
	{
		if (progress.set(step.getId(), done))
		{
			saveProgress();
			onProgressChanged(false);
		}
	}

	/** Vinkt alle stappen tot en met deze af (voor een account dat al onderweg is). */
	public void markUpTo(Route.Step step)
	{
		List<String> ids = new ArrayList<>();
		for (Route.Step s : guide.stepsUpTo(step))
		{
			ids.add(s.getId());
		}
		if (progress.setAll(ids, true) > 0)
		{
			saveProgress();
			onProgressChanged(false);
		}
	}

	public void resetSection(Route.Section section)
	{
		List<String> ids = new ArrayList<>();
		for (Route.Step s : section.getSteps())
		{
			ids.add(s.getId());
		}
		if (progress.setAll(ids, false) > 0)
		{
			saveProgress();
			onProgressChanged(false);
		}
	}

	public void resetAll()
	{
		progress.clear();
		saveProgress();
		onProgressChanged(true);
	}

	public void completeCurrentStep()
	{
		List<Route.Step> next = upcoming;
		if (!next.isEmpty())
		{
			setDone(next.get(0), true);
		}
	}

	public List<Route.Step> getUpcoming()
	{
		return upcoming;
	}

	public Route.Step getNextParallel()
	{
		return nextParallel;
	}

	public RouteGuide getGuide()
	{
		return guide;
	}

	public void openUrl(String url)
	{
		LinkBrowser.browse(url);
	}

	// ------------------------------------------------------------------ automatisch afvinken

	/** Draait op de client thread: Quest.getState voert een client-script uit. */
	private void checkQuests()
	{
		RouteGuide g = guide;
		if (g == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Map<String, QuestState> cache = new HashMap<>();
		List<Route.Step> ticked = new ArrayList<>();
		for (Route.Step step : g.getAllTrackableSteps())
		{
			if (step.getComplete().isEmpty() || progress.isDone(step.getId()))
			{
				continue;
			}
			boolean allFinished = true;
			for (String name : step.getComplete())
			{
				Quest quest = questsByName.get(name);
				if (quest == null)
				{
					// onbekend in deze RuneLite-versie: niet automatisch afvinken
					allFinished = false;
					break;
				}
				QuestState state = cache.computeIfAbsent(name, n -> quest.getState(client));
				if (state != QuestState.FINISHED)
				{
					allFinished = false;
					break;
				}
			}
			if (allFinished && progress.set(step.getId(), true))
			{
				ticked.add(step);
			}
		}
		afterAutoComplete(ticked, "quest voltooid");
	}

	/** Draait op de client thread. */
	private void checkTargets()
	{
		RouteGuide g = guide;
		if (g == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		List<Route.Step> ticked = new ArrayList<>();
		for (Route.Step step : g.getAllTrackableSteps())
		{
			// stappen met een quest wachten op die quest
			if (step.getTargets().isEmpty() || !step.getComplete().isEmpty() || progress.isDone(step.getId()))
			{
				continue;
			}
			boolean allMet = true;
			for (Route.SkillTarget target : step.getTargets())
			{
				if (!isTargetMet(target))
				{
					allMet = false;
					break;
				}
			}
			if (allMet && progress.set(step.getId(), true))
			{
				ticked.add(step);
			}
		}
		afterAutoComplete(ticked, "doel bereikt");
	}

	private void afterAutoComplete(List<Route.Step> ticked, String reason)
	{
		if (ticked.isEmpty())
		{
			return;
		}
		saveProgress();
		onProgressChanged(false);
		if (!config.chatMessages())
		{
			return;
		}
		String msg;
		if (ticked.size() <= 3)
		{
			Route.Step s = ticked.get(0);
			msg = "HCIM route (" + reason + "): " + s.getSection().getTitle() + " - "
				+ RouteText.firstSentence(s.getText(), 70)
				+ (ticked.size() > 1 ? " (+" + (ticked.size() - 1) + ")" : "");
		}
		else
		{
			msg = "HCIM route: " + ticked.size() + " stappen automatisch afgevinkt (" + reason + ").";
		}
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff9040>" + msg + "</col>", null);
	}

	/** Alleen aanroepen op de client thread. */
	boolean isTargetMet(Route.SkillTarget target)
	{
		Skill skill = skillOf(target);
		if (skill == null)
		{
			return false;
		}
		if (target.getXp() > 0)
		{
			return client.getSkillExperience(skill) >= target.getXp();
		}
		return client.getRealSkillLevel(skill) >= target.getLevel();
	}

	/** Alleen aanroepen op de client thread (of tijdens het renderen van een overlay). */
	int currentXp(Route.SkillTarget target)
	{
		Skill skill = skillOf(target);
		return skill == null ? 0 : client.getSkillExperience(skill);
	}

	static Skill skillOf(Route.SkillTarget target)
	{
		try
		{
			return Skill.valueOf(target.getSkill());
		}
		catch (IllegalArgumentException | NullPointerException e)
		{
			return null;
		}
	}
}
