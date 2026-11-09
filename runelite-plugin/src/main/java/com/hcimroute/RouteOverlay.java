package com.hcimroute;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.Experience;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** Linksboven in het spel: de huidige bank-sectie en je volgende stap(pen). */
public class RouteOverlay extends OverlayPanel
{
	private static final Color TITLE_COLOR = new Color(255, 152, 31);
	private static final Color NEXT_COLOR = new Color(170, 170, 170);
	private static final Color SAILING_COLOR = new Color(110, 190, 255);
	private static final NumberFormat XP_FORMAT = NumberFormat.getIntegerInstance(new Locale("nl", "BE"));

	private final HcimRoutePlugin plugin;
	private final HcimRouteConfig config;

	@Inject
	RouteOverlay(HcimRoutePlugin plugin, HcimRouteConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay() || plugin.getGuide() == null || !plugin.isProfileLoaded())
		{
			return null;
		}

		panelComponent.setPreferredSize(new Dimension(config.overlayWidth(), 0));
		List<Route.Step> steps = plugin.getUpcoming();

		if (steps.isEmpty())
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("HCIM route voltooid!")
				.color(TITLE_COLOR)
				.build());
		}
		else
		{
			Route.Step first = steps.get(0);
			Route.Section section = first.getSection();
			panelComponent.getChildren().add(TitleComponent.builder()
				.text(RouteText.toPlain(section.getDisplayTitle(), 60))
				.color(TITLE_COLOR)
				.build());

			int n = Math.min(config.overlaySteps(), steps.size());
			for (int i = 0; i < n; i++)
			{
				Route.Step step = steps.get(i);
				String prefix = step.getSection() != section ? "[" + step.getSection().getTitle() + "] " : "";
				panelComponent.getChildren().add(LineComponent.builder()
					.left((i == 0 ? "> " : "- ") + prefix + RouteText.toPlain(step.getText(), i == 0 ? 220 : 110))
					.leftColor(i == 0 ? Color.WHITE : NEXT_COLOR)
					.build());

				if (i == 0 && config.overlayTargets())
				{
					addTargetBars(step);
				}
			}
		}

		if (config.overlaySailing())
		{
			Route.Step sailing = plugin.getNextParallel();
			if (sailing != null)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Sailing " + sailing.getSection().getTitle() + ": " + RouteText.firstSentence(sailing.getText(), 90))
					.leftColor(SAILING_COLOR)
					.build());
			}
		}

		return super.render(graphics);
	}

	private void addTargetBars(Route.Step step)
	{
		for (Route.SkillTarget target : step.getTargets())
		{
			if (HcimRoutePlugin.skillOf(target) == null)
			{
				continue;
			}
			int goalXp = target.getXp() > 0 ? target.getXp() : Experience.getXpForLevel(target.getLevel());
			int current = plugin.currentXp(target);
			String skillName = capitalize(target.getSkill());
			String label;
			if (current >= goalXp)
			{
				label = skillName + " - doel bereikt";
			}
			else if (target.getXp() > 0)
			{
				label = skillName + " " + XP_FORMAT.format(current) + " / " + XP_FORMAT.format(target.getXp()) + " XP";
			}
			else
			{
				label = skillName + " " + Experience.getLevelForXp(current) + " / " + target.getLevel()
					+ " (" + XP_FORMAT.format(goalXp - current) + " XP)";
			}

			ProgressBarComponent bar = new ProgressBarComponent();
			bar.setMinimum(0);
			bar.setMaximum(Math.max(goalXp, 1));
			bar.setValue(Math.min(current, goalXp));
			bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
			bar.setCenterLabel(label);
			panelComponent.getChildren().add(bar);
		}
	}

	private static String capitalize(String skillConstant)
	{
		String s = skillConstant.toLowerCase(Locale.ROOT);
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
