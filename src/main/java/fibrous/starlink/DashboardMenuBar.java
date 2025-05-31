package fibrous.starlink;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

public class DashboardMenuBar extends JMenuBar {
	
	StarlinkDashboard dashboard;
	
	JMenu fileMenu;
		JMenuItem showResponseTextMenuItem;
		JMenuItem settingsMenuItem;
		JMenuItem exitMenuItem;
	
	JMenu helpMenu;
		JMenuItem aboutMenuItem;
	
	public DashboardMenuBar(StarlinkDashboard dashboard) {
		this.dashboard = dashboard;
		
		//FILE MENU
		fileMenu = new JMenu("File");
		showResponseTextMenuItem = new JMenuItem("Show response text");
		showResponseTextMenuItem.addActionListener((action) -> {dashboard.showResponseText();});
		settingsMenuItem = new JMenuItem("Settings");
		exitMenuItem = new JMenuItem("Exit");
		fileMenu.add(showResponseTextMenuItem);
		fileMenu.addSeparator();
		fileMenu.add(settingsMenuItem);
		fileMenu.addSeparator();
		fileMenu.add(exitMenuItem);
		add(fileMenu);
		
		
		//HELP MENU
		helpMenu = new JMenu("Help");
		aboutMenuItem = new JMenuItem("About");
		helpMenu.add(aboutMenuItem);
		add(helpMenu);
	}
}
