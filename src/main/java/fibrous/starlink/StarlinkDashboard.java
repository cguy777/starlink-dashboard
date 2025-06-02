package fibrous.starlink;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window.Type;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;

import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.Alerts;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.DisablementCode;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.TestResult;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.TestResultCode;
import io.grpc.StatusRuntimeException;

public class StarlinkDashboard {
	
	StarlinkClient client;
	boolean prevConnectedState = false;
	volatile DishGetDiagnosticsResponse lastDishResponse = null;
	
	ArrayList<String> prevAlerts;
	TestResult prevPOSTResult;
	ArrayList<String> prevPOSTCodes;
	DisablementCode prevDisableCode;
	boolean firstUpdate = true;
	
	JFrame mainFrame;
		JPanel mainPanel;
		DashboardMenuBar menuBar;
	
	JPanel infoPanel;
		JLabel connectedLabel;
		JLabel idLabel;
		JLabel hwVerLabel;
		JLabel swVerLabel;
	
	JPanel statusPanel;
		JPanel hwPOSTCodesPanel;
			JLabel hwPOSTLabel;
		JPanel alertPanel;
		
	JPanel disablementPanel;
		JLabel disablementLabel;
		JLabel disablementReasonLabel;
	
	JPanel alignmentPanel;
		JLabel azLabel;
		JLabel desAzLabel;
		JLabel elLabel;
		JLabel desElLabel;
		
	JFrame responseTextFrame;
		JScrollPane responseTextScroll;
			JTextArea responseTextArea;
	
	public static void main(String[]args) {
		StarlinkDashboard dashboard = new StarlinkDashboard();
	}
	
