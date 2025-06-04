package fibrous.starlink;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import SpaceX.API.Device.DeviceGrpc;
import SpaceX.API.Device.DeviceOuterClass;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse;
import SpaceX.API.Device.DeviceOuterClass.GetDiagnosticsRequest;
import SpaceX.API.Device.DeviceOuterClass.Response;
import SpaceX.API.Device.DeviceOuterClass.ToDevice;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.Alerts;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.AlignmentStats;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.DisablementCode;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.Location;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.TestResult;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.TestResultCode;
import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;

public class StarlinkClient {
	
	DeviceGrpc.DeviceBlockingStub blockingStub;
	ManagedChannel channel;

	String dishSocketAddr = "192.168.100.1:9200";
	String routerSocketAddr = "192.168.1.1:9000";
	String testSocketAddr = "localhost:9200";
	
	volatile DishGetDiagnosticsResponse lastResponse;
	
	public StarlinkClient() {
		channel = Grpc.newChannelBuilder(testSocketAddr, InsecureChannelCredentials.create()).build();
		blockingStub = DeviceGrpc.newBlockingStub(channel);
	}
	
	public DishGetDiagnosticsResponse getDishDiagnostics() {
		ToDevice diagReq = ToDevice.newBuilder()
				.setRequest(DeviceOuterClass.Request
						.newBuilder()
						.setGetDiagnostics(GetDiagnosticsRequest.newBuilder()
								.build())
						.build())
				.build();
		
		Response diagResp = blockingStub.handle(diagReq.getRequest());
		
		lastResponse = diagResp.getDishGetDiagnostics();
		return diagResp.getDishGetDiagnostics();
	}
	
	public String getDishDiagnosticsString() {
		String response = "";
		
		response += "ID: " + lastResponse.getId() + "\n";
		response += "HW VERSION: " + lastResponse.getHardwareVersion() + "\n";
		response += "SW VERSION: " + lastResponse.getSoftwareVersion() + "\n";
		response += "UTC OFFSET: " + lastResponse.getUtcOffsetS() + "\n";
		
		//HW SELF TEST STATUS (OVERALL)
		TestResult hwSelfTestStatus = lastResponse.getHardwareSelfTest();
		response += "HARDWARE SELF TEST STATUS: " + hwSelfTestStatus + "\n";
		
		//SELF TEST STATUS CODES
		response += "SELF TEST STATUS CODES:" + "\n";
		List<TestResultCode> returnedCodes = lastResponse.getHardwareSelfTestCodesList();
		if(returnedCodes.size() == 0) {
			response += "\tNone \n";
		} else {
			for(int i = 0; i < returnedCodes.size(); i++)
				response += "\t" + returnedCodes.get(i) + "\n";
		}
		response += "\n";
		
		//ALERTS
		String alertString = "";
		response += "PRESENT ALERTS:\n";
		Alerts alerts = lastResponse.getAlerts();
		if(alerts.getDishIsHeating())
			alertString += "\tdish is heating\n";
		if(alerts.getDishThermalThrottle())
			alertString += "\tcpu thermal throttle\n";
		if(alerts.getDishThermalShutdown())
			alertString += "\tdish thermal shutdown\n";
		if(alerts.getPowerSupplyThermalThrottle())
			alertString += "\tpower supply thermal throttle\n";
		if(alerts.getMotorsStuck())
			alertString += "\tdish motors stuck\n";
		if(alerts.getMastNotNearVertical())
			alertString += "\tdish not oriented near-vertically\n";
		if(alerts.getSlowEthernetSpeeds())
			alertString += "\tslow negotiated ethernet speed\n";
		if(alerts.getSoftwareInstallPending())
			alertString += "\tsoftware install pending\n";
		if(alerts.getMovingTooFastForPolicy())
			alertString += "\tmoving to fast for policy\n";
		if(alerts.getObstructed())
			alertString += "\tobstructed\n";
		if(alertString.isEmpty())
			alertString += "\tno alerts present\n";
		response += alertString + "\n";
		
		//DISABLEMENT CODE
		DisablementCode disableCode = lastResponse.getDisablementCode();
		response += "DISABLEMENT CODE (OKAY means no disablement code present):\n";
		response += "\t" + disableCode + "\n";
		response += "\n";
		
		//LOCATION
		DateFormat format = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.MEDIUM);
		format.setTimeZone(TimeZone.getTimeZone("ZULU"));
		Date jan_01_70 = new Date("01/01/70 00:00:00");
		Date jan_06_80 = new Date("01/06/80 00:00:00");
		long gpsEpochDiffMS = jan_06_80.getTime() - jan_01_70.getTime();
		response += "LOCATION:\n";
		Location location =  lastResponse.getLocation();
		if(!location.getEnabled()) {
			response += "\tlocation not enabled\n";
		} else {
			response += "\tLAT:\t\t " + location.getLatitude() + "\n";
			response += "\tLONG:\t\t " + location.getLongitude() + "\n";
			response += "\tALT (meters):\t " + location.getAltitudeMeters() + "\n";

			if(location.getUncertaintyMetersValid())
				response += "\tUNCERTAINTY DISTANCE (meters): " + location.getUncertaintyMeters() + "\n";
			else
				response += "\tUNCERTAINTY DISTANCE IS NOT VALID\n";
			
			//location.getGpsTimeS() is in seconds from 6 Jan 1980, currently assuming zulu time.
			long unixEpochTimeMS = (long) ((location.getGpsTimeS() * 1000) + gpsEpochDiffMS);
			Date date = new Date(unixEpochTimeMS);
			
			String formattedTime = format.format(date);
			response += "\tGPS TIME (zulu):\t\t " + formattedTime + "\n";
			response += "\tRAW GPS EPOCH TIME (seconds):\t " + (long) location.getGpsTimeS() + "\n";
		}
		response += "\n";
		
		//ALIGNMENT
		AlignmentStats alignment = lastResponse.getAlignmentStats();
		response += "CURRENT DISH ALIGNMENT:\n";
		response += "\tAZIMUTH:\t\t\t " + alignment.getBoresightAzimuthDeg() + " deg\n";
		response += "\tDESIRED AZIMUTH:\t\t " + alignment.getDesiredBoresightAzimuthDeg() + " deg\n";
		response += "\tELEVATION:\t\t\t " + alignment.getBoresightElevationDeg() + " deg\n";
		response += "\tDESIRED ELEVATION:\t " + alignment.getDesiredBoresightElevationDeg() + " deg\n";
		response += "\n";
		
		//STOWED
		response += "IS STOWED: " + lastResponse.getStowed() + "\n";
		
		return response;
	}
}
