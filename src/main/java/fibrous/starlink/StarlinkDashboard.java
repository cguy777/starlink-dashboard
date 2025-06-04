/*
BSD 3-Clause License

Copyright (c) 2025 Noah McLean

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package fibrous.starlink;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window.Type;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
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
	DiagnosticsUpdater updater;
	Thread updaterThread;
	boolean prevConnectedState = false;
	boolean switchedConnectedState = false;
	volatile DishGetDiagnosticsResponse lastDishResponse = null;
	Date lastUpdateTime = null;
	
	ArrayList<String> prevAlerts;
	TestResult prevPOSTResult;
	ArrayList<String> prevPOSTCodes;
	DisablementCode prevDisableCode;
	boolean firstUpdate = true;
	boolean updatePanels = true;
	
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
		
	JPanel errorPanel;
		JLabel errorLabel;
		JLabel errorReasonLabel;
	
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
		updater = new DiagnosticsUpdater(this, 5000);
		
		mainFrame = new JFrame("Starlink Dashboard");
		mainFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		mainFrame.addWindowListener(new MainWindowListener(this));
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
		statusPanel.setVisible(true);
		
		//DISABLEMENT PANEL
		disablementPanel = new JPanel();
		//disablementPanel.setBorder(BorderFactory.createLineBorder(Color.black));
		disablementLabel = new JLabel("THIS STARLINK DISH IS DISABLED");
		disablementLabel.setHorizontalAlignment(SwingConstants.CENTER);
		disablementLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 24));
		disablementLabel.setForeground(Color.red);
		disablementReasonLabel = new JLabel();
		disablementReasonLabel.setHorizontalAlignment(SwingConstants.CENTER);
		disablementReasonLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 24));
		disablementReasonLabel.setForeground(Color.red);
		disablementPanel.add(disablementLabel);
		disablementPanel.add(disablementReasonLabel);
		disablementPanel.setVisible(true);
		
		//ERROR PANEL
		errorPanel = new JPanel();
		errorLabel = new JLabel("ERROR");
		errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
		errorLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 24));
		errorLabel.setForeground(Color.red);
		errorReasonLabel = new JLabel();
		errorReasonLabel.setHorizontalAlignment(SwingConstants.CENTER);
		errorReasonLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
		//errorReasonLabel.setForeground(Color.red);
		errorPanel.add(errorLabel);
		errorPanel.add(errorReasonLabel);
		errorPanel.setVisible(true);
		
		
		//Not currently used
		/*
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
		*/
		
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
		
		updaterThread = Thread.ofVirtual().start(updater);
	}
	
	public void shutdown() {
		responseTextFrame.setVisible(false);
		updaterThread.interrupt();
		client.shutdown();
		try {
			//Not sure if it matters, but just to allow time for any cleanup from ManagedChannel.shutdownNow()
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}
	
	public void showError(String error, String reason) {
		errorLabel.setText(error);
		errorReasonLabel.setText(reason);
		
		mainPanel.remove(statusPanel);
		mainPanel.remove(disablementPanel);
		mainPanel.add(errorPanel);
		
		updatePanels = true;
	}
	
	public boolean isSameAlerts(ArrayList<String> newAlerts) {
		if(firstUpdate)
			return false;
		
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
		if(firstUpdate)
			return false;
		
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
		
		switchedConnectedState = true;
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
		if(alerts.isEmpty())
			alertPanel.add(new JLabel("No active alerts"));
		
		for(int i = 0; i < alerts.size(); i++) {
			JLabel alertLabel = new JLabel(alerts.get(i));
			alertLabel.setForeground(Color.RED);
			alertPanel.add(alertLabel);
		}
		
		prevAlerts = alerts;
		updatePanels = true;
	}
	
	public void setAlignmentInfo(String az, String desAz, String el, String desEl) {
		azLabel.setText("Current Azimuth: " + az);
		desAzLabel.setText("Desired Azimuth: " + desAz);
		elLabel.setText("Current Elevation: " + el);
		desElLabel.setText("Desired Elevation: " + desEl);
	}
	
	public void setPOSTStatus(TestResult result) {
		//Do nothing if nothing has changed
		if(!firstUpdate)
			if(result == prevPOSTResult)
				return;
		
		hwPOSTLabel.setText("POST Result: " + result.toString());
		if(result == TestResult.FAILED)
			hwPOSTLabel.setForeground(Color.red);
		else
			hwPOSTLabel.setForeground(Color.DARK_GRAY);
		
		prevPOSTResult = result;
		
		updatePanels = true;
	}
	
	
	public void setPOSTCodes(ArrayList<String> codes) {
		//Do nothing if nothing has changed
		if(isSamePOSTCodes(codes))
			return;
		
		hwPOSTCodesPanel.removeAll();
		hwPOSTCodesPanel.add(hwPOSTLabel);
		
		if(codes.isEmpty()) {
			hwPOSTCodesPanel.add(new JLabel("No codes present"));
		}
		
		for(int i = 0; i < codes.size(); i++) {
			JLabel codeLabel = new JLabel(codes.get(i));
			codeLabel.setForeground(Color.red);
			hwPOSTCodesPanel.add(codeLabel);
		}
		
		prevPOSTCodes = codes;
		
		updatePanels = true;
	}
	
	public void setDisablementCode(DisablementCode disablement) {
		//do nothing if it's the same code
		if(disablement == prevDisableCode)
			if(!switchedConnectedState)
				return;
		
		if(disablement == DisablementCode.OKAY) {
			mainPanel.remove(disablementPanel);
			mainPanel.remove(errorPanel);
			mainPanel.add(statusPanel, BorderLayout.CENTER);
		} else {
			mainPanel.remove(statusPanel);
			mainPanel.remove(errorPanel);
			mainPanel.add(disablementPanel);
			disablementReasonLabel.setText("Reason: " + disablement.toString());
		}
		
		prevDisableCode = disablement;
		
		switchedConnectedState = false;
		updatePanels = true;
	}
	
	public synchronized DishGetDiagnosticsResponse accessLastResponse() {
		return lastDishResponse;
	}
	
	public void zeroDisplay() {
		setID("");
		setHWVersion("");
		setSWVersion("");
		
		mainPanel.remove(statusPanel);
		mainPanel.remove(disablementPanel);
		mainPanel.remove(errorPanel);
		
		updatePanels = true;
	}
	
	public void performUpdate() {
		DishGetDiagnosticsResponse diags = null;
		try {
			diags = client.getDishDiagnostics();
			lastDishResponse = diags;
		} catch(StatusRuntimeException e) {
			//CAN'T CONNECT TO DISH
			if(e.getMessage().contains("io exception")) {
				setIsConnected(false);
			}
			//UNIMPLEMENTED
			//Could potentially be a problem with the service plan or software/hardware not implementing the gRPC calls.
			else if(e.getMessage().contains("UNIMPLEMENTED")) {
				showError("UNIMPLMENTED GRPC FUNCTION", "Connected, but unable to retrieve diagnostics due to software, hardware, or service plan limitations.");
				setIsConnected(true);
			}
			//ALL OTHERS
			else {
				JOptionPane.showMessageDialog(mainFrame, "Unhandled Exception.  See ErrorLog.log for more details", "Unhandled Exception", JOptionPane.ERROR_MESSAGE);
				logError(e);
				shutdown();
			}
			
			firstUpdate = false;
			
			if(updatePanels) {
				mainPanel.revalidate();
				mainPanel.repaint();
				updatePanels = false;
			}
			
			return;
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
		
		if(updatePanels) {
			mainPanel.revalidate();
			mainPanel.repaint();
			updatePanels = false;
		}
		
		lastUpdateTime = new Date();
	}
	
	public void showResponseText() {
		//Check if there's anything to show
		if(lastDishResponse == null) {
			JOptionPane.showMessageDialog(mainFrame, "No information has been received from the dish", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		responseTextArea.setText("Current as of " + lastUpdateTime + "\n\n" + client.getDishDiagnosticsString());
		responseTextFrame.setVisible(true);
	}
	
	public void logError(Exception e) {
		try {
			FileOutputStream errorStream = new FileOutputStream("ErrorLog.log");
			PrintStream ps = new PrintStream(errorStream);
			e.printStackTrace(ps);
			errorStream.flush();
			errorStream.close();
		} catch (Exception e1) {
			JOptionPane.showMessageDialog(mainFrame, "Cannot log error message to ErrorLog.log!\nReason: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
}

class DiagnosticsUpdater implements Runnable {
	
	StarlinkDashboard dashboard;
	volatile int updateInterval;
	
	public DiagnosticsUpdater(StarlinkDashboard dashboard, int updateInterval) {
		this.dashboard = dashboard;
		this.updateInterval = updateInterval;
	}
	
	@Override
	public void run() {
		while(true) {			
			try {
				dashboard.performUpdate();
				Thread.sleep(updateInterval);
			} catch (InterruptedException e) {
				break;
			}
		}
	}
}

class MainWindowListener implements WindowListener {

	StarlinkDashboard dashboard;
	
	public MainWindowListener(StarlinkDashboard dashboard) {
		this.dashboard = dashboard;
	}
	
	@Override
	public void windowClosing(WindowEvent e) {
		//Allows the main window to close before final cleanup
		Thread.ofPlatform().start(() -> {
			dashboard.shutdown();
		});
	}
	
	@Override
	public void windowOpened(WindowEvent e) {}
	@Override
	public void windowClosed(WindowEvent e) {}
	@Override
	public void windowIconified(WindowEvent e) {}
	@Override
	public void windowDeiconified(WindowEvent e) {}
	@Override
	public void windowActivated(WindowEvent e) {}
	@Override
	public void windowDeactivated(WindowEvent e) {}
}