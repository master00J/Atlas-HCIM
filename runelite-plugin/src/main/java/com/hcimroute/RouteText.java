package com.hcimroute;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JLabel;

/**
 * Zet de stap-tekst uit route.json (vetgedrukt met **, tags tussen [ ]) om
 * naar HTML voor het zijpaneel of naar platte tekst voor de overlay.
 */
public final class RouteText
{
	private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
	private static final Pattern TAG = Pattern.compile("\\[([^\\[\\]]{2,60})\\]");
	private static final Pattern HCIM = Pattern.compile("\\bHCIM\\b");
	private static final Pattern NIEUW = Pattern.compile("\\bNIEUW\\b");

	static final String TAG_COLOR = "#9a9a9a";
	static final String HCIM_COLOR = "#ff6b5e";
	static final String NEW_COLOR = "#ffb347";
	static final String READY_COLOR = "#5ec26a";

	private RouteText()
	{
	}

	/**
	 * Vervangt tekens die het RuneScape-lettertype van RuneLite niet kent
	 * (pijlen, gedachtestreepjes, typografische aanhalingstekens).
	 */
	public static String sanitize(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("↔", "<->")
			.replace("→", "->")
			.replace("–", "-")
			.replace("—", "-")
			.replace("…", "...")
			.replace("‘", "'")
			.replace("’", "'")
			.replace("“", "\"")
			.replace("”", "\"");
	}

	/** Maakt tekst veilig voor HTML (na {@link #sanitize}). */
	public static String escape(String s)
	{
		return sanitize(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** HTML-fragment zonder &lt;html&gt;-omhulsel. */
	public static String toHtmlFragment(String text)
	{
		String h = escape(text);
		h = BOLD.matcher(h).replaceAll("<b>$1</b>");
		h = TAG.matcher(h).replaceAll("<font color='" + TAG_COLOR + "'>[$1]</font>");
		h = HCIM.matcher(h).replaceAll("<font color='" + HCIM_COLOR + "'><b>HCIM</b></font>");
		h = NIEUW.matcher(h).replaceAll("<font color='" + NEW_COLOR + "'>NIEUW</font>");
		return h;
	}

	/** Volledige HTML voor een JLabel dat op {@code widthPx} pixels afbreekt. */
	public static String toHtml(String text, int widthPx)
	{
		return wrap(toHtmlFragment(text), widthPx);
	}

	public static String wrap(String fragment, int widthPx)
	{
		return "<html><body style='width:" + cssWidth(widthPx) + "px'>" + fragment + "</body></html>";
	}

	private static double htmlScale;

	/**
	 * Swing rekent een CSS-breedte niet 1-op-1 om naar schermpixels (meestal x1,33,
	 * afhankelijk van de DPI). We meten die factor één keer, zodat tekst precies
	 * binnen het zijpaneel afbreekt.
	 */
	static synchronized int cssWidth(int px)
	{
		if (htmlScale == 0)
		{
			StringBuilder words = new StringBuilder();
			for (int i = 0; i < 80; i++)
			{
				words.append("word ");
			}
			JLabel probe = new JLabel("<html><body style='width:100px'>" + words + "</body></html>");
			double measured = probe.getPreferredSize().getWidth() / 100.0;
			htmlScale = measured > 0.5 && measured < 4 ? measured : 1.3;
		}
		return Math.max(40, (int) Math.floor(px / htmlScale));
	}

	/**
	 * Platte tekst voor de overlay. Het RuneScape-lettertype kent geen pijlen of
	 * typografische tekens, dus die worden vervangen.
	 */
	public static String toPlain(String text, int maxLength)
	{
		if (text == null)
		{
			return "";
		}
		String p = sanitize(text).replace("**", "");
		if (maxLength > 0 && p.length() > maxLength)
		{
			int cut = p.lastIndexOf(' ', maxLength - 3);
			p = p.substring(0, cut > maxLength / 2 ? cut : maxLength - 3) + "...";
		}
		return p;
	}

	/** De tekst tot en met de eerste zin, voor korte weergaves. */
	public static String firstSentence(String text, int maxLength)
	{
		String p = toPlain(text, 0);
		Matcher m = Pattern.compile("^(.+?[.!?])(\\s|$)").matcher(p);
		String s = m.find() ? m.group(1) : p;
		return toPlain(s, maxLength);
	}

	public static String wikiUrl(String pageName)
	{
		String page = pageName.trim().replace(' ', '_');
		return "https://oldschool.runescape.wiki/w/" + URLEncoder.encode(page, StandardCharsets.UTF_8)
			.replace("%2F", "/")
			.replace("%27", "'")
			.replace("%21", "!");
	}

	public static String wikiSearchUrl(String query)
	{
		return "https://oldschool.runescape.wiki/w/Special:Search?search="
			+ URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
	}
}
