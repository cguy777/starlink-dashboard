package fibrous.starlink;

public class About {
	
	public static final String APPLICATION = "Starlink Dashboard";
	public static final String VERSION = "1.0.0";
	public static final String COPYRIGHT_HOLDER = "Noah McLean";
	public static final String COPYRIGHT_YEAR = "2025";
	
	public static String getCopyrightMessage() {
		return "Copyright (c) " +
				About.COPYRIGHT_YEAR +
				", " +
				COPYRIGHT_HOLDER;
	}

	public static String getAboutMessage() {
		return APPLICATION + " " + VERSION +
				"\n" +
				getCopyrightMessage();
	}
}
