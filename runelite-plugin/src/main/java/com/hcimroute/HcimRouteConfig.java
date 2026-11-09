package com.hcimroute;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(HcimRoutePlugin.CONFIG_GROUP)
public interface HcimRouteConfig extends Config
{
	@ConfigSection(
		name = "Overlay",
		description = "De overlay met je volgende stap(pen) in het spel",
		position = 0
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Automatisch afvinken",
		description = "Stappen afvinken op basis van je quests en skills",
		position = 1
	)
	String autoSection = "auto";

	@ConfigItem(
		keyName = "showOverlay",
		name = "Toon overlay",
		description = "Toont je volgende stap(pen) linksboven in het spel",
		position = 0,
		section = overlaySection
	)
	default boolean showOverlay()
	{
		return true;
	}

	@Range(min = 1, max = 5)
	@ConfigItem(
		keyName = "overlaySteps",
		name = "Aantal stappen",
		description = "Hoeveel komende stappen de overlay toont",
		position = 1,
		section = overlaySection
	)
	default int overlaySteps()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "overlayTargets",
		name = "Toon skill-doelen",
		description = "Toont een voortgangsbalk bij stappen als 'tot 62 Farming' of 'tot 31.512 Thieving XP'",
		position = 2,
		section = overlaySection
	)
	default boolean overlayTargets()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlaySailing",
		name = "Toon volgende Sailing-stap",
		description = "Fase 2 (Sailing) loopt naast de hoofdroute; toon er ook de volgende stap van",
		position = 3,
		section = overlaySection
	)
	default boolean overlaySailing()
	{
		return false;
	}

	@Range(min = 150, max = 400)
	@ConfigItem(
		keyName = "overlayWidth",
		name = "Breedte",
		description = "Breedte van de overlay in pixels",
		position = 4,
		section = overlaySection
	)
	default int overlayWidth()
	{
		return 230;
	}

	@ConfigItem(
		keyName = "autoQuests",
		name = "Bij voltooide quest",
		description = "Vinkt 'Voltooi X'-stappen af zodra de quest in je questlijst klaar is",
		position = 0,
		section = autoSection
	)
	default boolean autoCompleteQuests()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoTargets",
		name = "Bij bereikt skill-doel",
		description = "Vinkt stappen als 'tot 62 Farming' af zodra je dat level of die XP hebt",
		position = 1,
		section = autoSection
	)
	default boolean autoCompleteTargets()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatMessages",
		name = "Chatmelding",
		description = "Meldt in de chat wanneer een stap automatisch is afgevinkt",
		position = 2,
		section = autoSection
	)
	default boolean chatMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideCompleted",
		name = "Verberg afgevinkte stappen",
		description = "Toont in het zijpaneel alleen stappen die je nog moet doen",
		position = 10
	)
	default boolean hideCompleted()
	{
		return false;
	}

	@ConfigItem(
		keyName = "completeHotkey",
		name = "Hotkey: stap afvinken",
		description = "Vinkt je huidige stap af zonder het zijpaneel te openen",
		position = 11
	)
	default Keybind completeHotkey()
	{
		return Keybind.NOT_SET;
	}
}
