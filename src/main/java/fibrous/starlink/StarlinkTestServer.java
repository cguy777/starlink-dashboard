package fibrous.starlink;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import SpaceX.API.Device.*;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.Alerts;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.AlignmentStats;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.DisablementCode;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.Location;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.TestResult;
import SpaceX.API.Device.DeviceOuterClass.DishGetDiagnosticsResponse.TestResultCode;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;

public class StarlinkTestServer {
	
	private Server server;
	
	private void start() throws IOException {
		int routerPort = 9000;
		int dishPort = 9200;
		
		ExecutorService executor = Executors.newFixedThreadPool(2);
		server = Grpc.newServerBuilderForPort(dishPort, InsecureServerCredentials.create()).executor(executor).addService(new DeviceImpl()).build().start();
		
		System.out.println("Starlink Test Server started");
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			
			@Override
			public void run() {
				System.err.println("*** shutting down Starlink Test Server since JVM is shutting down");
				
				try {
					StarlinkTestServer.this.stop();
				} catch(InterruptedException e) {
					if(server != null) {
						server.shutdownNow();
					}
					e.printStackTrace();
				} finally {
					executor.shutdown();
				}
				
				System.err.println("*** server shut down");
			}
		});
	}
	
	private void stop() throws InterruptedException {
		if(server != null) {
			server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
		}
	}
	
	private void blockUntilShutdown() throws InterruptedException {
		if(server != null) {
			server.awaitTermination();
		}
	}
	
	public static void main(String[]args) throws IOException, InterruptedException {
		StarlinkTestServer server = new StarlinkTestServer();
		server.start();
		server.blockUntilShutdown();
	}
	
	static class DeviceImpl extends DeviceGrpc.DeviceImplBase {
		
		@Override
		public void handle(DeviceOuterClass.Request request, io.grpc.stub.StreamObserver<DeviceOuterClass.Response> responseObserver) {
			Date jan_01_70 = new Date("01/01/70 00:00:00");
			Date jan_06_80 = new Date("01/06/80 00:00:00");
			long gpsEpochDiffMS = jan_06_80.getTime() - jan_01_70.getTime();
			
			//Can only send one as they are on separate servers (9200/9000) and joined together with "oneof"
			//WifiGetDiagnosticsResponse wifiResponse = WifiGetDiagnosticsResponse.newBuilder().setId("WifiNetwork").build();
			DishGetDiagnosticsResponse dishResponse = DishGetDiagnosticsResponse.newBuilder()
					.setId("1234567")
					.setHardwareVersion("1.2.3")
					.setSoftwareVersion("4.5.6")
					.setUtcOffsetS(-5)
					.setHardwareSelfTest(
							TestResult.FAILED)
					.addHardwareSelfTestCodes(TestResultCode.CPU_VOLTAGE)
					.addHardwareSelfTestCodes(TestResultCode.TEMPERATURE)
					.setAlerts(Alerts.newBuilder()
							.setDishThermalThrottle(true)
							.setObstructed(true))
					.setDisablementCode(DisablementCode.CELL_IS_DISABLED)
					.setLocation(Location.newBuilder()
							.setEnabled(true)
							.setLatitude(33.413791)
							.setLongitude(-82.141163)
							.setAltitudeMeters(100)
							.setUncertaintyMetersValid(true)
							.setUncertaintyMeters(10)
							.setGpsTimeS((System.currentTimeMillis() - gpsEpochDiffMS) / 1000))
					.setAlignmentStats(AlignmentStats.newBuilder()
							.setBoresightElevationDeg(39.81f)
							.setDesiredBoresightElevationDeg(39.96f)
							.setBoresightAzimuthDeg(134.21f)
							.setDesiredBoresightAzimuthDeg(134.31f))
					.setStowed(false)
					.build();
			//WifiGetDiagnosticsResponse wifiResponse = WifiGetDiagnosticsResponse.newBuilder().setId("WifiNetwork").build();
			
			DeviceOuterClass.Response response = DeviceOuterClass.Response.newBuilder().setDishGetDiagnostics(dishResponse).build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		}
	}
}