	public StarlinkDashboard() {
		prevAlerts = new ArrayList<>();
		prevPOSTResult = TestResult.NO_RESULT;
		prevPOSTCodes = new ArrayList<>();
		prevDisableCode = DisablementCode.UNKNOWN;
		
		client = new StarlinkClient();
		DiagnosticsUpdater updater = new DiagnosticsUpdater(this, 5000);
		
		mainFrame = new JFrame("Starlink Dashboard");
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.setSize(640, 480);
		menuBar = new DashboardMenuBar(this);
		mainPanel = new JPanel(new BorderLayout());
		mainFrame.setJMenuBar(menuBar);
		mainFrame.add(mainPanel);
		
		//INFO PANEL
		infoPanel = new JPanel(new GridLayout(0, 1));
		infoPanel.setBorder(BorderFactory.createTitledBorder(null, "Dish Information", TitledBorder.LEADING, TitledBorder.TOP));
		connectedLabel = new JLabel("");
		idLabel = new JLabel("ID: ");
		hwVerLabel = new JLabel("HW Version: ");
		swVerLabel = new JLabel("SW Version: ");
		infoPanel.add(connectedLabel);
		infoPanel.add(idLabel);
		infoPanel.add(hwVerLabel);
		infoPanel.add(swVerLabel);
		mainPanel.add(infoPanel, BorderLayout.NORTH);
		
		//STATUS PANEL
		statusPanel = new JPanel(new GridLayout(1, 0));
		statusPanel.setBorder(BorderFactory.createTitledBorder(null, "Hardware Status", TitledBorder.CENTER, TitledBorder.TOP));
		alertPanel = new JPanel();
		alertPanel.setLayout(new BoxLayout(alertPanel, BoxLayout.Y_AXIS));
		alertPanel.setBorder(BorderFactory.createTitledBorder(null, "Alerts", TitledBorder.CENTER, TitledBorder.TOP));
		setAlerts(new ArrayList<String>());
		statusPanel.add(alertPanel);
		hwPOSTCodesPanel = new JPanel();
		hwPOSTCodesPanel.setLayout(new BoxLayout(hwPOSTCodesPanel, BoxLayout.Y_AXIS));
		hwPOSTCodesPanel.setBorder(BorderFactory.createTitledBorder(null, "Power On Self Test", TitledBorder.CENTER, TitledBorder.TOP));
		hwPOSTLabel = new JLabel("POST Result: ");
		hwPOSTLabel.setName("hwPOSTLabel");
		hwPOSTLabel.setHorizontalAlignment(SwingConstants.CENTER);
		hwPOSTCodesPanel.add(hwPOSTLabel);
		statusPanel.add(hwPOSTCodesPanel);
		statusPanel.setVisible(false);
		mainPanel.add(statusPanel, BorderLayout.CENTER);
		
		//DISABLEMENT PANEL
		disablementPanel = new JPanel();
		//disablementPanel.setBorder(BorderFactory.createLineBorder(Color.black));
		disablementLabel = new JLabel("THIS STARLINK DISH IS DISABLED");
		disablementLabel.setHorizontalAlignment(SwingConstants.CENTER);
		disablementLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 24));
		disablementReasonLabel = new JLabel();
		disablementReasonLabel.setHorizontalAlignment(SwingConstants.CENTER);
		disablementReasonLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 24));
		disablementPanel.add(disablementLabel);
		disablementPanel.add(disablementReasonLabel);
		disablementPanel.setVisible(false);
		mainPanel.add(disablementPanel, BorderLayout.CENTER);
		
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
		//mainPanel.add(alignmentPanel, BorderLayout.EAST);
		
		//RAW RESPONSE FRAME
		responseTextFrame = new JFrame("Latest Dish Response");
		responseTextFrame.setSize(600, 800);
		responseTextFrame.setType(Type.UTILITY);
		responseTextArea = new JTextArea();
		responseTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		responseTextArea.setEditable(false);
		responseTextScroll = new JScrollPane(responseTextArea);
		responseTextFrame.add(responseTextScroll);
		responseTextFrame.setVisible(false);
		
		mainFrame.setVisible(true);
		
		Thread.ofVirtual().start(updater);
	}
	
	public boolean isSameAlerts(ArrayList<String> newAlerts) {
		//Easy quick check
		if(newAlerts.size() != prevAlerts.size())
			return false;
		
		for(int i = 0; i < newAlerts.size(); i++) {
			if(!prevAlerts.contains(newAlerts.get(i)))
				return false;
		}
		
		return true;
	}
	
	public boolean isSamePOSTCodes(ArrayList<String> newPOSTCodes) {
		//Easy quick check
		if(newPOSTCodes.size() != prevPOSTCodes.size())
			return false;
		
		for(int i = 0 ; i < newPOSTCodes.size(); i++) {
			if(!prevPOSTCodes.contains(newPOSTCodes.get(i)))
				return false;
		}
		
		return true;
	}
	
	public void setIsConnected(boolean connected) {
		if(!firstUpdate) {
			if(connected == prevConnectedState)
				return;
		}
		
		if(connected) {
			connectedLabel.setText("CONNECTED TO STARLINK DISH");
			connectedLabel.setForeground(Color.DARK_GRAY);
		} else {
			connectedLabel.setText("CANNOT COMMUNICATE WITH DISH");
			connectedLabel.setForeground(Color.red);
			zeroDisplay();
		}
		
		prevConnectedState = connected;
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
		//Do nothing if nothing has changed
		if(isSameAlerts(alerts))
			return;
		
		alertPanel.removeAll();
		//alertPanel.add(hwPOSTLabel);
		if(alerts.isEmpty()) {
			alertPanel.add(new JLabel("No active alerts"));
			return;
		}
		
		for(int i = 0; i < alerts.size(); i++)
			alertPanel.add(new JLabel(alerts.get(i)));
		
		mainPanel.revalidate();
		mainPanel.repaint();
	}
	
	public void setAlignmentInfo(String az, String desAz, String el, String desEl) {
		azLabel.setText("Current Azimuth: " + az);
		desAzLabel.setText("Desired Azimuth: " + desAz);
		elLabel.setText("Current Elevation: " + el);
		desElLabel.setText("Desired Elevation: " + desEl);
	}
	
	public void setPOSTStatus(TestResult result) {
		//Do nothing if nothing has changed
		if(result == prevPOSTResult)
			return;
		
		hwPOSTLabel.setText("POST Result: " + result.toString());
		prevPOSTResult = result;
		
		mainPanel.revalidate();
		mainPanel.repaint();
	}
	
	
	public void setPOSTCodes(ArrayList<String> codes) {
		//Do nothing if nothing has changed
		if(isSamePOSTCodes(codes))
			return;
		
		hwPOSTCodesPanel.removeAll();
		hwPOSTCodesPanel.add(hwPOSTLabel);
		
		if(codes.isEmpty()) {
			hwPOSTCodesPanel.add(new JLabel("No codes present"));
			return;
		}
		
		for(int i = 0; i < codes.size(); i++) {
			JLabel codeLabel = new JLabel(codes.get(i));
			hwPOSTCodesPanel.add(codeLabel);
		}
		
		prevPOSTCodes = codes;
		
		mainPanel.revalidate();
		mainPanel.repaint();
	}
	
	public void setDisablementCode(DisablementCode disablement) {
		//do nothing if it's the same code
		if(disablement == prevDisableCode)
			return;
		
		if(disablement == DisablementCode.OKAY) {
			disablementPanel.setVisible(false);
			statusPanel.setVisible(true);
		} else {
			statusPanel.setVisible(false);
			disablementPanel.setVisible(true);
			disablementReasonLabel.setText("Reason: " + disablement.toString());
		}
		
		prevDisableCode = disablement;
		
		mainPanel.revalidate();
		mainPanel.repaint();
	}
	
	public synchronized DishGetDiagnosticsResponse accessLastResponse() {
		return lastDishResponse;
	}
	
	public void zeroDisplay() {
		setID("");
		setHWVersion("");
		setSWVersion("");
		
		statusPanel.setVisible(false);
		disablementPanel.setVisible(false);
	}
	
	public void performUpdate() {
		DishGetDiagnosticsResponse diags = null;
		try {
			diags = client.getDishDiagnostics();
			lastDishResponse = diags;
		} catch(StatusRuntimeException e) {
			if(e.getMessage().contains("io exception")) {
				setIsConnected(false);
				
				firstUpdate = false;
				return;
			}
		}
		
		//SET INFO
		setIsConnected(true);
		setID(diags.getId());
		setHWVersion(diags.getHardwareVersion());
		setSWVersion(diags.getSoftwareVersion());
		
		//SET DISABLEMENT CODE
		setDisablementCode(diags.getDisablementCode());
		
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
		setPOSTStatus(diags.getHardwareSelfTest());
		List<TestResultCode> codeList = diags.getHardwareSelfTestCodesList();
		ArrayList<String> postCodes = new ArrayList<String>();
		for(int i = 0; i < codeList.size(); i++)
			postCodes.add(codeList.get(i).name());
		setPOSTCodes(postCodes);
		
		firstUpdate = false;
	}
	
	public void showResponseText() {
		//Check if there's anything to show
		if(lastDishResponse == null) {
			JOptionPane.showMessageDialog(null, "No information has been received from the dish", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		responseTextArea.setText(client.getDishDiagnosticsString());
		responseTextFrame.setVisible(true);
	}
}

class DiagnosticsUpdater implements Runnable {

	volatile boolean quit = false;
	
	StarlinkDashboard dashboard;
	volatile int updateInterval;
	
	public DiagnosticsUpdater(StarlinkDashboard dashboard, int updateInterval) {
		this.dashboard = dashboard;
		this.updateInterval = updateInterval;
	}
	
	@Override
	public void run() {
		while(!quit) {
			dashboard.performUpdate();
			
			try {
				Thread.sleep(updateInterval);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}