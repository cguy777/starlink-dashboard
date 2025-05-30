package fibrous.starlink;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;

import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.Alerts;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.TestResultCode;

public class StarlinkDashboard {
	
	StarlinkClient client;
	
	JFrame mainFrame;
		JPanel mainPanel;
	
	JPanel infoPanel;
		JLabel idLabel;
		JLabel hwVerLabel;
		JLabel swVerLabel;
	
	JPanel statusPanel;
		JPanel hwPOSTCodesPanel;
			JLabel hwPOSTLabel;
			JList<String> hwPOSTList;
		JPanel alertPanel;
			JList<String> alertList;
	
	JPanel alignmentPanel;
		JLabel azLabel;
		JLabel desAzLabel;
		JLabel elLabel;
		JLabel desElLabel;
	
	public static void main(String[]args) {
		StarlinkDashboard dashboard = new StarlinkDashboard();
	}
	
	public StarlinkDashboard() {
		mainFrame = new JFrame("Starlink Dashboard");
		mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		mainFrame.setSize(640, 480);
		mainFrame.setVisible(true);
		mainPanel = new JPanel(new BorderLayout());
		mainFrame.add(mainPanel);
		
		//INFO PANEL
		infoPanel = new JPanel(new GridLayout(0, 1));
		infoPanel.setBorder(BorderFactory.createTitledBorder(null, "Dish Information", TitledBorder.LEADING, TitledBorder.TOP));
		idLabel = new JLabel("ID: ");
		hwVerLabel = new JLabel("HW Version: ");
		swVerLabel = new JLabel("SW Version: ");
		infoPanel.add(idLabel);
		infoPanel.add(hwVerLabel);
		infoPanel.add(swVerLabel);
		mainFrame.add(infoPanel, BorderLayout.NORTH);
		
		//STATUS PANEL
		statusPanel = new JPanel(new GridLayout(1, 0));
		statusPanel.setBorder(BorderFactory.createTitledBorder(null, "Hardware Status", TitledBorder.CENTER, TitledBorder.TOP));
		alertPanel = new JPanel();
		alertPanel.setBorder(BorderFactory.createTitledBorder(null, "Alerts", TitledBorder.CENTER, TitledBorder.TOP));
		alertList = new JList<>();
		alertPanel.add(alertList);
		setAlerts(new ArrayList<String>());
		statusPanel.add(alertPanel);
		hwPOSTCodesPanel = new JPanel(new BorderLayout());
		hwPOSTCodesPanel.setBorder(BorderFactory.createTitledBorder(null, "Power On Self Test", TitledBorder.CENTER, TitledBorder.TOP));
		hwPOSTLabel = new JLabel("POST Result: ");
		hwPOSTLabel.setHorizontalAlignment(SwingConstants.CENTER);
		hwPOSTList = new JList<>();
		hwPOSTCodesPanel.add(hwPOSTLabel, BorderLayout.NORTH);
		setPOSTCodes(new ArrayList<String>());
		statusPanel.add(hwPOSTCodesPanel);
		mainFrame.add(statusPanel, BorderLayout.CENTER);
		
		//ALIGNMENT PANEL
		alignmentPanel = new JPanel(new GridLayout(0, 1));
		azLabel = new JLabel();
		desAzLabel = new JLabel();
		elLabel = new JLabel();
		desElLabel = new JLabel();
		alignmentPanel.add(azLabel);
		alignmentPanel.add(desAzLabel);
		alignmentPanel.add(elLabel);
		alignmentPanel.add(desElLabel);
		setAlignmentInfo("", "", "", "");
		//mainFrame.add(alignmentPanel, BorderLayout.EAST);
		
		client = new StarlinkClient();
		performUpdate();
	}
	
	public void setID(String id) {
		idLabel.setText("ID: " + id);
	}
	
	public void setHWVersion(String version) {
		hwVerLabel.setText("HW Version: " + version);
	}
	
	public void setSWVersion(String version) {
		swVerLabel.setText("SW Version: " + version);
	}
	
	public void setAlerts(ArrayList<String> alerts) {
		alertPanel.removeAll();
		if(alerts.isEmpty()) {
			alertPanel.add(new JLabel("No active alerts"));
			return;
		}
		
		alertList = new JList(alerts.toArray(new String[] {}));
		alertPanel.add(alertList);
	}
	
	public void setAlignmentInfo(String az, String desAz, String el, String desEl) {
		azLabel.setText("Current Azimuth: " + az);
		desAzLabel.setText("Desired Azimuth: " + desAz);
		elLabel.setText("Current Elevation: " + el);
		desElLabel.setText("Desired Elevation: " + desEl);
	}
	
	public void setPOSTStatus(String status) {
		hwPOSTLabel.setText("POST Result: " + status);
	}
	
	
	public void setPOSTCodes(ArrayList<String> codes) {
		hwPOSTCodesPanel.remove(hwPOSTList);
		if(codes.isEmpty()) {
			//hwPOSTCodesPanel.add(new JLabel("No codes present"));
			return;
		}
		
		hwPOSTList = new JList(codes.toArray(new String[] {}));
		hwPOSTCodesPanel.add(hwPOSTList);
	}
	
	public void performUpdate() {
		DishGetDiagnosticsResponse diags = client.getDishDiagnostics();
		
		//SET INFO
		setID(diags.getId());
		setHWVersion(diags.getHardwareVersion());
		setSWVersion(diags.getSoftwareVersion());
		
		//SET ALERTS
		ArrayList<String> alertsList = new ArrayList<>();
		Alerts alerts = diags.getAlerts();
		if(alerts.getDishIsHeating())
			alertsList.add("dish is heating");
		if(alerts.getDishThermalThrottle())
			alertsList.add("cpu thermal throttle");
		if(alerts.getDishThermalShutdown())
			alertsList.add("dish thermal shutdown");
		if(alerts.getPowerSupplyThermalThrottle())
			alertsList.add("power supply thermal throttle");
		if(alerts.getMotorsStuck())
			alertsList.add("dish motors stuck");
		if(alerts.getMastNotNearVertical())
			alertsList.add("dish not oriented near-vertically");
		if(alerts.getSlowEthernetSpeeds())
			alertsList.add("slow negotiated ethernet speed");
		if(alerts.getSoftwareInstallPending())
			alertsList.add("software install pending");
		if(alerts.getMovingTooFastForPolicy())
			alertsList.add("moving to fast for policy");
		if(alerts.getObstructed())
			alertsList.add("obstructed");
		setAlerts(alertsList);
		
		//POST
		setPOSTStatus(diags.getHardwareSelfTest().name());
		List<TestResultCode> codeList = diags.getHardwareSelfTestCodesList();
		ArrayList<String> postCodes = new ArrayList<String>();
		for(int i = 0; i < codeList.size(); i++)
			postCodes.add(codeList.get(i).name());
		setPOSTCodes(postCodes);
	}
}
