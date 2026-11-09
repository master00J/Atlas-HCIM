package com.hcimroute;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Het zijpaneel: bovenaan je voortgang en volgende stap, daaronder per hoofdstuk
 * alle banks met hun stappen. Alle methodes draaien op de Swing-thread.
 */
class RoutePanel extends PluginPanel
{
	// breedtes in schermpixels; het zijpaneel is altijd 242 px breed
	private static final int STEP_TEXT_PX = 166;
	private static final int NOTE_PX = 192;
	private static final int HEADER_TEXT_PX = 146;
	private static final int TABLE_PX = 182;
	private static final int CARD_PX = 196;
	private static final Color CURRENT_BG = new Color(52, 46, 38);
	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	private static final Color DONE_TEXT = new Color(120, 120, 120);
	private static final Color NOTE_TEXT = new Color(170, 170, 170);
	private static final Border ROW_BORDER = BorderFactory.createEmptyBorder(3, 4, 3, 4);
	private static final Border CURRENT_BORDER = BorderFactory.createCompoundBorder(
		BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT),
		BorderFactory.createEmptyBorder(3, 1, 3, 4));

	private final HcimRoutePlugin plugin;
	private final RouteGuide guide;
	private final HcimRouteConfig config;

	private final JLabel progressLabel = new JLabel();
	private final JProgressBar progressBar = new JProgressBar(0, 100);
	private final JLabel loginHint = new JLabel();
	private final JLabel nextTitle = new JLabel();
	private final JLabel nextText = new JLabel();
	private final JButton doneButton = new JButton("Klaar");
	private final JButton showButton = new JButton("Toon");
	private final JComboBox<Route.Chapter> chapterBox = new JComboBox<>();
	private final JTextField searchField = new JTextField();
	private final JPanel content = new JPanel();
	private final JScrollPane scrollPane;

	private final Map<String, StepRow> rows = new HashMap<>();
	private final Map<String, SectionHeader> headers = new HashMap<>();
	private final Set<String> collapsed = new HashSet<>();
	private boolean jumpedAfterLogin;
	private Route.Step pendingScroll;

	RoutePanel(HcimRoutePlugin plugin, RouteGuide guide, HcimRouteConfig config)
	{
		super(false);
		this.plugin = plugin;
		this.guide = guide;
		this.config = config;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (Route.Chapter chapter : guide.getChapters())
		{
			chapterBox.addItem(chapter);
			for (Route.Section section : chapter.getSections())
			{
				// uitleg- en efficiëntie-secties in de fases starten dichtgeklapt
				if (chapter.isTrackable() && section.isInfo())
				{
					collapsed.add(section.getId());
				}
			}
		}

		add(buildHeader(), BorderLayout.NORTH);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(BorderFactory.createEmptyBorder(4, 6, 8, 6));
		ScrollableWrapper wrapper = new ScrollableWrapper();
		wrapper.add(content, BorderLayout.NORTH);
		scrollPane = new JScrollPane(wrapper);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		add(scrollPane, BorderLayout.CENTER);

		rebuild();
		onProgressChanged(false);
	}

	// ------------------------------------------------------------------ header

	private JComponent buildHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(8, 6, 6, 6));

		JLabel title = new JLabel(RouteText.sanitize(guide.getTitle()));
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(left(title));

		progressLabel.setFont(FontManager.getRunescapeSmallFont());
		progressLabel.setForeground(NOTE_TEXT);
		header.add(left(progressLabel));

		progressBar.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		progressBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progressBar.setBorderPainted(false);
		progressBar.setPreferredSize(new Dimension(CARD_PX, 6));
		progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
		progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(progressBar);

		loginHint.setFont(FontManager.getRunescapeSmallFont());
		loginHint.setForeground(ACCENT);
		loginHint.setText(RouteText.wrap("Log in om af te vinken: je voortgang wordt per account geladen en opgeslagen.", CARD_PX));
		header.add(left(loginHint));

		// kaart met de volgende stap
		JPanel card = new JPanel(new BorderLayout(0, 4));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		nextTitle.setFont(FontManager.getRunescapeBoldFont());
		nextTitle.setForeground(ACCENT);
		card.add(nextTitle, BorderLayout.NORTH);

		nextText.setFont(FontManager.getRunescapeSmallFont());
		nextText.setForeground(Color.WHITE);
		card.add(nextText, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setOpaque(false);
		doneButton.setToolTipText("Vink je huidige stap af");
		doneButton.addActionListener(e -> plugin.completeCurrentStep());
		showButton.setToolTipText("Toon de huidige stap in de lijst");
		showButton.addActionListener(e -> jumpToCurrent());
		buttons.add(doneButton);
		buttons.add(showButton);
		card.add(buttons, BorderLayout.SOUTH);

		header.add(spacer(6));
		header.add(card);
		header.add(spacer(6));

		chapterBox.setRenderer(new ChapterRenderer());
		chapterBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		chapterBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		chapterBox.addActionListener(e ->
		{
			if (searchField.getText().isEmpty())
			{
				rebuild();
				scrollPane.getVerticalScrollBar().setValue(0);
			}
		});

		JPanel controls = new JPanel(new BorderLayout(4, 0));
		controls.setOpaque(false);
		controls.setAlignmentX(Component.LEFT_ALIGNMENT);
		controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		controls.add(chapterBox, BorderLayout.CENTER);
		JButton more = new JButton("Meer");
		more.setToolTipText("Meer opties");
		more.addActionListener(e -> showMoreMenu(more));
		controls.add(more, BorderLayout.EAST);
		header.add(controls);
		header.add(spacer(4));

		searchField.setToolTipText("Zoek in de hele route, bv. 'limpwurt' of 'Bank 104'");
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setForeground(Color.WHITE);
		searchField.setCaretColor(Color.WHITE);
		searchField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(3, 4, 3, 4)));
		searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				rebuild();
			}
		});
		JLabel searchLabel = new JLabel("Zoeken");
		searchLabel.setFont(FontManager.getRunescapeSmallFont());
		searchLabel.setForeground(NOTE_TEXT);
		header.add(left(searchLabel));
		header.add(searchField);
		return header;
	}

	private void showMoreMenu(JComponent anchor)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem current = new JMenuItem("Ga naar huidige stap");
		current.addActionListener(e -> jumpToCurrent());
		menu.add(current);
		JMenuItem expand = new JMenuItem("Alle secties openklappen");
		expand.addActionListener(e ->
		{
			collapsed.clear();
			rebuild();
		});
		menu.add(expand);
		menu.addSeparator();
		JMenuItem reset = new JMenuItem("Alle voortgang wissen...");
		reset.addActionListener(e ->
		{
			int answer = JOptionPane.showConfirmDialog(this,
				"Alle afgevinkte stappen van dit account wissen?\nDit kan je niet ongedaan maken.",
				"HCIM route", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer == JOptionPane.OK_OPTION)
			{
				plugin.resetAll();
			}
		});
		reset.setEnabled(plugin.isProfileLoaded());
		menu.add(reset);
		menu.show(anchor, 0, anchor.getHeight());
	}

	// ------------------------------------------------------------------ status

	void onProgressChanged(boolean jumpToCurrent)
	{
		updateHeader();
		if (config.hideCompleted())
		{
			rebuild();
		}
		else
		{
			updateRows();
		}
		chapterBox.repaint();

		boolean loaded = plugin.isProfileLoaded();
		if (jumpToCurrent || (loaded && !jumpedAfterLogin))
		{
			jumpedAfterLogin = loaded;
			jumpToCurrent();
		}
		if (!loaded)
		{
			jumpedAfterLogin = false;
		}
	}

	private void updateHeader()
	{
		List<Route.Step> all = guide.getMainSteps();
		int done = RouteGuide.countDone(all, plugin::isStepDone);
		int pct = all.isEmpty() ? 0 : (int) Math.round(100.0 * done / all.size());
		progressLabel.setText(done + " / " + all.size() + " stappen (" + pct + "%)");
		progressBar.setValue(pct);
		loginHint.setVisible(!plugin.isProfileLoaded());

		List<Route.Step> upcoming = plugin.getUpcoming();
		if (upcoming.isEmpty())
		{
			nextTitle.setText("Route voltooid");
			nextText.setText("");
			doneButton.setEnabled(false);
			showButton.setEnabled(false);
		}
		else
		{
			Route.Step step = upcoming.get(0);
			nextTitle.setText(RouteText.toPlain(step.getSection().getDisplayTitle(), 40));
			String body = RouteText.toHtmlFragment(step.getText());
			if (plugin.isQuestReady(step))
			{
				body = "<font color='" + RouteText.READY_COLOR + "'><b>Quest klaar.</b> Vink af zodra de rest ook gedaan is.</font><br>" + body;
			}
			nextText.setText(RouteText.wrap(body, CARD_PX));
			doneButton.setEnabled(plugin.isProfileLoaded());
			showButton.setEnabled(true);
		}
	}

	private void updateRows()
	{
		Route.Step current = currentStep();
		boolean editable = plugin.isProfileLoaded();
		for (StepRow row : rows.values())
		{
			refreshRow(row, current, editable);
		}
		for (SectionHeader header : headers.values())
		{
			header.updateCount();
		}
	}

	private void refreshRow(StepRow row)
	{
		refreshRow(row, currentStep(), plugin.isProfileLoaded());
	}

	private void refreshRow(StepRow row, Route.Step current, boolean editable)
	{
		row.update(plugin.isDone(row.step), row.step == current, plugin.isQuestReady(row.step), editable);
	}

	private Route.Step currentStep()
	{
		List<Route.Step> upcoming = plugin.getUpcoming();
		return upcoming.isEmpty() ? null : upcoming.get(0);
	}

	/** Selecteert het hoofdstuk van de huidige stap, klapt de sectie open en scrollt ernaartoe. */
	void jumpToCurrent()
	{
		Route.Step step = currentStep();
		if (step == null)
		{
			return;
		}
		if (!searchField.getText().isEmpty())
		{
			searchField.setText("");
		}
		collapsed.remove(step.getSection().getId());
		if (chapterBox.getSelectedItem() != step.getSection().getChapter())
		{
			chapterBox.setSelectedItem(step.getSection().getChapter());
		}
		else
		{
			rebuild();
		}
		scrollTo(step);
	}

	@Override
	public void onActivate()
	{
		// het paneel was dicht toen we wilden scrollen: nu het zichtbaar is, opnieuw proberen
		if (pendingScroll != null)
		{
			scrollTo(pendingScroll);
		}
	}

	private void scrollTo(Route.Step step)
	{
		pendingScroll = step;
		SwingUtilities.invokeLater(() ->
		{
			StepRow row = rows.get(step.getId());
			if (row == null || row.getParent() == null)
			{
				pendingScroll = null;
				return;
			}
			if (row.getHeight() == 0 || scrollPane.getViewport().getHeight() == 0)
			{
				// nog niet uitgelegd (paneel dicht); onActivate probeert het opnieuw
				return;
			}
			pendingScroll = null;
			Rectangle r = SwingUtilities.convertRectangle(row.getParent(), row.getBounds(), content);
			int viewHeight = scrollPane.getViewport().getHeight();
			content.scrollRectToVisible(new Rectangle(0, Math.max(0, r.y - 70), 1, Math.max(viewHeight, r.height)));
		});
	}

	// ------------------------------------------------------------------ inhoud

	void rebuild()
	{
		content.removeAll();
		rows.clear();
		headers.clear();

		String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
		if (query.isEmpty())
		{
			Route.Chapter chapter = (Route.Chapter) chapterBox.getSelectedItem();
			if (chapter != null)
			{
				buildChapter(chapter);
			}
		}
		else
		{
			buildSearch(query);
		}

		content.revalidate();
		content.repaint();
		updateRows();
	}

	private void buildChapter(Route.Chapter chapter)
	{
		for (String intro : chapter.getIntro())
		{
			content.add(textLabel(intro, NOTE_TEXT, NOTE_PX));
			content.add(spacer(6));
		}
		String group = null;
		for (Route.Section section : chapter.getSections())
		{
			if (section.getGroup() != null && !section.getGroup().equals(group))
			{
				group = section.getGroup();
				JLabel g = new JLabel(RouteText.toPlain(group, 40));
				g.setFont(FontManager.getRunescapeBoldFont());
				g.setForeground(ACCENT);
				g.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
				content.add(left(g));
			}
			addSection(section, null, section.getSteps(), !collapsed.contains(section.getId()));
		}
	}

	private void buildSearch(String query)
	{
		int hits = 0;
		for (Route.Chapter chapter : guide.getChapters())
		{
			for (Route.Section section : chapter.getSections())
			{
				boolean sectionMatch = section.getDisplayTitle().toLowerCase(Locale.ROOT).contains(query);
				List<Route.Step> matches = new ArrayList<>();
				for (Route.Step step : section.getSteps())
				{
					if (sectionMatch || step.getText().toLowerCase(Locale.ROOT).contains(query))
					{
						matches.add(step);
					}
				}
				if (!matches.isEmpty())
				{
					hits += matches.size();
					addSection(section, chapter.getShortTitle(), matches, true);
				}
			}
		}
		if (hits == 0)
		{
			content.add(textLabel("Niets gevonden voor '" + query + "'.", NOTE_TEXT, NOTE_PX));
		}
	}

	private void addSection(Route.Section section, String chapterPrefix, List<Route.Step> steps, boolean open)
	{
		SectionHeader header = new SectionHeader(section, chapterPrefix, open);
		headers.put(section.getId(), header);
		content.add(spacer(4));
		content.add(header);
		if (!open)
		{
			return;
		}

		boolean search = chapterPrefix != null;
		if (!search)
		{
			for (String note : section.getNotes())
			{
				content.add(textLabel(note, NOTE_TEXT, NOTE_PX));
			}
			for (Route.Table table : section.getTables())
			{
				for (List<String> tableRow : table.getRows())
				{
					content.add(tableCard(table.getHeader(), tableRow));
				}
			}
		}

		boolean trackable = section.getChapter().isTrackable() && !section.isInfo();
		boolean hideDone = config.hideCompleted() && !search;
		for (Route.Step step : steps)
		{
			if (hideDone && plugin.isDone(step))
			{
				continue;
			}
			StepRow row = new StepRow(step, trackable);
			rows.put(step.getId(), row);
			content.add(row);
		}

		if (!search)
		{
			for (String note : section.getFooter())
			{
				content.add(textLabel(note, NOTE_TEXT, NOTE_PX));
			}
		}
	}

	// ------------------------------------------------------------------ bouwstenen

	private static JComponent left(JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		return c;
	}

	private static JComponent spacer(int height)
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(1, height));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	private static JLabel textLabel(String text, Color color, int width)
	{
		JLabel l = new JLabel(RouteText.toHtml(text, width));
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JComponent tableCard(List<String> header, List<String> row)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("<b>").append(RouteText.toHtmlFragment(row.isEmpty() ? "" : row.get(0))).append("</b>");
		for (int i = 1; i < row.size(); i++)
		{
			String cell = row.get(i);
			if (cell == null || cell.isEmpty() || "–".equals(cell))
			{
				continue;
			}
			String head = i < header.size() ? header.get(i) : "";
			sb.append("<br><font color='").append(RouteText.TAG_COLOR).append("'>")
				.append(RouteText.escape(head)).append(":</font> ")
				.append(RouteText.toHtmlFragment(cell));
		}
		JLabel l = new JLabel(RouteText.wrap(sb.toString(), TABLE_PX));
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(Color.WHITE);
		l.setOpaque(true);
		l.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		l.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(l, BorderLayout.CENTER);
		return wrap;
	}

	private void addWikiItems(JPopupMenu menu, Route.Step step)
	{
		Set<String> quests = new LinkedHashSet<>(step.getComplete());
		for (String q : quests)
		{
			JMenuItem item = new JMenuItem("Wiki: " + q);
			item.addActionListener(e -> plugin.openUrl(RouteText.wikiUrl(q)));
			menu.add(item);
		}
		for (String tag : step.getTags())
		{
			if (quests.contains(tag))
			{
				continue;
			}
			JMenuItem item = new JMenuItem("Wiki: " + tag);
			item.addActionListener(e -> plugin.openUrl(RouteText.wikiSearchUrl(tag)));
			menu.add(item);
		}
		for (Route.Link link : step.getLinks())
		{
			JMenuItem item = new JMenuItem("Link: " + link.getTitle());
			item.addActionListener(e -> plugin.openUrl(link.getUrl()));
			menu.add(item);
		}
	}

	/** Een stap met een vinkje. Klik op de tekst vinkt ook af; rechtsklik opent het menu. */
	private class StepRow extends JPanel
	{
		final Route.Step step;
		private final JCheckBox checkBox = new JCheckBox();
		private final JLabel label = new JLabel();
		private final boolean trackable;

		StepRow(Route.Step step, boolean trackable)
		{
			super(new BorderLayout(4, 0));
			this.step = step;
			this.trackable = trackable;
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			setBorder(ROW_BORDER);
			setAlignmentX(Component.LEFT_ALIGNMENT);

			checkBox.setOpaque(false);
			checkBox.setVerticalAlignment(SwingConstants.TOP);
			checkBox.setEnabled(trackable && plugin.isProfileLoaded());
			checkBox.addActionListener(e ->
			{
				if (!plugin.setDone(step, checkBox.isSelected()))
				{
					// geweigerd (geen of ander profiel): vinkje terugzetten
					refreshRow(this);
				}
			});
			JPanel west = new JPanel(new BorderLayout());
			west.setOpaque(false);
			west.add(checkBox, BorderLayout.NORTH);
			add(west, BorderLayout.WEST);

			label.setFont(FontManager.getRunescapeSmallFont());
			label.setVerticalAlignment(SwingConstants.TOP);
			label.setText(html(false));
			label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			add(label, BorderLayout.CENTER);

			MouseAdapter mouse = new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (e.isPopupTrigger())
					{
						showMenu(e);
					}
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					if (e.isPopupTrigger())
					{
						showMenu(e);
					}
					else if (SwingUtilities.isLeftMouseButton(e) && e.getSource() == label && trackable
						&& plugin.isProfileLoaded())
					{
						plugin.setDone(step, !plugin.isDone(step));
					}
				}
			};
			label.addMouseListener(mouse);
			addMouseListener(mouse);
		}

		private boolean showsQuestReady;

		private String html(boolean questReady)
		{
			String body = RouteText.toHtmlFragment(step.getText());
			if (questReady)
			{
				body = "<font color='" + RouteText.READY_COLOR + "'><b>Quest klaar.</b> Vink af zodra de rest ook gedaan is.</font><br>" + body;
			}
			return RouteText.wrap(body, STEP_TEXT_PX);
		}

		void update(boolean done, boolean current, boolean questReady, boolean editable)
		{
			checkBox.setSelected(done);
			checkBox.setEnabled(trackable && editable);
			if (questReady != showsQuestReady)
			{
				showsQuestReady = questReady;
				label.setText(html(questReady));
			}
			label.setForeground(done ? DONE_TEXT : Color.WHITE);
			setBackground(current ? CURRENT_BG : ColorScheme.DARK_GRAY_COLOR);
			setBorder(current ? CURRENT_BORDER : ROW_BORDER);
		}

		private void showMenu(MouseEvent e)
		{
			JPopupMenu menu = new JPopupMenu();
			if (trackable && plugin.isProfileLoaded())
			{
				boolean done = plugin.isDone(step);
				JMenuItem toggle = new JMenuItem(done ? "Niet afgevinkt" : "Afvinken");
				toggle.addActionListener(a -> plugin.setDone(step, !done));
				menu.add(toggle);
				JMenuItem upTo = new JMenuItem("Alles tot hier afvinken");
				upTo.setToolTipText("Handig als je account al verder is dan je voortgang hier");
				upTo.addActionListener(a -> plugin.markUpTo(step));
				menu.add(upTo);
			}
			JPopupMenu wiki = new JPopupMenu();
			addWikiItems(wiki, step);
			if (wiki.getComponentCount() > 0)
			{
				if (menu.getComponentCount() > 0)
				{
					menu.addSeparator();
				}
				for (Component item : wiki.getComponents())
				{
					menu.add(item);
				}
			}
			if (menu.getComponentCount() > 0)
			{
				menu.show(e.getComponent(), e.getX(), e.getY());
			}
		}
	}

	/** Klikbare sectiekop met titel, meenemen-lijst en teller. */
	private class SectionHeader extends JPanel
	{
		private final Route.Section section;
		private final JLabel count = new JLabel();
		private final List<Route.Step> trackableSteps = new ArrayList<>();

		SectionHeader(Route.Section section, String chapterPrefix, boolean open)
		{
			super(new BorderLayout(4, 0));
			this.section = section;
			if (section.getChapter().isTrackable() && !section.isInfo())
			{
				trackableSteps.addAll(section.getSteps());
			}
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

			StringBuilder sb = new StringBuilder();
			if (chapterPrefix != null)
			{
				sb.append("<font color='").append(RouteText.TAG_COLOR).append("'>")
					.append(RouteText.escape(chapterPrefix)).append("</font><br>");
			}
			sb.append("<b>").append(open ? "- " : "+ ").append(RouteText.escape(section.getTitle())).append("</b>");
			if (section.getSubtitle() != null)
			{
				sb.append("<br>").append(RouteText.toHtmlFragment(section.getSubtitle()));
			}
			if (section.getWithdraw() != null)
			{
				sb.append("<br><font color='").append(RouteText.TAG_COLOR).append("'>Meenemen: ")
					.append(RouteText.escape(section.getWithdraw())).append("</font>");
			}
			JLabel title = new JLabel(RouteText.wrap(sb.toString(), HEADER_TEXT_PX));
			title.setFont(FontManager.getRunescapeSmallFont());
			title.setForeground(Color.WHITE);
			title.setVerticalAlignment(SwingConstants.TOP);
			add(title, BorderLayout.CENTER);

			count.setFont(FontManager.getRunescapeSmallFont());
			count.setVerticalAlignment(SwingConstants.TOP);
			add(count, BorderLayout.EAST);

			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (e.isPopupTrigger())
					{
						showMenu(e);
					}
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					if (e.isPopupTrigger())
					{
						showMenu(e);
					}
					else if (SwingUtilities.isLeftMouseButton(e))
					{
						if (!collapsed.remove(section.getId()))
						{
							collapsed.add(section.getId());
						}
						rebuild();
					}
				}
			});
		}

		void updateCount()
		{
			if (trackableSteps.isEmpty())
			{
				count.setText("");
				return;
			}
			int done = RouteGuide.countDone(trackableSteps, plugin::isStepDone);
			count.setText(done + "/" + trackableSteps.size());
			count.setForeground(done == trackableSteps.size() ? ColorScheme.PROGRESS_COMPLETE_COLOR : NOTE_TEXT);
		}

		private void showMenu(MouseEvent e)
		{
			if (trackableSteps.isEmpty() || !plugin.isProfileLoaded())
			{
				return;
			}
			JPopupMenu menu = new JPopupMenu();
			JMenuItem upTo = new JMenuItem("Alles tot en met deze sectie afvinken");
			upTo.addActionListener(a -> plugin.markUpTo(trackableSteps.get(trackableSteps.size() - 1)));
			menu.add(upTo);
			JMenuItem reset = new JMenuItem("Sectie niet afgevinkt zetten");
			reset.addActionListener(a -> plugin.resetSection(section));
			menu.add(reset);
			menu.show(e.getComponent(), e.getX(), e.getY());
		}
	}

	/** Toont het hoofdstuk met zijn voortgang in de keuzelijst. */
	private class ChapterRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
		{
			super.getListCellRendererComponent(list, value, index, selected, focus);
			if (value instanceof Route.Chapter)
			{
				Route.Chapter chapter = (Route.Chapter) value;
				String text = chapter.getShortTitle();
				List<Route.Step> steps = RouteGuide.trackableSteps(chapter);
				if (!steps.isEmpty())
				{
					int done = RouteGuide.countDone(steps, plugin::isStepDone);
					text += "  " + done + "/" + steps.size();
				}
				setText(text);
			}
			return this;
		}
	}

	/** Laat de inhoud de breedte van de viewport volgen, zodat er nooit horizontaal gescrold wordt. */
	private static class ScrollableWrapper extends JPanel implements Scrollable
	{
		ScrollableWrapper()
		{
			super(new BorderLayout());
			setBackground(ColorScheme.DARK_GRAY_COLOR);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height - 32;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
