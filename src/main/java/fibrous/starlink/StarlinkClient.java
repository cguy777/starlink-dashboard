package fibrous.starlink;

import java.util.Date;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import com.google.rpc.context.AttributeContext.Request;

import SpaceX.API.Device.DeviceGrpc;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.Alerts;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.AlignmentStats;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.DisablementCode;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.Location;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.TestResult;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.TestResultCode;
import SpaceX.API.Device.DeviceOuterClass.GetDiagnosticsRequest;
import SpaceX.API.Device.DeviceOuterClass.Response;
import SpaceX.API.Device.DeviceOuterClass.ToDevice;
import SpaceX.API.Device.DeviceOuterClass.WifiGetDiagnosticsResponse;
import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;

public class StarlinkClient {
	
	DeviceGrpc.DeviceBlockingStub blockingStub;
	
	public StarlinkClient(Channel channel) {
		blockingStub = DeviceGrpc.newBlockingStub(channel);
	}
	
	public void getDiagnostics() throws ParseException {
		ToDevice diagReq = ToDevice.newBuilder()
				.setRequest(SpaceX.API.Device.DeviceOuterClass.Request
						.newBuilder()
						.setGetDiagnostics(GetDiagnosticsRequest.newBuilder()
								.build())
						.build())
				.build();
		Response diagResp = blockingStub.handle(diagReq.getRequest());
		
		DishGetDiagnosticsResponse dishDiags = diagResp.getDishGetDiagnostics();
		WifiGetDiagnosticsResponse wifiDiags = diagResp.getWifiGetDiagnostics();
		
		System.out.println("ID: " + dishDiags.getId());
		System.out.println("HW VERSION: " + dishDiags.getHardwareVersion());
		System.out.println("SW VERSION: " + dishDiags.getSoftwareVersion());
		System.out.println("UTC OFFSET: " + dishDiags.getUtcOffsetS());
		
		//HW SELF TEST STATUS (OVERALL)
		TestResult hwSelfTestStatus = dishDiags.getHardwareSelfTest();
		System.out.println("HARDWARE SELF TEST STATUS: " + hwSelfTestStatus);
		
		//SELF TEST STATUS CODES
		System.out.println("SELF TEST STATUS CODES:");
		List<TestResultCode> returnedCodes = dishDiags.getHardwareSelfTestCodesList();
		if(returnedCodes.size() == 0) {
			System.out.println("\tNone");
		} else {
			for(int i = 0; i < returnedCodes.size(); i++)
				System.out.println("\t" + returnedCodes.get(i));
		}
		System.out.println();
		
		//ALERTS
		System.out.println("PRESENT ALERTS:");
		Alerts alerts = dishDiags.getAlerts();
		if(alerts.getDishIsHeating())
			System.out.println("\t-dish is heating");
		if(alerts.getDishThermalThrottle())
			System.out.println("\t-cpu thermal throttle");
		if(alerts.getDishThermalShutdown())
			System.out.println("\t-dish thermal shutdown");
		if(alerts.getPowerSupplyThermalThrottle())
			System.out.println("\t-power supply thermal throttle");
		if(alerts.getMotorsStuck())
			System.out.println("\t-dish motors stuck");
		if(alerts.getMastNotNearVertical())
			System.out.println("\t-dish not oriented near-vertically");
		if(alerts.getSlowEthernetSpeeds())
			System.out.println("\t-slow negotiated ethernet speed");
		if(alerts.getSoftwareInstallPending())
			System.out.println("\t-software install pending");
		if(alerts.getMovingTooFastForPolicy())
			System.out.println("\t-moving to fast for policy");
		if(alerts.getObstructed())
			System.out.println("\t-obstructed");
		System.out.println();
		
		//DISABLEMENT CODE
		DisablementCode disableCode = dishDiags.getDisablementCode();
		System.out.println("DISABLEMENT CODE (OKAY means no disablement code present):");
		System.out.println("\t" + disableCode);
		
		//LOCATION
		DateFormat format = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.MEDIUM);
		format.setTimeZone(TimeZone.getTimeZone("ZULU"));
		Date jan_01_70 = new Date("01/01/70 00:00:00");
		Date jan_06_80 = new Date("01/06/80 00:00:00");
		long gpsEpochDiffMS = jan_06_80.getTime() - jan_01_70.getTime();
		System.out.println("LOCATION:");
		Location location =  dishDiags.getLocation();
		if(!location.getEnabled()) {
			System.out.println("\tlocation not enabled");
		} else {
			System.out.println("\tLAT:\t\t " + location.getLatitude());
			System.out.println("\tLONG:\t\t " + location.getLongitude());
			System.out.println("\tALT (meters):\t " + location.getAltitudeMeters());

			if(location.getUncertaintyMetersValid())
				System.out.println("\tUNCERTAINTY DISTANCE (meters): " + location.getUncertaintyMeters());
			else
				System.out.println("\tUNCERTAINTY DISTANCE IS NOT VALID");
			
			//location.getGpsTimeS() is in seconds from 6 Jan 1980, currently assuming zulu time.
			long unixEpochTimeMS = (long) ((location.getGpsTimeS() * 1000) + gpsEpochDiffMS);
			Date date = new Date(unixEpochTimeMS);
			
			String formattedTime = format.format(date);
			System.out.println("\tGPS TIME (zulu):\t\t " + formattedTime);
			System.out.println("\tRAW GPS EPOCH TIME (seconds):\t " + (long) location.getGpsTimeS());
		}
		System.out.println();
		
		//ALIGNMENT
		AlignmentStats alignment = dishDiags.getAlignmentStats();
		System.out.println("CURRENT DISH ALIGNMENT:");
		System.out.println("\tAZIMUTH:\t\t " + alignment.getBoresightAzimuthDeg() + " deg");
		System.out.println("\tDESIRED AZIMUTH:\t " + alignment.getDesiredBoresightAzimuthDeg() + " deg");
		System.out.println("\tELEVATION:\t\t " + alignment.getBoresightElevationDeg() + " deg");
		System.out.println("\tDESIRED ELEVATION:\t " + alignment.getDesiredBoresightElevationDeg() + " deg");
		System.out.println();
		
		//STOWED
		System.out.println("IS STOWED: " + dishDiags.getStowed());
	}
	
	public static void main(String[]args) throws InterruptedException {
		int routerPort = 9000;
		String dishSocketAddr = "192.168.100.1:9200";
		String routerSocketAddr = "192.168.1.1:9000";
		String testSocketAddr = "localhost:9200";
		
		ManagedChannel channel = Grpc.newChannelBuilder(testSocketAddr, InsecureChannelCredentials.create()).build();
		try {
			StarlinkClient client = new StarlinkClient(channel);
			try {
				client.getDiagnostics();
			} catch (ParseException e) {
				e.printStackTrace();
			}
		} finally {
			channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
		}
	}
}
